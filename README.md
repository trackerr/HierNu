# HierToen

Android-app (Kotlin + Jetpack Compose) die een rit registreert en bij stilstand automatisch
een historisch beeld van de locatie toont. De volledige productspecificatie staat in
[`HierToen-technische-bouwspecificatie.pdf`](HierToen-technische-bouwspecificatie.pdf) — dat
document is leidend, zie [`CLAUDE.md`](CLAUDE.md).

## Status

Bouwvolgorde uit spec §17.1:

- [x] Stap 1 — Project, thema, navigatie, basis-README
- [x] Stap 2 — Room-schema en Trip/TrackPoint repositories
- [x] Stap 3 — Foreground TrackingService en actieve-rit UI
- [x] Stap 4 — MotionStateEngine met configureerbare drempels
- [x] Stap 5 — "Deze plek bewaren" en TripMoment
- [x] Stap 6 — WikimediaSource en kandidaatselectie
- [x] Stap 7 — Veilige fotoweergave
- [x] Stap 8 — Route review en exports
- [ ] Stap 9 — Settings, privacy en fouttoestanden
- [ ] Stap 10 — APK en veldtestpakket (vereist een fysiek toestel en een echte rit — buiten
      wat in deze omgeving uitgevoerd kan worden)

## Bouwen

Er is in deze omgeving geen lokale Android SDK/Gradle-installatie beschikbaar; de build en
tests worden geverifieerd via GitHub Actions (`.github/workflows/android-ci.yml`) bij elke
push. Zie de Actions-tab van de repository voor de laatste build- en teststatus.

### Lokaal openen (Android Studio)

1. Open deze map als project in Android Studio (Ladybug of nieuwer).
2. Android Studio herkent het ontbreken van `gradle-wrapper.jar` en biedt aan de wrapper te
   herstellen bij het synchroniseren — accepteer dit, of genereer 'm handmatig met een lokaal
   geïnstalleerde Gradle: `gradle wrapper`.
3. Sync het project; de Android Gradle Plugin downloadt de benodigde SDK-platformen
   automatisch (compileSdk/targetSdk 35, minSdk 28).
4. Run op een emulator of fysiek toestel (Run ▸ app). Voor ritregistratie is een fysiek
   toestel met echte GPS nodig — een emulator kan dit met een gesimuleerde route benaderen.

### Command line (met lokale Gradle- en SDK-installatie)

```bash
gradle wrapper
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Architectuur

Zie spec §9.3 voor de module-indeling. Tot nu toe geïmplementeerd:

```
app/src/main/java/nl/hiertoen/app/
├── MainActivity.kt
├── HierToenApp.kt
├── core/                # ActivityType, GeoMath (gedeeld)
├── motion/              # MotionStateEngine — statusmachine §5, puur Kotlin/testbaar
├── export/              # GpxExporter, GeoJsonExporter — §6.5
├── photos/              # WikimediaClient, kandidatenscoring §7, PhotoSearchService
├── tracking/            # TrackingService (foreground), validatie, adaptieve opslag §6
├── ui/
│   ├── theme/            # Donker, zwart met oranje accent — §1.3
│   ├── navigation/       # Start / Trips / TripDetail / ActiveTrip / Settings
│   ├── permissions/      # Runtime-toestemmingen, aangevraagd bij "Start rit" — §12.4
│   └── screens/          # Start, actieve rit (rijscherm), ritdetail, instellingen
└── data/
    ├── local/            # Room: Trip/TrackPoint/TripMoment, DAO's, database, migraties
    └── repository/       # TripRepository tussen Room en de rest van de app
```

## Testen

Room-DAO-tests en de MotionStateEngine-tests draaien als JVM-unit tests (Robolectric voor de
Room-tests, gewone JUnit voor de statusmachine en de validatie-/opslaglogica), zodat ze zonder
emulator werken — zowel lokaal (`./gradlew testDebugUnitTest`) als in CI. `TrackingService`
zelf (foreground service + Play Services locatie/activity-API's) heeft geen geautomatiseerde
test — dat vereist een emulator of fysiek toestel en hoort bij de veldtest in stap 10.

## Signing / release

Nog niet van toepassing — dit is een debug-only scaffold. Zie spec §16.2 voor het
publicatiepad (sideload → besloten testgroep → Play Internal/Closed Testing) en §18 voor het
nog te bevestigen pakketnaam-beslispunt (`nl.hiertoen.app`, voorlopig).

## Bekende beperkingen van deze scaffold

- `gradle-wrapper.jar` is niet meegecommit (binair bestand); wordt gegenereerd door Android
  Studio of door `gradle wrapper` te draaien.
- `TrackingService` herstelt zichzelf niet automatisch na een door het OS geforceerde
  processtop; een onderbroken rit wordt bij de volgende koude start herkend als RECOVERABLE
  (§6.4), maar de foreground service moet dan opnieuw gestart worden vanuit de UI.
- Ritdetail toont de route als een lichte, kaartprovider-loze Canvas-schets (eigen projectie,
  geen tegels) — §18 laat de kaartprovider zelf nog als open beslispunt staan, dus een echte
  MapLibre/Maps-SDK zou dat ongevraagd invullen. Export (GPX/GeoJSON) gaat via Storage Access
  Framework, geen WRITE_EXTERNAL_STORAGE nodig. CSV-export is bewust overgeslagen (§6.5 noemt
  het "gewenst", niet verplicht).
- Alleen Wikimedia Commons als beeldbron (§7.1 rang 2); gecureerde archieffoto's (rang 1),
  Mapillary en Google Street View zijn Fase 2 (§2.2).
- De fotoweergave op het rijscherm gaat via `TrackingSessionState.displayedPhoto`, expliciet
  gegate door de pure functie `displayedPhotoFor()` (nooit iets anders dan STILL) — die functie
  is los getest, maar de intervalwissel van de locatie-polling zelf (sneller pollen tijdens
  STILL, zodat de foto op tijd verdwijnt) is alleen op een echt toestel te verifiëren.
- Geen "toen/nu"-vergelijking, tijdlijn met alternatieve beelden of langere-stilstand-verrijking
  (§4.3 vervolggedrag) — dat is expliciet Fase 2/latere UX-verfijning, niet MVP.
