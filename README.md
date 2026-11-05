# Moodified

**A quiet companion for your emotional patterns.**

Two-tap mood logging, on-device behavioral context, and adaptive care suggestions — without accounts, servers, or friction.

![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)
![targetSdk](https://img.shields.io/badge/targetSdk-36-3DDC84?logo=android&logoColor=white)
![Build](https://img.shields.io/badge/build-Gradle%209.3.1-02303A?logo=gradle&logoColor=white)
![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-lightgrey)

---

## What it is

Moodified is a native Android app that helps you notice how you feel — in seconds, not sessions. A modal sheet captures your mood in two taps. In the background, with your permission, the app correlates each entry with quiet on-device signals (activity, sleep, screen use) so the picture over a week means something. When the pattern warrants it, an adaptive care engine surfaces a small, matched suggestion.

Everything runs on-device. No accounts, no servers, no telemetry. Nothing leaves your phone unless you export it.

---

## Screens

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/01-check-in.png" width="220" alt="Check-in home"/><br/><sub><b>Check-in home</b></sub></td>
    <td align="center"><img src="docs/screenshots/02-quick-log.png" width="220" alt="Quick log sheet with a note and a time picker"/><br/><sub><b>Quick log, note and time</b></sub></td>
    <td align="center"><img src="docs/screenshots/03-insight-overview.png" width="220" alt="Insight overview with an inferred mood card and its driver chips"/><br/><sub><b>Insight overview and drivers</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/04-insight-screen.png" width="220" alt="Sleep tab showing seven nights, one of them logged by hand"/><br/><sub><b>Sleep, with a logged night</b></sub></td>
    <td align="center"><img src="docs/screenshots/05-care.png" width="220" alt="Care screen"/><br/><sub><b>Adaptive care</b></sub></td>
    <td align="center"><img src="docs/screenshots/06-profile.png" width="220" alt="Profile"/><br/><sub><b>Profile</b></sub></td>
  </tr>
</table>

<sub>Captured on a Pixel-class device. Frame list and capture notes live in <a href="docs/screenshots/README.md"><code>docs/screenshots/README.md</code></a>.</sub>

---

## Features

- **Two-tap mood logging** — valence, then arousal, then optional note. Entries stay editable afterwards, and the sheet is deep-linkable via `moodified://quicklog`.
- **Passive behavioral context** — steps, activity intensity, inferred sleep windows, screen interactions, all recorded on-device alongside your entries.
- **Weekly insights** — mood stability, activity trends, sleep duration and bedtime, screen-time flags including late-night usage. Each chart carries a plain-language card that says what the week showed and how many days it rests on.
- **Sleep you can correct** — tap any night in the sleep chart to log or fix it. A session that overlaps the estimated night replaces it, a nap adds on top of it, and the chart reads "Last 7 nights" with every bar named for the night it began.
- **Inference that learns your baseline** — the engine takes your sleep goal and usual bedtime from your own history, scales its thresholds to them, and adjusts its weights when a mood you log disagrees with the one it inferred. Driver chips on Today's Mood name the signals behind the estimate.
- **Adaptive care engine** — a transparent rule-based system that reads today's behavioral snapshot and picks a matching intervention (micro-confirmation, guided routine, trend alert, or motivational nudge).
- **Micro-prompt notifications** — occasional gentle check-ins that deep-link straight into the quick-log sheet.
- **On-device by design** — no accounts, no network, no analytics. Data export is JSON to your storage; deletion is one tap.

---

## Tech stack

| Layer | What |
| --- | --- |
| **Language / UI** | Kotlin 2.1, Jetpack Compose (BOM 2024.12), Material 3 |
| **DI** | Dagger Hilt |
| **Persistence** | Room (SQLite), DataStore for preferences |
| **Async** | Kotlin Coroutines + Flow |
| **Background** | WorkManager (nightly rollups, retention purges), Foreground Service (tracking) |
| **Motion** | Lottie |
| **Build** | Gradle 9.3.1, AGP 8.7.3 with build-type variants (`debug` / `release`), R8, AAB splits |
| **Quality** | ktlint, detekt, JUnit4, MockK |

---

## Install (pre-built APK)

Prefer to just try the app? Grab the latest APK from the [Releases page](https://github.com/mkepg/moodified/releases/latest).

1. Download `app-release.apk` from the release assets.
2. On your Android device, allow *Install unknown apps* for your browser or file manager.
3. Open the APK and tap **Install**.
4. Grant Activity Recognition and Notifications permissions when prompted for passive sensing to work.

Current builds are **debug-signed** and marked *pre-release* — Android will show "unknown signer" during install. That's expected for pre-1.0 test builds; a properly-signed release is planned before wider distribution.

---

## Quick start (from source)

```bash
git clone https://github.com/mkepg/Moodified.git
cd Moodified
./gradlew installDebug   # installs on a connected device/emulator
```

Debug builds install as `com.moodified.app.debug` — no keystore needed, mock data enabled, side-by-side with any release install. Requires JDK 17, Android Studio (recent stable), and SDK Platform 36.

Full walkthrough — including release builds, common tasks, and troubleshooting — is in [`docs/clone-and-run.md`](docs/clone-and-run.md).

---

## Tests

```bash
./gradlew test              # JVM unit tests
./gradlew connectedCheck    # instrumented tests, needs a device or emulator
```

Unit tests cover the mood-inference engine and its feedback calibration, the care-evaluation
engine and base guidance, activity intensity, sleep merging (estimated nights against logged
sessions and naps), night labelling, weekly sleep trends, the insight narrator, and the quick-log
view model. Instrumented tests verify Room schema migrations.

---

## Architecture at a glance

The codebase follows a clean-architecture-lite layering:

```
app/src/main/java/com/moodified/app/
├── core/           # theme, coordination, dev tools, navigation, permissions, background service + workers
├── data/           # Room entities/DAOs, DataStore, exporter, receivers, repository implementations
├── di/             # Hilt modules
├── domain/         # repository interfaces, use cases, model classes
└── presentation/   # Compose screens & viewmodels by feature (check-in, care, insight, calendar, onboarding, …)
```

Debug-only surfaces (mock data seeders, dev navigation) live in `app/src/debug/` and are physically stripped from release builds. See [`docs/application-overview.md`](docs/application-overview.md) for the full architectural walkthrough.

---

## Privacy posture

Moodified is on-device by design. Concretely:

- No accounts, no user identifiers. The app never asks who you are.
- No network calls, no analytics SDKs, no crash reporters. There is no server side.
- Cloud backups and device-transfer are explicitly disabled in the manifest.
- Any data leaves the phone only if you export it from the Privacy screen.
- One-tap "delete all my data" wipes every mood entry, tracking record, and preference.

---

## Documentation

- **[`docs/application-overview.md`](docs/application-overview.md)** — full application overview and technical reference (~550 lines): features, architecture, data layer, inference engines, permissions, build/release, glossary.
- **[`docs/clone-and-run.md`](docs/clone-and-run.md)** — local development setup, build variants, troubleshooting.
- **[`CONTRIBUTING.md`](CONTRIBUTING.md)** — ground rules, style, PR shape.

---

## Status

Moodified is a single-maintainer portfolio project. It's functional end-to-end on Android 8.0+. The current build is [**v0.9.0-alpha**](https://github.com/mkepg/moodified/releases/tag/v0.9.0-alpha), a pre-release APK on the [Releases page](https://github.com/mkepg/moodified/releases). Not distributed on the Play Store today. Issues and small PRs are welcome; see `CONTRIBUTING.md` before proposing a larger change.

---

## License

Copyright © 2026 Mikhael Edman Gomez. All rights reserved. See [`LICENSE`](LICENSE). The source is public for reference; no permission is granted to redistribute or build derivative products.
