# Content pack schema v1

`pack.v1.json` (JSON Schema 2020-12) describes a content pack: `pack → units[] → exercises[]`. The same JSON
is bundled in the APK (`app/src/main/assets/packs/`) in Release A and served by the backend (#22) in Release B.
Kotlin model: `engine/src/main/kotlin/fi/skillit/engine/pack/ContentPack.kt` (kotlinx.serialization, no Android).

- **Units are levels.** `ContentPack.questions(unitId)` gives questions for an `ExerciseSession`; the unit index is the level.
- **Answer types** (`answer.type`): `choice`, `numeric`, `column-multiplication`, `steps`, `fill-table`, `missing-factor`.
  Non-choice answers are checked per cell, in solving order, so the UI can highlight the first wrong cell.
- **Answers are recomputed, never trusted.** An answer counts as checked only when
  - the exercise `expression` recomputes it (for `choice`, every choice carries a `value` and exactly one equals the
    result, which must be `correctId`; `steps` must equal the evaluation order), or
  - each `fill-table` blank has its own `expression` (a table may not use an exercise-level expression), or
  - a registered `AnswerRecomputer` (from `mathgen`, #21) ran and rebuilt exactly the stored answer.
  An unknown generator family always rejects the pack. `ContentPack.parse` rejects the pack on any mismatch.
- **Expressions** use only ASCII digits, `+ - * / ( )`, `×`, `·`, `:` and ASCII spaces (at most 200 characters, 16 nested parentheses).
- **Size limits** (`PackLimits`, mirrored in the schema): titles 200, prompt 500, hint and explanation 1000 characters;
  50 units, 100 exercises per unit, 10 choices, 20×20 tables, 200 digit cells, 20 generator params (1000 chars as JSON, model only).
- **`sourceRef` holds chapter, lesson and page numbers only**: never page text, scans or photos.
- Column multiplication cell ids: `p{row}.{col}` partial digit, `c{row}.{col}` carry, `s{col}` sum carry,
  `r{col}` result digit (column 0 = ones). `AnswerSpec.ColumnMultiplication.layout(a, b)` builds the standard layout.
  Carries are optional by default: a wrong carry is highlighted but does not change XP.

`examples/math-sample.v1.json` is a small hand-written pack using every answer type; the engine tests validate it
against both the schema and the model.
