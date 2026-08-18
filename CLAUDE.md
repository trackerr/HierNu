# HierToen

Android-app voor ritregistratie met automatische historische straatbeelden op basis van GPS en stilstand.

## Master-document

De enige bron van waarheid voor scope, architectuur en gedrag is:
[`HierToen-technische-bouwspecificatie.pdf`](HierToen-technische-bouwspecificatie.pdf) (v1.0, 18 augustus 2026).

Bij twijfel over functionaliteit, datamodel, UX-gedrag of bouwvolgorde: dit document raadplegen, niet aannames maken. Wijzigingen op de spec horen eerst in het document te worden bijgewerkt (nieuwe versie), niet stilzwijgend in code te worden geïmproviseerd.

## Kernregel uit de spec

Rijden = rustig en niet-interactief. Stilstaan = automatisch tonen hoe de plek er vroeger uitzag. Veiligheidsregels (§12.3) hebben altijd voorrang boven gebruikersinstellingen.

## Bouwvolgorde (§16 / §17.1)

MVP zonder account, zonder eigen backend, local-first. Eerst: ritlogger + motion engine + Wikimedia Commons als enige beeldbron. Mapillary, Google Street View en archiefconnectors pas na een stabiele kern.

Zie §17.2 in het PDF voor een expliciete "niet doen"-lijst (o.a. geen jaartallen verzinnen, geen secrets hardcoden, geen foto tijdens rijden).
