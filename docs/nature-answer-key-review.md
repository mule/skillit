# Ympäristöoppi: answer key review

Agreement between the Kotlin data and the docs does not prove a fact. This file records what was actually compared with a source. It is a check by the implementing agent, not the independent editorial review, which is still pending.

## Active: printed pages 4–13

**Source read:** photos `IMG_4801`–`IMG_4810`, the older photographed pages, on 2026-11-01. The rescanned PDF was not available.

All 27 answers and their order are pinned in `NatureChaptersTest.reviewedTripKey`.

| Result | Items |
|---|---|
| Answer matches the printed text or labels | All 27 questions |
| **Corrected** | `vuohenputki` clue said "leveämmät lehdet"; the p10 card says "ehyemmät lehdet" |
| **Corrected in `nature-source-pages-4-13.md`** | Heinäsirkka and hepokatti: the book prints "toukka", the notes said nymph. Kimalainen: the book says "naaras/naaraat", the notes said queen |
| Clue not found in legible card text | `sitruunaperhonen` "siivet muistuttavat lehtiä": p13 is under glare; the wings do look leaf-like in the picture |
| Clue taken from the picture, not card text | `hepokatti` "hyppyjalat", `kastemato` "jaokkeinen", `leppakerttu`, `amiraaliperhonen`, and "tumma" and "nisäkäs" in `kontiainen` |
| Weak wording, left as it is | `sitruunaperhonen` fact only describes the picture. Under glare, p13 appears to say that the species overwinters in Finland |

The five chapter summaries for p4–13 were written from these same photos, using only legible text.

**Crops:** spot-checked by eye: pihatatar, koiranputki, vuohenputki, heinäsirkka, hepokatti, harakankello (the easily confused ones). All six match their cards. All 20 files are distinct. The other 14 were not checked individually.

## Staged: printed pages 14–33 (not shown to the child)

**Nothing here has been compared with a source.** It comes from PR17 notes that were paraphrased from scans.

| Pages | Staged IDs | Basis |
|---|---|---|
| 14–15 | `peat`, `bog-plant` | Spread-level notes only |
| 16–17 | `tree-parts`, `pine-needles`, `tree-needs` | Spread-level notes only |
| 18–19 | species: `kuusi`, `manty`, `kataja`, `pihlaja`, `rauduskoivu`, `hieskoivu`, `haapa`, `harmaaleppa` | PR17 JSON |
| 20–21 | `forest-renewal`, `forest-thinning`, `deadwood` | PR17 JSON |
| 22–23 | `mammal`, `territory`, `hoof` | PR17 JSON |
| 24–25 | `mammal-footprints`; species: `metsamyyra`, `metsajanis`, `hirvi`, `valkohantakauris`, `mayra`, `kettu`, `ilves`, `susi` | PR17 JSON |
| 26–27 | `courtship`, `woodpecker`, `winter-flock` | PR17 JSON |
| 28–29 | species: `metso`, `teeri`, `kapytikka`, `huuhkaja`, `peippo`, `rakattirastas`, `homotiainen`, `hippiainen` | PR17 JSON |
| 30–31 | `ant-roles`, `spider-not-insect` | PR17 JSON |
| 32–33 | `food-chain-order`, `food-start`, `arrows`, `herbivore`, `decomposers` | PR17 JSON |

Species questions have the ID `species-<id>`. The wrong answer choices on concept questions are newly written and also need a read-through. The chapter summaries in `NatureChapters.kt` need the same check.

### Check these first

| Item | Claim | Why it is uncertain |
|---|---|---|
| `metsamyyra` clue | "pitkä häntä" | Ambiguous, not shown to be wrong. Sources found so far do not settle it, and it may be relative to other voles; a tail claim needs p24. The Luke page on metsämyyrä confirms bud and bark food but does not discuss the tail |
| `winter-flock` answer | Flocks spot predators more easily | Gives a reason; the reading notes only say that the birds join flocks |
| `ant-roles` answer | Queens, workers and males | Not in the reading notes for p30–31 |
| `hoof` answer | Two toes ending in large claws | Definition appears only in the JSON |
| `forest-renewal` answer | Seedlings replace felled trees | Reading notes only say that planting supports renewal |
| `courtship` answer | The male attracts a female, for example by calling | Reading notes only name the term and that metso gathers for it |
| `valkohantakauris` fact | Introduced to Finland | Single-source claim; check p24 |
| `teeri` fact | Group display in late winter | Timing needs checking on p28 |
| `metsajanis`, `metso`, `mayra`, `peippo`, `hippiainen` facts | Diet and seasonal food | Notes warn that card details need direct checking |
| JSON `sourcePage` values | Single page for each item | Not confirmed; the notes are organised by spread |

### Not in the structured data

- p14–17: no source records at all.
- p30–31: no species records, although the notes name eight animals.
- p32–33: scavengers, and the second food chain, where the plant is not named.

## To enable content

1. Compare the item with the book, or with an authoritative source. Record which of the two it was, because an outside source confirms the fact but not that it matches the textbook.
2. Correct the text in `NatureStaging` if needed.
3. Add the IDs to `approvedQuestionIds` and the chapter to `approvedChapterIds`.
4. Add the validated answers to the independent key in `NatureChaptersTest`, and update the test that pins which chapters are open.
