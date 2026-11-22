# mathgen v1

Pure Kotlin/JVM multiplication generators shared by the Android app, pack builder
and future backend. No Android, network, or model dependency.

```kotlin
val ref = GeneratorRef("column", buildJsonObject {
    put("digits", 3)
    put("carries", 2)
    put("overflow", true)
}, seed = 42)
val generated = MathGen.generate(ref)
check(MathGen.problems(generated).isEmpty())
val validated = MathGen.parsePack(packJson) // builder/import validation includes prompt numbers
```

Every family accepts a signed 64-bit seed, including negative values. The v1
algorithm uses the specified `java.util.Random` sequence and an explicit
Fisher–Yates shuffle. Same family/params/seed gives the same exercise and metadata
on Android and JVM. Algorithm changes must use a new family ID/version: old packs
must retain their recomputation semantics. Unknown keys, incorrect JSON types,
unsupported ranges and impossible profiles throw `IllegalArgumentException`.
Parameters must be JSON numbers/booleans, not strings containing them.

Family IDs use kebab-case to satisfy the pack schema's lowercase ID grammar.
Knobs are family-specific; unsupported knobs are rejected rather than ignored.

| Family (brief name) | Parameters and defaults | Answer |
| --- | --- | --- |
| `tables` | `table`: 2–10 (seeded), `count`: 2–10 (5) | Skip-count strip, multipliers within 1–10 |
| `tens-hundreds` (tensHundreds) | `zeros`: 1–2 (1), `split`: boolean (false; requires zeros=2) | Product; split puts a zero on each factor |
| `multi-factor` (multiFactor) | `digits`: 1–2 (1), `factorCount`: 3–4 (3), `tensFactors`: 0–count (0 for digits=1; 1 for digits=2) | Product plus varied `smartOrder` pairs: 2·5, 4·25, and factors of ten |
| `column` | `digits`: 2–3 (3), `carries`: 0–digits−1 (1), `overflow`: boolean (false) | Full engine column layout |
| `column-tens` (columnTens) | Same as column | Two/three-digit number × 20–90 |
| `order-of-ops` (orderOfOps) | `digits`: 1–2 (1), `factorCount`: 2–4 (2) | `Expression.steps()` in Finnish school order, including parentheses |
| `missing-factor` (missingFactor) | `digits`: 1–2 (1), `factorCount`: 2–4 (2), `tensFactors`: 0–count (0 for digits=1; 1 for digits=2) | Exactly one missing positive factor |
| `product-grid` (productGrid) | `size`: 2–5 (3) | Unique row/column headers and all product blanks |
| `multiples-of` (multiplesOf) | `maxTable`: 3–10 (10) | One 1/0 blank per table 2–maxTable |
| `word` | `digits`: 1–2 (1), `template`: 0–2 (seeded); optional `slots`, `text`, `expression` below | Computed integer answer |
| `letter-code` (letterCode) | `word`: 0–3 (seeded): KISSA, KOIRA, SAUNA, TÄHTI | Products and an unambiguous product→letter key; `rewardWord` metadata |

Multi-factor, missing-factor and order-of-ops cap every product and intermediate
at **10,000**. `digits` is a maximum, not a guarantee that every operand has that
many digits. In multi-factor/missing-factor, `tensFactors` selects exactly how many
factors are multiples of ten (10–90); positive tensFactors requires digits=2.
Other factors are 2–9, with the extra smart pair 4·25 available for digits=2.
For example, digits=2/tensFactors=1 can generate 40·☐=360, and
factorCount=3/tensFactors=2 can generate 20·4·10. Order-of-ops operands are
2–9 or 2–20, budgeted so parentheses, intermediate products and final sums stay
within the cap. All draws preserve enough budget for remaining factors.
Not every family has a carry or factor-count knob: these only apply where the
answer format uses them.

## Carry convention

`GeneratedExercise.carries` counts nonzero **written carry boxes**, matching
`DigitKind.CARRY` in the engine layout. `overflow` separately records a final
outgoing carry that becomes a leading result digit. For example, 234×5=1170 has
two carry boxes and overflow; 50×2=100 has no carry boxes and overflow. Three-digit
× one-digit exercises target carries 0–2, following the lead's clarified convention.
For column-tens the count applies to the nonzero multiplier digit; multiplying
by the tens place does not create an extra carry. `countCarries(a, b)` accepts
10–999 and 1–9; generated multipliers use 2–9. Pools are exhaustive and bounded.
The count/overflow/factors are stored on `GeneratedExercise`, not added as
undeclared properties to the v1 pack schema. They can be recovered from provenance.

## Templates and validation boundaries

Word defaults cover packages, money and weekly quantities. Default slots are
`count` and `amount`; default expression is `{count} * {amount}`. #23 can provide:

```json
{
  "slots": {"count": 4, "amount": 12},
  "text": "Ostat {count} pakkausta. Yhdessä on {amount} kynää. Montako kynää saat?",
  "expression": "{count} * {amount}"
}
```

Custom slots: 1–10 keys matching `[a-z][a-z0-9]{0,15}`, integer values 1–999.
Templates use `{slot}`; missing/malformed placeholders fail. The expression is
substituted then checked by the engine's bounded exact-integer parser. Finnish
text still needs the parent/language review: arithmetic validation cannot prove
that arbitrary prose describes the supplied expression.

`multiples-of` explicitly means multiplication-table entries with multipliers
1–10, not unrestricted divisibility. Each 1/0 cell says whether that particular
table contains the number. Grid headers and the letter-code dictionary are given,
so no blank requires guessing. Letter-code uses the existing numeric answer cells;
the UI can reveal `rewardWord` after solving the products.

`MathGen.problems(item)` checks the entire generated exercise and metadata.
For pack-backed items, use `MathGen.problems(PackExercise)` or the builder/import
entry point **`MathGen.parsePack(text)`**. The latter runs engine validation and
checks every generator-backed prompt. The sample writer uses this path before
writing output; #23 must use the same path before preview/publication.

The language pass may reword text, **never numbers**: prompt integer tokens must
remain unchanged and in the same order (including repeated values). Letter-code
`n=L` pairs must also remain unchanged. Added/replaced/deleted numbers or changed
letters reject the pack. Prompts display Finnish `·` and `:`; stored arithmetic
expressions may retain ASCII `*` and `/`. Parent review still checks prose meaning,
operators/grouping and wording: token validation cannot understand arbitrary Finnish.
The registry alone checks answers and `ContentPack.parse(..., recomputers)` alone
does not include these extra prompt checks. Hints and explanations may be reworded
after review.
No XP, retries, existing saves, or music/nature runtime behavior is modified.

## Checks and sample

`MathGen.samplePack()` produces one item per family. Regenerate the committed
sample with `flock /home/japurane/repos/skillit/.git/gradle-build.lock ./gradlew --no-daemon --max-workers=2 :mathgen:samplePack`. The file lives at
`content-schema/examples/mathgen-sample.v1.json` and requires `MathGen.recomputers`
when using the engine directly; prefer `MathGen.parsePack` for import/build paths.
`samplePack` is a test-source developer entry point, excluded from the runtime jar
and APK. Generators and recomputers perform no I/O.

Tests cover 10,000 seeds per family (110,000 total), varied supported difficulty
settings, independent arithmetic/layout/grid/code checks, strict params, full
signed seed bounds, metadata/answer tampering, JSON Schema validation and sample
round-trip parsing.
