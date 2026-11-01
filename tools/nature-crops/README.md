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
| `crop-manifest.json` | Source identity, coordinate space, per-species entries (`box`/`exclusions` = reviewed upright full-res bounds; `visualReview` records who reviewed what — AI reviewers with image input, no human approval claimed). |
| `apply_reviewed_bounds.py` | Applies `.pane/prompts/environment-crop-bounds-reviewed.json` to the manifest after verifying: source hash, 44 ids/pages, actual preview header sizes, and preview→full scale consistency (±3 px). |
| `build_nature_crops.py` | Extract → rotate → exclusion fill (`#dbdcdb` document-art clipping) → crop → resize → save; provenance + contacts + exclusion spot-check pairs; `--self-test` proves the crop/rotate transform; `--dry-run`; `--only id…`; memory guard (`free -b` MemAvailable ≥ 1024 MiB). |
| `make_grid_sheets.py` | Bounds-picking aids into `tmp/verify/environment/asset-work/grid/` (labeled grids, non-semantic candidate hints, rotation cross-check vs parent previews). See `INDEX.md` there. |
| `before-inventory-2aaed08.json` | Dims/bytes/SHA-256 of the 20 old crops at base commit (for the before/after diff). |

## Status (2026-11-01)

Bounds for all 44 species were proposed by the Agy (Gemini 4) visual audit and
independently reviewed by root (GPT, image input) on all 5 source grids plus a
first contact comparison; three species carry document-art exclusion rectangles
outside subject art (rauduskoivu, hirvi, peippo; paper fill `#dbdcdb`).
Season/sex annotations (kesä/talvi, ♂) are original scientific annotations and
stay. Reviewers are AI agents; no human approval is claimed. The final
original-resolution contact-44 review happens before any verified status or
commit of the generated JPEGs.

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
