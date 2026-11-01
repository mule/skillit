#!/usr/bin/env python3
"""Build nature species JPEG crops from the canonical scanned PDF.

Reproducible pipeline (one page in RAM at a time, guarded):
  1. Verify source PDF SHA256 (see tools/nature-crops/crop-manifest.json).
  2. Extract each needed PDF page's embedded JPEG with `pdfimages -j`
     (pure stream copy, no decode).
  3. For each species with an approved box on that page: decode the page once,
     crop in ORIGINAL sideways coordinates, then rotate the crop 90deg CW to
     upright (memory-safe equivalent of rotate-then-crop; verified by --self-test),
     downscale so the long edge is <= 700 px (never upscale), save baseline JPEG
     quality 90, 4:2:2 subsampling, optimized.
  4. Write per-crop provenance (source hash, page, boxes, dims, output hash,
     replaced-file before-hash) and a labeled contact sheet for human review.

Bounds MUST be human-approved: this script performs structural checks only
(no semantic verification of what a crop depicts).

Run with the project-local Pillow interpreter:
  .pane/prompts/image-tools/bin/python tools/nature-crops/build_nature_crops.py --self-test
  .pane/prompts/image-tools/bin/python tools/nature-crops/build_nature_crops.py --dry-run
  .pane/prompts/image-tools/bin/python tools/nature-crops/build_nature_crops.py [--only id ...]
"""

import argparse
import gc
import hashlib
import json
import math
import os
import shutil
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
MANIFEST_PATH = os.path.join(ROOT, 'tools', 'nature-crops', 'crop-manifest.json')

MIN_MEM_AVAILABLE_BYTES = 1024 * 1024 * 1024  # hard stop guard from the brief
MIN_BOX_PX = 150          # full-res px; smaller is almost certainly a mistake
MIN_OUT_LONG_EDGE = 300   # output px; smaller needs --allow-small
MAX_OUT_LONG_EDGE = 700
JPEG_QUALITY = 90
SUBSAMPLING = 1           # 4:2:2


def mem_available():
    """MemAvailable via `free -b` (the brief forbids reading kernel paths directly)."""
    out = subprocess.run(['free', '-b'], capture_output=True, text=True, check=True).stdout
    for line in out.splitlines():
        if line.startswith('Mem:'):
            return int(line.split()[6])
    raise RuntimeError('could not parse `free -b` output')


def require_memory(context):
    avail = mem_available()
    if avail < MIN_MEM_AVAILABLE_BYTES:
        sys.exit(f'ABORT ({context}): MemAvailable {avail//2**20} MiB < '
                 f'{MIN_MEM_AVAILABLE_BYTES//2**20} MiB stop guard. Try again when the host frees RAM.')
    return avail


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def load_manifest():
    with open(MANIFEST_PATH, encoding='utf-8') as f:
        return json.load(f)


def upright_to_sideways_box(box, upright_w, upright_h, orig_w, orig_h):
    """Map a box in upright (CW90-rotated) space to the original scan space.

    Upright = orig.transpose(ROTATE_270) (90 deg clockwise). For orig W x H
    (here 3510x5100 -> upright 5100x3510), upright pixel (x', y') comes from
    orig pixel (x, y) with x' = H-1-y, y' = x. Hence an upright box
    [x0, y0, x1, y1] maps to orig crop box (left, upper, right, lower) =
    (y0, H - x1, y1, H - x0).
    """
    assert (upright_w, upright_h) == (orig_h, orig_w), 'page size mismatch'
    x0, y0, x1, y1 = box
    return (y0, orig_h - x1, y1, orig_h - x0)


def self_test():
    """Prove crop-then-rotate == rotate-then-crop for the box transform."""
    import random
    random.seed(20261101)
    W, H = 320, 240  # pretend "orig" is landscape-sideways like the scan
    orig = Image.new('RGB', (W, H))
    px = orig.load()
    for y in range(H):
        for x in range(W):
            px[x, y] = (x % 256, y % 256, (x + y) % 256)
    upright = orig.transpose(Image.Transpose.ROTATE_270)
    uw, uh = upright.size
    assert (uw, uh) == (H, W)
    for _ in range(200):
        x0 = random.randrange(0, uw - 10); x1 = random.randrange(x0 + 10, uw)
        y0 = random.randrange(0, uh - 10); y1 = random.randrange(y0 + 10, uh)
        box = (x0, y0, x1, y1)
        want = upright.crop(box)
        l, u, r, b = upright_to_sideways_box(box, uw, uh, W, H)
        got = orig.crop((l, u, r, b)).transpose(Image.Transpose.ROTATE_270)
        assert got.size == want.size, (box, got.size, want.size)
        assert got.tobytes() == want.tobytes(), box
    print('self-test OK: crop-then-rotate is pixel-identical to rotate-then-crop (200 random boxes)')
    print('orientation cross-check against parent previews is done by make_grid_sheets.py')


def extract_page_jpegs(pdf_path, pages, workdir, expect_sha):
    got = sha256_file(pdf_path)
    if got != expect_sha:
        sys.exit(f'source PDF SHA256 mismatch: {got} != {expect_sha}; refusing')
    pagedir = os.path.join(workdir, 'pages')
    os.makedirs(pagedir, exist_ok=True)
    out = {}
    for p in pages:
        dst = os.path.join(pagedir, f'p{p}-page.jpg')
        if not os.path.exists(dst):
            tmpdir = os.path.join(workdir, 'extract-tmp')
            os.makedirs(tmpdir, exist_ok=True)
            subprocess.run(['pdfimages', '-f', str(p), '-l', str(p), '-j', pdf_path,
                            os.path.join(tmpdir, f'p{p}-')], check=True)
            produced = [f for f in os.listdir(tmpdir) if f.startswith(f'p{p}-') and f.endswith('.jpg')]
            if len(produced) != 1:
                sys.exit(f'page {p}: expected exactly 1 embedded JPEG, got {produced}')
            shutil.move(os.path.join(tmpdir, produced[0]), dst)
            for f in os.listdir(tmpdir):
                os.remove(os.path.join(tmpdir, f))
        out[p] = dst
    return out


def validate_box(box, upright_w, upright_h, page_species):
    x0, y0, x1, y1 = box
    warns = []
    if not (0 <= x0 < x1 <= upright_w and 0 <= y0 < y1 <= upright_h):
        return [f'box {box} outside upright page {upright_w}x{upright_h}'], None
    w, h = x1 - x0, y1 - y0
    if w < MIN_BOX_PX or h < MIN_BOX_PX:
        return [f'box {box} too small (<{MIN_BOX_PX}px full-res): {w}x{h}'], None
    if x0 <= 2 or y0 <= 2 or x1 >= upright_w - 2 or y1 >= upright_h - 2:
        warns.append('box touches page edge; check for clipped anatomy/page furniture')
    for other in page_species:
        ob = other.get('box')
        if not ob:
            continue
        ox0, oy0, ox1, oy1 = ob
        iw = min(x1, ox1) - max(x0, ox0)
        ih = min(y1, oy1) - max(y0, oy0)
        if iw > 0 and ih > 0:
            frac = (iw * ih) / min((x1 - x0) * (y1 - y0), (ox1 - ox0) * (oy1 - oy0))
            if frac > 0.3:
                warns.append(f'box overlaps {other["id"]} by {frac:.0%} (>30%)')
    return warns, (w, h)


def render_output(crop_upright, species_id):
    w, h = crop_upright.size
    long_edge = max(w, h)
    if long_edge <= MAX_OUT_LONG_EDGE:
        out = crop_upright  # never upscale
        scaled = False
    else:
        s = MAX_OUT_LONG_EDGE / long_edge
        out = crop_upright.resize((max(1, round(w * s)), max(1, round(h * s))),
                                  Image.Resampling.LANCZOS)
        scaled = True
    if max(out.size) < MIN_OUT_LONG_EDGE:
        raise ValueError(f'{species_id}: output long edge {max(out.size)}px < {MIN_OUT_LONG_EDGE}px '
                         f'(source region probably too small; use --allow-small only if intentional)')
    return out, scaled


def load_font(size):
    for cand in ('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
                 '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'):
        if os.path.exists(cand):
            return ImageFont.truetype(cand, size)
    return ImageFont.load_default()


def contact_sheet(records, out_path):
    cols = 6
    cell = 220
    rows = math.ceil(len(records) / cols)
    sheet = Image.new('RGB', (cols * cell, rows * (cell + 26)), 'white')
    draw = ImageDraw.Draw(sheet)
    font = load_font(13)
    for i, rec in enumerate(records):
        cx, cy = (i % cols) * cell, (i // cols) * (cell + 26)
        im = Image.open(rec['path'])
        im.thumbnail((cell - 8, cell - 8))
        sheet.paste(im, (cx + (cell - im.width) // 2, cy + (cell - im.height) // 2))
        draw.text((cx + 4, cy + cell + 2),
                  f"{rec['id']} {im.width}x{im.height}", fill='black', font=font)
    sheet.save(out_path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--pdf', default=None, help='source PDF (default: manifest path, repo-relative)')
    ap.add_argument('--manifest', default=MANIFEST_PATH)
    ap.add_argument('--out', default=os.path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi'))
    ap.add_argument('--workdir', default=os.path.join(ROOT, 'tmp', 'verify', 'environment', 'asset-work'))
    ap.add_argument('--only', nargs='*', default=None, help='restrict to species ids')
    ap.add_argument('--dry-run', action='store_true', help='validate boxes and report, write nothing')
    ap.add_argument('--allow-small', action='store_true')
    ap.add_argument('--self-test', action='store_true')
    args = ap.parse_args()

    if args.self_test:
        self_test()
        return

    with open(args.manifest, encoding='utf-8') as f:
        manifest = json.load(f)
    src = manifest['sourcePdf']
    pdf_path = args.pdf or os.path.join(ROOT, src['path'])
    orig_w, orig_h = src['pageImageSize']
    upright_w, upright_h = src['uprightPageSize']
    assert (upright_w, upright_h) == (orig_h, orig_w)

    todo = [s for s in manifest['species'] if not args.only or s['id'] in args.only]
    pending = [s['id'] for s in todo if not s.get('box')]
    with_box = [s for s in todo if s.get('box')]
    print(f'species selected: {len(todo)}; with approved boxes: {len(with_box)}; pending bounds: {len(pending)}')
    if pending:
        print('pending (skipped):', ', '.join(pending))
    if not with_box:
        print('nothing to build; fill "box" in the manifest first (human-approved bounds only)')
        return

    pages = sorted({s['pdfPage'] for s in with_box})
    print('MemAvailable:', require_memory('start'), 'bytes')

    page_jpegs = extract_page_jpegs(pdf_path, pages, args.workdir, src['sha256'])

    os.makedirs(os.path.join(args.workdir, 'provenance'), exist_ok=True)
    prov_path = os.path.join(args.workdir, 'provenance', 'crops-provenance.json')
    provenance = {'run': {}, 'crops': []}
    if os.path.exists(prov_path):
        with open(prov_path, encoding='utf-8') as f:
            provenance = json.load(f)

    records = []
    for p in pages:
        species_on_page = [s for s in with_box if s['pdfPage'] == p]
        print(f'-- page {p}: {len(species_on_page)} crops')
        print('MemAvailable:', require_memory(f'page {p} decode'), 'bytes')
        page_img = Image.open(page_jpegs[p])          # full-res decode, ~54 MiB
        assert page_img.size == (orig_w, orig_h), (page_img.size, (orig_w, orig_h))
        page_sha = sha256_file(page_jpegs[p])
        for s in species_on_page:
            box = tuple(s['box'])
            warns, dims = validate_box(box, upright_w, upright_h, species_on_page)
            if dims is None:
                print(f'  {s["id"]}: REJECTED ->', '; '.join(warns))
                continue
            for w_ in warns:
                print(f'  {s["id"]}: WARN: {w_}')
            if args.dry_run:
                print(f'  {s["id"]}: box ok {box} -> {dims[0]}x{dims[1]} px upright (dry run)')
                continue
            side = upright_to_sideways_box(box, upright_w, upright_h, orig_w, orig_h)
            crop = page_img.crop(side)                # small copy
            up = crop.transpose(Image.Transpose.ROTATE_270)  # upright, small
            del crop
            try:
                out, scaled = render_output(up, s['id'])
            except ValueError as e:
                if args.allow_small and 'allow-small' in str(e):
                    out, scaled = up, False
                else:
                    print(f'  {s["id"]}: REJECTED -> {e}')
                    del up
                    continue
            dst = os.path.join(args.out, s['resource'])
            before = None
            if os.path.exists(dst):
                before = {'sha256': sha256_file(dst), 'bytes': os.path.getsize(dst)}
            out.save(dst, 'JPEG', quality=JPEG_QUALITY, subsampling=SUBSAMPLING, optimize=True)
            out_sha = sha256_file(dst)
            prov = {
                'id': s['id'], 'kind': s['kind'], 'resource': s['resource'],
                'sourcePdfSha256': src['sha256'], 'pdfPage': p,
                'pageImageSha256': page_sha, 'rotationToUpright': 'CW90',
                'boxUpright': list(box), 'boxSidewaysOriginal': list(side),
                'cropSizeUprightPx': list(up.size),
                'outputSizePx': list(out.size), 'downscaled': scaled,
                'outputBytes': os.path.getsize(dst), 'outputSha256': out_sha,
                'replacedFile': before,
                'verifiedByHuman': s.get('verifiedByHuman', False),
            }
            provenance['crops'] = [c for c in provenance['crops'] if c['id'] != s['id']] + [prov]
            records.append({'id': s['id'], 'path': dst})
            print(f'  {s["id"]}: {box} -> {out.size[0]}x{out.size[1]} '
                  f'{os.path.getsize(dst)//1024} KiB {"(downscaled)" if scaled else "(native)"}')
            del up, out
            gc.collect()
        del page_img
        gc.collect()

    if args.dry_run:
        print('dry run complete; nothing written')
        return

    provenance['run'] = {
        'manifest': os.path.relpath(args.manifest, ROOT),
        'speciesBuilt': len(records),
        'sourcePdfSha256': src['sha256'],
        'jpegSettings': f'quality {JPEG_QUALITY}, subsampling 4:2:2, baseline, optimized',
        'maxLongEdge': MAX_OUT_LONG_EDGE,
    }
    with open(prov_path, 'w', encoding='utf-8') as f:
        json.dump(provenance, f, indent=1, sort_keys=True)
    cs = os.path.join(args.workdir, 'contacts', f'contact-{len(records)}.png')
    os.makedirs(os.path.dirname(cs), exist_ok=True)
    contact_sheet(records, cs)
    print(f'provenance: {prov_path}')
    print(f'contact sheet (human review): {cs}')


if __name__ == '__main__':
    main()
