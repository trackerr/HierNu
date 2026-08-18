# HierToen

Android-app (Kotlin + Jetpack Compose) die een rit registreert en bij stilstand automatisch
een historisch beeld van de locatie toont. De volledige productspecificatie staat in
[`HierToen-technische-bouwspecificatie.pdf`](HierToen-technische-bouwspecificatie.pdf) — dat
document is leidend, zie [`CLAUDE.md`](CLAUDE.md).

## Status

Bouwvolgorde uit spec §17.1:

- [x] Stap 1 — Project, thema, navigatie, basis-README
- [x] Stap 2 — Room-schema en Trip/TrackPoint repositories
- [ ] Stap 3 — Foreground TrackingService en actieve-rit UI
- [ ] Stap 4 — MotionStateEngine met configureerbare drempels
- [ ] Stap 5 — "Deze plek bewaren" en TripMoment
- [ ] Stap 6 — WikimediaSource en kandidaatselectie
- [ ] Stap 7 — Veilige fotoweergave
- [ ] Stap 8 — Route review en exports
- [ ] Stap 9 — Settings, privacy en fouttoestanden
- [ ] Stap 10 — APK en veldtestpakket

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
4. Run op een emulator of fysiek toestel (Run ▸ app).

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
├── ui/
│   ├── theme/          # Donker, zwart met oranje accent — §1.3
│   ├── navigation/      # Start / Trips / Settings
│   └── screens/         # Placeholder-schermen per §4.1
└── data/
    ├── local/           # Room: Trip- en TrackPoint-entiteiten, DAO's, database — §10.1
    └── repository/      # TripRepository tussen Room en de rest van de app
```

## Testen

Room-DAO-tests draaien als JVM-unit tests via Robolectric (`app/src/test/...`), zodat ze
zonder emulator werken — zowel lokaal (`./gradlew testDebugUnitTest`) als in CI.

## Signing / release

Nog niet van toepassing — dit is een debug-only scaffold. Zie spec §16.2 voor het
publicatiepad (sideload → besloten testgroep → Play Internal/Closed Testing) en §18 voor het
nog te bevestigen pakketnaam-beslispunt (`nl.hiertoen.app`, voorlopig).

## Bekende beperkingen van deze scaffold

- `gradle-wrapper.jar` is niet meegecommit (binair bestand); wordt gegenereerd door Android
  Studio of door `gradle wrapper` te draaien.
- Geen toestemmingen (locatie, activity recognition) in het manifest — die horen bij stap 3.
- Geen echte databron; schermen tonen placeholder-teksten.
