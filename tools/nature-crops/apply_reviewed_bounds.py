#!/usr/bin/env python3
"""Apply root/Agy-reviewed bounds to tools/nature-crops/crop-manifest.json.

Verification performed before writing (all must pass):
  - reviewed file sourceSha256 matches the manifest's pinned source PDF hash;
  - exactly 44 rows matching the manifest's species ids (and pdfPage);
  - each row's previewSize matches the ACTUAL parent preview image header
    (tmp/verify/environment/scans/book-NN-upright.jpg, read via PIL - not the
    guessed 1376/1377);
  - boxFull/exclusionsFull equal boxPreview/exclusionsPreview scaled by
    (5100/preview_w, 3510/preview_h) within 3 px.

boxFull/exclusionsFull are authoritative (per handoff); the scale check only
guards against transcription/scale mistakes. Reviewers are AI agents with
image input (Agy Gemini4 audit; root GPT grid/crop comparison) - recorded as
visualReview, never as human approval.

Run: .pane/prompts/image-tools/bin/python tools/nature-crops/apply_reviewed_bounds.py
"""

import json
import os
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
MANIFEST = os.path.join(ROOT, 'tools', 'nature-crops', 'crop-manifest.json')
REVIEWED = os.path.join(ROOT, '.pane', 'prompts', 'environment-crop-bounds-reviewed.json')
PREVIEW = os.path.join(ROOT, 'tmp', 'verify', 'environment', 'scans', 'book-{:02d}-upright.jpg')

UPRIGHT_W, UPRIGHT_H = 5100, 3510
TOL_PX = 3


def scaled_ok(prev_box, full_box, sx, sy):
    return all(abs(f - p * s) <= TOL_PX for p, f, s in
               zip(prev_box, full_box, (sx, sy, sx, sy)))


def main():
    with open(MANIFEST, encoding='utf-8') as f:
        manifest = json.load(f)
    with open(REVIEWED, encoding='utf-8') as f:
        reviewed = json.load(f)

    if reviewed['sourceSha256'] != manifest['sourcePdf']['sha256']:
        sys.exit('source SHA-256 mismatch between reviewed file and manifest')
    rows = {r['id']: r for r in reviewed['rows']}
    species = {s['id']: s for s in manifest['species']}
    if len(rows) != 44 or set(rows) != set(species):
        sys.exit(f'id mismatch: reviewed {len(rows)} vs manifest {len(species)}')

    problems = []
    for sid, r in sorted(rows.items()):
        s = species[sid]
        actual_w, actual_h = Image.open(PREVIEW.format(r['pdfPage'])).size
        if list(r.get('previewSize', [])) != [actual_w, actual_h]:
            problems.append(f'{sid}: previewSize {r.get("previewSize")} != actual {(actual_w, actual_h)}')
            continue
        sx, sy = UPRIGHT_W / actual_w, UPRIGHT_H / actual_h
        if not scaled_ok(r['boxPreview'], r['boxFull'], sx, sy):
            problems.append(f'{sid}: boxFull != boxPreview x ({sx:.4f},{sy:.4f}) within {TOL_PX}px')
        for i, (pe, fe) in enumerate(zip(r.get('exclusionPreview', []), r.get('exclusionsFull', []))):
            if not scaled_ok(pe, fe, sx, sy):
                problems.append(f'{sid}: exclusion {i} full != preview scaled within {TOL_PX}px')
        if len(r.get('exclusionPreview', [])) != len(r.get('exclusionsFull', [])):
            problems.append(f'{sid}: exclusionPreview/exclusionsFull count mismatch')
        if r['pdfPage'] != s['pdfPage']:
            problems.append(f'{sid}: pdfPage {r["pdfPage"]} != manifest {s["pdfPage"]}')
        x0, y0, x1, y1 = r['boxFull']
        if not (0 <= x0 < x1 <= UPRIGHT_W and 0 <= y0 < y1 <= UPRIGHT_H and
                (x1 - x0) >= 150 and (y1 - y0) >= 150):
            problems.append(f'{sid}: boxFull {r["boxFull"]} fails page/size validation')
        for i, e in enumerate(r.get('exclusionsFull', [])):
            if not (x0 <= e[0] < e[2] <= x1 and y0 <= e[1] < e[3] <= y1):
                problems.append(f'{sid}: exclusion {i} {e} outside box {r["boxFull"]}')

    if problems:
        print('REFUSING TO APPLY:')
        for p in problems:
            print(' -', p)
        sys.exit(1)

    for sid, r in rows.items():
        s = species[sid]
        s['box'] = r['boxFull']
        s['exclusions'] = r.get('exclusionsFull', [])
        s['status'] = 'bounds-reviewed-output-pending'
        s['boundsReview'] = {
            'previewSize': r['previewSize'],
            'boxPreview': r['boxPreview'],
            'exclusionsPreview': r.get('exclusionPreview', []),
            'note': r['notes'],
            'source': '.pane/prompts/environment-crop-bounds-reviewed.json',
        }
        s['visualReview'] = {
            'status': 'bounds-reviewed; final original-resolution output review PENDING',
            'proposedBy': ['Agy Gemini4 source/page audit'],
            'reviewedBy': ['root GPT with image input: all 5 source grids + crop comparison'],
            'isHuman': False,
            'evidence': ['.pane/prompts/environment-crop-bounds-reviewed.json',
                         'tmp/verify/environment/asset-work/grid/',
                         'tmp/verify/environment/asset-work/contacts/'],
        }

    manifest['verification'] = {
        'status': 'bounds-reviewed-output-pending',
        'boundsProvenance': ('Proposed by Agy (Gemini 4, image input) against the actual scans; '
                             'independently reviewed by root (GPT, image input) on all 5 source '
                             'grids and a first-contact comparison. No human has approved these '
                             'crops; final original-resolution contact-44 review is pending.'),
        'exclusionPolicy': ('Document-art clipping rectangles (paper fill #dbdcdb) only, outside '
                            'subject art: rauduskoivu (text above leaf inset; left edge refined to '
                            'x=1260 preview by root after the first mask clipped canopy), hirvi '
                            '(card number/title strip left of antlers), peippo (three text strips). '
                            'Season/sex annotations (kesa/talvi, male symbols) stay: original '
                            'scientific annotations, not species answers.'),
        'protocol': ('1) This applier verified preview scale from actual preview headers. '
                     '2) build_nature_crops.py --self-test/--dry-run, then guarded serial build. '
                     '3) root/Agy inspect contact-44 at native resolution plus the exclusion '
                     'spot-check pairs (unmasked/masked) before any verified status or commit.'),
    }
    with open(MANIFEST, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write('\n')
    print(f'applied reviewed bounds to 44 species; {sum(1 for r in rows.values() if r.get("exclusionsFull"))} with exclusions')


if __name__ == '__main__':
    main()
