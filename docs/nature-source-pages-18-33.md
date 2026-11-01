# Ympäristöoppi – source material, printed pages 18–33

Continuation of [pages 4–13](nature-source-pages-4-13.md) and the existing **Niittyetsivä** module. The original partial scan covered **eight two-page spreads**; the canonical 15-spread PDF now covers printed pages 4–33. Pages 14–17 are now included in the canonical complete PDF and summarized in [the bridge notes](nature-readable-text-pages-14-17.md).

## Page index

| Printed pages | Topic | Extracted material |
| --- | --- | --- |
| 18–19 | Puita | Eight tree cards: kuusi, mänty, kataja, pihlaja, rauduskoivu, hieskoivu, haapa, harmaaleppä. Key traits include needles, leaf shape, growing places and uses. |
| 20–21 | Metsissä kasvatetaan puita | Managed forest at 0/40/80 years; planting, thinning, wood products, deadwood, threatened forest species and exercises. |
| 22–23 | Metsän nisäkkäitä | Mammals nurse their young; wolf territories; moose movement and hooves; voles and their food-web importance. |
| 24–25 | Metsän nisäkkäitä – lajikortit | Eight mammal cards with diet, size, reproduction and track images: metsämyyrä, metsäjänis, hirvi, valkohäntäkauris, mäyrä, kettu, ilves, susi. |
| 26–27 | Metsän lintuja | Metso, käpytikka, hömötiainen, huuhkaja; nesting, seasonal feeding, mating displays and winter flocks. |
| 28–29 | Metsän lintuja – lajikortit | Eight bird cards: metso, teeri, käpytikka, huuhkaja, peippo, räkättirastas, hömötiainen, hippiäinen. |
| 30–31 | Hyönteiset ja hämähäkit ovat selkärangattomia | Invertebrates, forest hiding places, ant colonies and worker roles, spiders and insect comparison. |
| 32–33 | Ravintoketju alkaa kasveista | Producers, herbivores, predators, scavengers, decomposers; food chain example apila → metsäjänis → ilves. |

## Structured content

- `nature-content-pages-18-33.json`: 24 species records (8 trees, 8 mammals, 8 birds), 16 paraphrased concept prompts and a food-chain example, with source-page references.
- Every species record names an optional crop `crops/<id>.jpg`, but **the images are not in this GitHub change**. A separate local export archive contains 26 image crops and unverified Finnish OCR text.
- `sourcePage` always means the **printed book page**, not the PDF image number.
- The 24 crop files are review assets, **not clean background-removed game illustrations**. Two further crops show the food-chain diagrams.

## Subject continuity and lesson design

The previous material introduces Finnish habitats, plant/animal classification, meadows, pollination, and meadow species (pages 4–13). These new pages continue with forest trees, forest management, mammals, birds, invertebrates and food chains.

Suggested future Niittyetsivä sections:

1. **Puutunnistin** – identify eight tree species by needles, leaves, berries and bark.
2. **Metsänhoitaja** – put planting, growing, thinning and harvesting in order.
3. **Jälkietsivä** – match mammal silhouettes and foot/hoof print shapes.
4. **Lintubongari** – recognize eight forest birds and learn their behavior.
5. **Kuusijalan jäljillä** – compare insect and spider body parts, plus the roles in an ant colony.
6. **Ravintoketjun rakentaja** – build apila → metsäjänis → ilves, then discuss decomposers.

These are **content suggestions**, not implemented Android game screens or final questions.

## Accuracy and licensing

This is a **source-derived, paraphrased learning index** rather than a fully verified word-for-word transcription. OCR on the scans was noisy, especially around columns and illustrations; the companion archive's OCR files are explicitly unverified and should not be used to automatically populate child-facing text. Data on body measurements, breeding and food may need direct visual checking before expanding the pack.

**Licensing warning:** The original textbook text and artwork may be protected by copyright. The public repository should not include the full page scans or copied illustration crops unless redistribution rights have been confirmed. Keep the local crop archive as reference material and replace crops with appropriately licensed/original game assets before publishing.

## Reference export

A companion ZIP was generated from the uploaded PDF with `crops/` (26 JPGs), `transcription/` (8 raw OCR files) and `image-manifest.json`. The ZIP is delivered in the ChatGPT conversation and **has not been pushed to GitHub**. An importing agent can match `crops/<id>.jpg` to the JSON species records by ID.
