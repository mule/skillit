# Niittyetsivä: source notes

These notes record the material used for the game from ten photos supplied by the user. Page numbers refer to the printed textbook pages, not photo order. The textbook title, author, publisher and image rights were not visible in these photos. The game questions are newly worded; the illustrations are cropped from the supplied photos.

| Printed page | Photo | Legible content used |
| --- | --- | --- |
| 4 | IMG_4801.jpeg | Finnish habitats: tunturi, metsä, suo, and lakes/rivers/sea. Tundra tops have no trees; reindeer live in fell areas. Much of Finland is forest, and moose is named as the largest forest animal. Waterways are habitats for fish, aquatic birds and other animals. |
| 5 | IMG_4802.jpeg | Fields are human-shaped habitats for food crops. Meadows are open habitats with flowering plants, butterflies and other insects. Built environments contain roads and buildings; squirrels and hares can live there. |
| 6 | IMG_4803.jpeg | Living things need food, reproduce, grow and die. The diagram names animals, plants, fungi, protists and bacteria. |
| 7 | IMG_4804.jpeg | Animal diagram separates vertebrates and invertebrates. Legible groups include fish, amphibians, reptiles, annelids, molluscs, arthropods, insects, spiders and crustaceans. |
| 8 | IMG_4805.jpeg | Meadows, yards and verges are bright growing places. The illustration labels pietaryrtti, päivänkakkara, koiranputki, kissankello, niittyleinikki and puna-apila, plus a bee. |
| 9 | IMG_4806.jpeg | Meadow plants need light. Insects use nectar and pollen. In pollination, pollen moves between flowers, carried by insects or wind. Seeds develop after pollination. Meadows have declined; mowing can help protect meadow species. |
| 10 | IMG_4807.jpeg | Plant cards 1–6: päivänkakkara, pietaryrtti, koiranputki, vuohenputki, siankärsämö, pujo. Visible identifying traits and flowering months are represented in `NaturePack.species`. |
| 11 | IMG_4808.jpeg | Plant cards 7–12: pihatatar, pihasaunio, kissankello, harakankello, niittyleinikki, maitohorsma. Visible identifying traits are represented in `NaturePack.species`. |
| 12 | IMG_4809.jpeg | Animal cards 1–4: heinäsirkka, hepokatti, kastemato, leppäkerttu. Their food and distinguishing traits are represented in `NaturePack.species`. |
| 13 | IMG_4810.jpeg | Animal cards 5–8: sitruunaperhonen, amiraaliperhonen, kimalainen, kontiainen. Visible food and distinguishing traits are represented in `NaturePack.species` where legible. |

## Species card extraction

Finnish names and measurements below follow the printed cards. A dash means the photograph does not clearly show that field. The quiz uses a smaller set of these facts and phrases them for play.

| Page | Species | Height or length | Flowering or food | Further legible detail |
| --- | --- | --- | --- | --- |
| 10 | päivänkakkara | 20–70 cm | kesä–elokuu | Yellow flower centre, white petals; Keski-Suomen maakuntakukka. |
| 10 | pietaryrtti | 30–120 cm | heinä–syyskuu | Yellow clusters of small flowers. |
| 10 | koiranputki | 50–150 cm | kesä–elokuu | Tall, easily broken stem; many small flowers in white clusters. |
| 10 | vuohenputki | 30–100 cm | kesä–elokuu | White clusters; leaves broader than koiranputki; spreads strongly. |
| 10 | siankärsämö | 20–70 cm | heinä–syyskuu | Strong stem and many white flowers; used as herb and medicinal plant. |
| 10 | pujo | 50–150 cm | heinä–lokakuu | Brown strong stem; pollen causes allergies for many people. |
| 11 | pihatatar | 5–30 cm | — | Grows near the ground; small pale pink flowers. Other text is obscured. |
| 11 | pihasaunio | 10–35 cm | heinä–syyskuu | Thin threadlike leaves, round yellow-green flower heads; used as herb. |
| 11 | kissankello | 10–60 cm | kesä–elokuu | Blue, pointed, bell-shaped flowers; Keski-Pohjanmaan maakuntakukka. |
| 11 | harakankello | 20–70 cm | kesä–heinäkuu | Violet bell-shaped flowers turn toward sun and droop in rain. |
| 11 | niittyleinikki | 10–60 cm | kesä–syyskuu | Yellow flowers; mildly poisonous plant. |
| 11 | maitohorsma | 50–150 cm | kesä–elokuu | Purple-red flowers and narrow pointed leaves; leaves contain vitamin C. |
| 12 | heinäsirkka | 2–3 cm | Plants | Egg → nymph → adult; jumps about half a metre using hind legs. |
| 12 | hepokatti | 3–4 cm | Insects | Egg → nymph → adult; larger than heinäsirkka and has long antennae. |
| 12 | kastemato | 10–30 cm | Fallen leaves and dead plants in soil | Lives mostly underground; often reaches surface in rain. |
| 12 | leppäkerttu | 5 mm | Aphids, mites, small insects | Bad taste and colour warn predators. |
| 13 | sitruunaperhonen | 5 cm wingspan | Flower nectar; rest obscured | Yellow illustration; other text partly obscured by glare. |
| 13 | amiraaliperhonen | 5 cm wingspan | Flower nectar and tree sap | Flies south for winter. |
| 13 | kimalainen | 1–2 cm | Nectar and pollen | Queen lays eggs in wax cells; only queens survive winter. |
| 13 | kontiainen | 10–15 cm | Earthworms and larvae | Shovel-like forelegs dig underground; formerly called maamyyrä. |

## Printed exercises

- Page 5 asks the reader to name familiar habitats from the illustration, choose where they feel most comfortable, match hirvi, ritariperhonen, poro and hauki to habitats, and find photos of four habitats.
- Page 9 asks where meadows occur, what pollination means and how it happens, what plants and animals live along a school route, and to collect and compare seeds.

## Reading limits

- Page 4: glare obscures parts of the bog and water descriptions. The quiz uses only the clearly visible habitat labels and forest/fell facts.
- Page 5: glare obscures part of the meadow paragraph. No uncertain wording was copied.
- Page 7: glare covers the printed labels next to the bird and fox. The game does not rely on those labels.
- Page 9: glare crosses parts of the insect-food paragraph and summary box. The pollination question uses the clear explanatory paragraph below.
- Page 11: glare affects some of the pihatatar and pihasaunio text. Their questions use the visible pictures and unambiguous identifying traits.
- Page 13: glare covers parts of the butterfly cards. The sitruunaperhonen fact in the game is limited to its visible yellow wings; no obscured wording was reconstructed.

The twenty cropped illustrations are in `app/src/main/res/drawable-nodpi/`. Their filenames match the species IDs in `NaturePack.kt`. The full photographed pages are not included in the app. The source notes and question page numbers let a reviewer compare every fact with the supplied photos.
