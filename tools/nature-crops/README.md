# Nature species crops — reproducible asset pipeline

Produces the 44 species illustration JPEGs for
`app/src/main/res/drawable-nodpi/` from the user's private scanned textbook PDF.

- 20 **replacements** (same resource names, better pixels): printed pages 10–13
  (PDF pages 14–15), the original 20-crop pack.
- 24 **new** species: trees printed 18–19 (PDF 1), mammals printed 24–25 (PDF 4),
  birds printed 28–29 (PDF 6), IDs matching `docs/nature-content-pages-18-33.json`.

Art is never generated or redrawn: each output is a lossless extraction of the
original embedded page JPEG (`pdfimages -j`), rotated 90° clockwise to upright,
cropped to the human-approved box, and downscaled (never upscaled) to a
≤700 px long edge, JPEG quality 90, 4:2:2.

## Files

| File | Purpose |
| --- | --- |
| `crop-manifest.json` | Source identity, coordinate space, per-species entries (`box` = human-approved upright full-res bounds), crop rules. |
| `build_nature_crops.py` | Extract → rotate → crop → resize → save; provenance + contact sheet; `--self-test` proves the crop/rotate transform; `--dry-run`; `--only id…`; memory guard (`free -b` MemAvailable ≥ 1024 MiB). |
| `make_grid_sheets.py` | Human bounds-picking aids into `tmp/verify/environment/asset-work/grid/` (labeled grids, non-semantic candidate hints, rotation cross-check vs parent previews). See `INDEX.md` there. |
| `before-inventory-2aaed08.json` | Dims/bytes/SHA-256 of the 20 old crops at base commit (for the before/after diff). |

## Status (2026-11-01)

All 44 manifest entries are `pending-bounds`: the OpenCode agent's model cannot
view images, so bounds and semantic verification are deliberately left to
root/Agy via the grid sheets. Nothing in `drawable-nodpi` has been modified yet.

## Run

```sh
# from repo root
.pane/prompts/image-tools/bin/python tools/nature-crops/build_nature_crops.py --self-test
.pane/prompts/image-tools/bin/python tools/nature-crops/build_nature_crops.py --dry-run
.pane/prompts/image-tools/bin/python tools/nature-crops/build_nature_crops.py
```

Peak working memory ≈ 90 MiB (one page decode at a time, crop-then-rotate).
The original PDF and all page renders stay in gitignored paths (`.pane/`, `tmp/`)
and must never be committed; see `docs/nature-image-sources.md` for rights notes.
