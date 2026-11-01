# Ympäristöoppi: answer key review

**Who reviewed this:** AI agents, not a person. **No human approval is claimed.**

| Item | Value |
|---|---|
| Source | `Kokomatsku ympäristöoppi.pdf` (private; not committed), SHA-256 `b464cf60de0edb2bac6d24148d6adaf868c66c7dbd34dd0a30dd9c7e82fa01e4`, 15 spreads, printed pages 4–33 |
| Full content audit | Agy, visual read of all 15 spreads, all 44 species and 73 questions, at `2aaed08`. Approved on condition that its corrections are applied |
| Targeted recheck | Claude read the scans of pp. 14–17 and 20–21 for the corrections below. Other spreads rely on the Agy audit |
| Crops | 44 crops, AI visual review recorded in `nature-image-sources.md` and `tools/nature-crops/crop-manifest.json` |

The independent key is pinned as literals in `NatureChaptersTest` (`reviewedTripKey`, `reviewedPages14To33Key`).

## Corrections applied

| Item | Was | Now | Page |
|---|---|---|---|
| `tree-parts` | "juuret, runko ja latvus" | "juuret, varsi ja lehdet" | 17 |
| `puun-rakenne` summary and terms | runko, latvus | juuret, varsi, lehdet, ravinteet, runko, tyvi, latva, kuori, neulaset, kanto, vuosirenkaat | 16–17 |
| `suot` summary and terms | Bog types and "suomarjat", which the book does not contain | Metsä, rahkasammal, peat, pond overgrowing | 15 |
| `haapa` clue and fact | Leaves trembling in the wind | Wavy-edged leaf; soft wood with woodpecker nest holes | 19 |
| `metsamyyra` clue | "pitkä häntä" (the card only gives the length) | Finland's most common mammal | 24 |
| `metsanhoito` terms | lahopuu | puuntaimi, harvennus, kelo, sukupuutto, uhanalainen | 20–21 |
| `sitruunaperhonen` clue and fact | Picture description, from glare in the old photos | Nectar; overwinters in Finland | 13 |
| `vuohenputki` clue | "leveämmät lehdet" | "ehyemmät lehdet" | 10 |
| Page citations | `peat`, `bog-plant` 14; `tree-needs` 16; `forest-renewal` 20 | 15; 17; 21 | |

Summary wording was lightly paraphrased from the audit's suggestions, because the app calls its summaries own-words. Answers in the key were not changed.

## Known limits

- `pine-needles` sits in the p16–17 chapter and cites p16, which only pictures the needles. "Pareittain" is printed on the p18 card.
- `peat` answers "kuolleista kasvien osista"; the book describes dead rahkasammal specifically.
- The wrong answer choices are newly written; the audit reviewed them, but they do not come from the book.
- Redistribution rights for the crops are unconfirmed.
