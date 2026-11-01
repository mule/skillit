# Skillit – complete environmental studies source index

The canonical source is the user's **Kokomatsku ympäristöoppi.pdf**, 15 scanned spreads covering **printed textbook pages 4–33** continuously. The PDF is ordered 18–33, 14–17, then 4–13, rather than by printed page.

| Printed pages | PDF page | Chapter |
| --- | ---: | --- |
| 4–5 | 11 | Suomessa on monenlaisia elinympäristöjä |
| 6–7 | 12 | Eliökunnan luokittelu |
| 8–9 | 13 | Niityt, pihat ja pientareet ovat valoisia kasvupaikkoja |
| 10–11 | 14 | Niityn ja pientareen kasveja |
| 12–13 | 15 | Niityn ja pientareen eläimiä |
| 14–15 | 9 | Suomi on metsäinen ja soinen maa |
| 16–17 | 10 | Puut kasvavat korkeiksi |
| 18–19 | 1 | Puita |
| 20–21 | 2 | Metsissä kasvatetaan puita |
| 22–23 | 3 | Metsän nisäkkäitä |
| 24–25 | 4 | Metsän nisäkkäitä |
| 26–27 | 5 | Metsän lintuja |
| 28–29 | 6 | Metsän lintuja |
| 30–31 | 7 | Hyönteiset ja hämähäkit ovat selkärangattomia |
| 32–33 | 8 | Ravintoketju alkaa kasveista |

## Data ownership and compatibility

- Pages 4–13 remain in `NaturePack.kt` (20 existing species) and `nature-source-pages-4-13.md`.
- Pages 14–17 are summarized in `nature-readable-text-pages-14-17.md`.
- Pages 18–33 have 24 structured species records, 16 original concept prompts and reading notes.
- `nature-canonical-manifest.json` is the unified machine-readable index. It references the existing pack rather than duplicating IDs or changing saved gameplay.
- App status: all 15 chapters are active, with 44 species and 73 questions. Text is in `NaturePack.kt` and `NatureChapters.kt`, images in `drawable-nodpi`. See `nature-answer-key-review.md`. There is no runtime content loader.

## Editorial and licensing gate

The scan is not machine-readable text. App content was checked by an AI source audit (`nature-answer-key-review.md`); no human approval is claimed. Original textbook images and full scans carry unconfirmed redistribution rights: the full scans stay in ignored paths, and the rights to the committed crops must be reviewed before any publication.
