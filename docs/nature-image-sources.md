# Nature illustration sources and crop provenance

Records where each species JPEG in `app/src/main/res/drawable-nodpi/` comes from
and how it is produced. The scanned textbook is user-provided and private: the
PDF, full-page renders, and OCR text stay in gitignored paths (`.pane/`,
`tmp/`) and are never committed or published. Cropped illustrations are committed for the
family's own device builds. **The GitHub repository `mule/skillit` is public**, so
pushing these commits would publish the crops. Redistribution rights are unconfirmed;
review this before any push or release (see `docs/nature-canonical-manifest.json`).

## Canonical source

| Field | Value |
| --- | --- |
| File (private) | `.pane/prompts/environment-source.pdf` |
| SHA-256 | `b464cf60de0edb2bac6d24148d6adaf868c66c7dbd34dd0a30dd9c7e82fa01e4` |
| Size / pages | 69,911,526 bytes / 15 spreads |
| Page image | one embedded JPEG per page, 3510×5100, rotation 0 (scanned sideways) |
| Upright orientation | rotate 90° clockwise (parent-verified previews; agent cross-checked, meanAbsDiff ≈ 3/255) |
| Printed pages covered | 4–33 (non-chronological PDF order; map in `docs/nature-canonical-manifest.json`) |

## Crop pipeline (reproducible)

`tools/nature-crops/build_nature_crops.py` (see `tools/nature-crops/README.md`):

1. verify source PDF SHA-256;
2. `pdfimages -j` lossless extraction of the page's original embedded JPEG;
3. crop the human-approved upright box (crop-then-rotate transform, proven
   pixel-identical by `--self-test`);
4. downscale to ≤700 px long edge (LANCZOS, never upscale);
5. baseline JPEG quality 90, 4:2:2, optimized;
6. provenance JSON (source page hash, boxes, before/after hashes, sizes) into
   `tmp/verify/environment/asset-work/provenance/`, plus a labeled contact sheet.

Boxes live in `tools/nature-crops/crop-manifest.json`, applied from
`.pane/prompts/environment-crop-bounds-reviewed.json` (private) after programmatic
scale verification against the actual preview headers. Bounds were proposed by
the Agy (Gemini 4) visual audit and independently reviewed by root (GPT, image
input) on the source grids; the generated OpenCode agent itself cannot view
images and only ran the verified pipeline. Reviewers are AI agents — no human
approval is claimed anywhere. Three species carry document-art exclusion
rectangles outside subject art (rauduskoivu, hirvi, peippo; paper fill
`#dbdcdb`, per-audit tone), recorded per-crop in the provenance JSON with
unmasked/masked spot-check pairs. Season/sex annotations (kesä/talvi, ♂) are
original scientific annotations, kept as-is. The final original-resolution
contact-44 review by root/Agy gates any verified status and the commit.

## Species → source page

20 replacements (printed 10–13 = PDF 14–15; original pack, same filenames;
old crops were 129–417 px long edge, 318 KiB total — see
`tools/nature-crops/before-inventory-2aaed08.json`):

| Printed page | Species (ids) |
| --- | --- |
| 10 | paivankakkara, pietaryrtti, koiranputki, vuohenputki, siankarsamo, pujo |
| 11 | pihatatar, pihasaunio, kissankello, harakankello, niittyleinikki, maitohorsma |
| 12 | heinasirkka, hepokatti, kastemato, leppakerttu |
| 13 | sitruunaperhonen, amiraaliperhonen, kimalainen, kontiainen |

24 additions (printed 18–19 = PDF 1, 24–25 = PDF 4, 28–29 = PDF 6; IDs match
`docs/nature-content-pages-18-33.json`):

| Printed page | Species (ids) |
| --- | --- |
| 18 | kuusi, manty, kataja, pihlaja |
| 19 | rauduskoivu, hieskoivu, haapa, harmaaleppa |
| 24 | metsamyyra, metsajanis, hirvi, valkohantakauris |
| 25 | mayra, kettu, ilves, susi |
| 28 | metso, teeri, kapytikka, huuhkaja |
| 29 | peippo, rakattirastas, homotiainen, hippiainen |

Crop rules: only the species' own art — no names, captions, card numbers, page
furniture, or neighboring art (answer leakage); complete anatomy (antennae,
tails, leaves); trees may include their leaf/cone detail art when it belongs to
the same illustration region. New resources are named `nature_<id>.jpg` with the
ASCII ids above.

## Verification status and limits

- Agent-side (done): source hash verified; page/size checks; rotation
  cross-checked against parent-verified previews; transform self-test; grid aids
  and non-semantic candidate hints generated for the review pass; reviewed
  bounds applied after preview-scale verification (actual 2000×1377 headers).
- Reviewer-side: bounds proposed by Agy (Gemini 4, image input) and reviewed by
  root (GPT, image input) on all 5 source grids + first contact comparison;
  final original-resolution contact-44 and exclusion spot-check review PENDING
  before verified status. Automated shape checks are not semantic verification,
  and no human approval is claimed.
