#!/usr/bin/env python3
"""Generate human bounds-picking aids for the nature crop pages.

Outputs into tmp/verify/environment/asset-work/grid/ (private, gitignored):
  pN-grid-full.png     whole upright page at 1/4 scale, grid every 200 full-px,
                       labels in FULL-RESOLUTION upright pixel units
  pN-q1..q4.png        four half-page zooms at 1/2 scale, grid every 100 full-px
  pN-candidates.png    numbered candidate art regions (NON-SEMANTIC heuristic:
                       ink/saturation clusters - hints only, never verification)
  candidates-pN.json   the same candidate boxes in full-px upright coordinates
  pN-rotation-check.png my CW90 upright render (left) beside the parent-checked
                       preview book-N-upright.jpg (right), plus a printed diff
  INDEX.md             how to read the grid and fill crop-manifest.json

All image decoding uses JPEG draft mode at 1/2 or 1/4 scale (few MiB RAM).
Run with: .pane/prompts/image-tools/bin/python tools/nature-crops/make_grid_sheets.py
"""

import json
import os
import subprocess
from collections import deque

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
WORK = os.path.join(ROOT, 'tmp', 'verify', 'environment', 'asset-work')
PAGES = {1: 1, 4: 4, 6: 6, 14: 14, 15: 15}
UPRIGHT_W, UPRIGHT_H = 5100, 3510
PARENT_PREVIEW = os.path.join(ROOT, 'tmp', 'verify', 'environment', 'scans', 'book-{:02d}-upright.jpg')


def page_jpeg(p):
    for cand in (os.path.join(WORK, 'pages', f'p{p}-page.jpg'),
                 os.path.join(WORK, 'pages', f'p{p}--000.jpg')):
        if os.path.exists(cand):
            return cand
    raise SystemExit(f'page {p} JPEG not extracted yet; run pdfimages first')


def mem_ok():
    out = subprocess.run(['free', '-b'], capture_output=True, text=True, check=True).stdout
    avail = int([l for l in out.splitlines() if l.startswith('Mem:')][0].split()[6])
    if avail < 1024 * 1024 * 1024:
        raise SystemExit(f'ABORT: MemAvailable {avail//2**20} MiB < 1024 MiB guard')
    return avail


def font(size):
    for c in ('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
              '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
        if os.path.exists(c):
            return ImageFont.truetype(c, size)
    return ImageFont.load_default()


def load_upright(p, scale):
    """Decode page at <=1/scale via draft (cheap), rotate CW90 to upright."""
    im = Image.open(page_jpeg(p))
    im.draft('RGB', (3510 // scale, 5100 // scale))
    im = im.transpose(Image.Transpose.ROTATE_270)
    return im


def actual_scale(im):
    return im.width / UPRIGHT_W


def draw_grid(im, step_full, label_step_full, f_label):
    """Grid over an upright render; scale derived from the actual image width."""
    scale = actual_scale(im)
    d = ImageDraw.Draw(im, 'RGBA')
    w, h = im.size
    x_full = 0
    while x_full <= UPRIGHT_W:
        x = round(x_full * scale)
        major = x_full % label_step_full == 0
        d.line([(x, 0), (x, h)], fill=(255, 0, 0, 200 if major else 90), width=2 if major else 1)
        if major:
            d.text((min(x + 3, w - 60), 2), str(x_full), fill=(200, 0, 0, 255), font=f_label)
        x_full += step_full
    y_full = 0
    while y_full <= UPRIGHT_H:
        y = round(y_full * scale)
        major = y_full % label_step_full == 0
        d.line([(0, y), (w, y)], fill=(255, 0, 0, 200 if major else 90), width=2 if major else 1)
        if major:
            d.text((3, min(y + 2, h - 18)), str(y_full), fill=(200, 0, 0, 255), font=f_label)
        y_full += step_full


def candidates(p):
    """Non-semantic candidate art regions: ink/saturation clusters. Hints only."""
    im = load_upright(p, 8)                       # ~637x438
    small_w, small_h = im.size
    mask = im.convert('RGB').resize((small_w // 2, small_h // 2))
    mw, mh = mask.size
    px = mask.load()
    m = [[False] * mw for _ in range(mh)]
    for y in range(mh):
        for x in range(mw):
            r, g, b = px[x, y]
            mx, mn = max(r, g, b), min(r, g, b)
            sat = 0 if mx == 0 else (mx - mn) * 255 // mx
            m[y][x] = (sat > 40) or (mx < 150)    # colored ink or dark ink
    # dilate to merge a card's sub-elements (MaxFilter on the bitmask)
    dil = Image.frombytes('L', (mw, mh), bytes(255 if v else 0 for row in m for v in row))
    dil = dil.filter(__import__('PIL.ImageFilter', fromlist=['MaxFilter']).MaxFilter(9))
    dm = dil.load()
    seen = [[False] * mw for _ in range(mh)]
    boxes = []
    for y0 in range(mh):
        for x0 in range(mw):
            if dm[x0, y0] < 128 or seen[y0][x0]:
                continue
            q = deque([(x0, y0)])
            seen[y0][x0] = True
            minx = maxx = x0
            miny = maxy = y0
            area = 0
            while q:
                x, y = q.popleft()
                area += 1
                minx, maxx = min(minx, x), max(maxx, x)
                miny, maxy = min(miny, y), max(maxy, y)
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < mw and 0 <= ny < mh and not seen[ny][nx] and dm[nx, ny] >= 128:
                        seen[ny][nx] = True
                        q.append((nx, ny))
            boxes.append((minx, miny, maxx + 1, maxy + 1, area))
    full_area = UPRIGHT_W * UPRIGHT_H
    scale_full = (UPRIGHT_W / mw, UPRIGHT_H / mh)
    out = []
    for (bx0, by0, bx1, by1, area) in boxes:
        area_full = area * scale_full[0] * scale_full[1]
        if not (0.012 * full_area <= area_full <= 0.5 * full_area):
            continue
        fx0, fy0 = int(bx0 * scale_full[0]), int(by0 * scale_full[1])
        fx1, fy1 = int(bx1 * scale_full[0]), int(by1 * scale_full[1])
        out.append([fx0, fy0, fx1, fy1, round(area_full / full_area, 4)])
    # order: left printed page first, then row-major
    def key(b):
        half = 0 if (b[0] + b[2]) / 2 < UPRIGHT_W / 2 else 1
        return (half, b[1], b[0])
    out.sort(key=key)
    return [[b[0], b[1], b[2], b[3]] for b in out], [b[4] for b in out]


def main():
    os.makedirs(os.path.join(WORK, 'grid'), exist_ok=True)
    g = os.path.join(WORK, 'grid')
    f_small, f_big = font(15), font(13)
    for p in sorted(PAGES):
        mem_ok()
        full = load_upright(p, 4)                 # 1275x878
        print(f'p{p}: upright render {full.size}')
        gf = full.copy()
        draw_grid(gf, 200, 1000, f_small)
        gf.save(os.path.join(g, f'p{p}-grid-full.png'))

        # quadrants at 1/2 scale (2550x1755 source halves -> 1275x878 each)
        half = load_upright(p, 2)                 # 2550x1755, ~13 MiB
        hw, hh = half.size
        for qi, (x0, y0) in enumerate([(0, 0), (hw // 2, 0), (0, hh // 2), (hw // 2, hh // 2)], 1):
            q = half.crop((x0, y0, min(x0 + hw // 2 + 25, hw), min(y0 + hh // 2 + 25, hh)))
            draw_grid(q, 100, 500, f_big)
            q.save(os.path.join(g, f'p{p}-q{qi}.png'))
        del half

        boxes, areas = candidates(p)
        cs = full.copy()
        sc = actual_scale(cs)
        d = ImageDraw.Draw(cs)
        for i, b in enumerate(boxes, 1):
            r = [round(v * sc) for v in b]
            d.rectangle(r, outline=(0, 90, 255), width=3)
            d.text((r[0] + 4, max(0, r[1] - 20)), str(i), fill=(0, 60, 255), font=f_small)
        cs.save(os.path.join(g, f'p{p}-candidates.png'))
        with open(os.path.join(g, f'candidates-p{p}.json'), 'w') as f:
            json.dump({'page': p, 'note': 'NON-SEMANTIC heuristic hints; verify visually',
                       'candidates': [{'n': i + 1, 'box': b, 'areaFrac': a}
                                      for i, (b, a) in enumerate(zip(boxes, areas))]}, f, indent=1)

        # rotation cross-check against parent-verified preview
        from PIL import ImageChops, ImageStat
        parent = Image.open(PARENT_PREVIEW.format(p)).convert('RGB')
        mine_small = full.convert('RGB').resize((500, round(500 * UPRIGHT_H / UPRIGHT_W)))
        parent_small = parent.resize((500, round(500 * UPRIGHT_H / UPRIGHT_W)))
        diff = sum(ImageStat.Stat(ImageChops.difference(mine_small, parent_small)).mean) / 3
        combo = Image.new('RGB', (1010, mine_small.size[1] + 30), 'white')
        combo.paste(mine_small, (0, 0))
        combo.paste(parent_small, (510, 0))
        dc = ImageDraw.Draw(combo)
        dc.text((5, mine_small.size[1] + 6),
                f'left: my CW90 render | right: parent book preview | meanAbsDiff {diff:.1f}',
                fill='black', font=f_big)
        combo.save(os.path.join(g, f'p{p}-rotation-check.png'))
        print(f'  candidates: {len(boxes)}; rotation meanAbsDiff vs parent preview: {diff:.1f}')
        del full, cs
    print('grid aids written to', g)


if __name__ == '__main__':
    main()
