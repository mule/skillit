# Content pack schema v1

`pack.v1.json` (JSON Schema 2020-12) describes a content pack: `pack → units[] → exercises[]`. The same JSON
is bundled in the APK (`app/src/main/assets/packs/`) in Release A and served by the backend (#22) in Release B.
Kotlin model: `engine/src/main/kotlin/fi/skillit/engine/pack/ContentPack.kt` (kotlinx.serialization, no Android).

- **Units are levels.** `ContentPack.questions(unitId)` gives questions for an `ExerciseSession`; the unit index is the level.
- **Answer types** (`answer.type`): `choice`, `numeric`, `column-multiplication`, `steps`, `fill-table`, `missing-factor`.
  Non-choice answers are checked per cell, in solving order, so the UI can highlight the first wrong cell.
- **Answers are recomputed, never trusted.** Each exercise has an `expression` or a `generator {family, params, seed}`
  (a table may instead give an `expression` per blank). `ContentPack.parse` re-derives every stored value and rejects
  the pack if one disagrees. A generator-only exercise needs a registered `AnswerRecomputer` (from `mathgen`, #21).
- **`sourceRef` holds chapter, lesson and page numbers only**: never page text, scans or photos.
- Column multiplication cell ids: `p{row}.{col}` partial digit, `c{row}.{col}` carry, `s{col}` sum carry,
  `r{col}` result digit (column 0 = ones). `AnswerSpec.ColumnMultiplication.layout(a, b)` builds the standard layout.
  Carries are optional by default: a wrong carry is highlighted but does not change XP.

`examples/math-sample.v1.json` is a small hand-written pack using every answer type; the engine tests validate it
against both the schema and the model.
