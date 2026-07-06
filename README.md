<p align="center">
  <img src="https://raw.githubusercontent.com/trvny/autka/main/fastlane/metadata/android/en-US/images/icon.png" width="96" alt="Autka icon">
</p>

<h1 align="center">Autka</h1>

<p align="center">
  Used-car aggregator for Poland, the EU, and US imports — with landed-cost estimation built in.
</p>

<p align="center">
  <a href="https://github.com/trvny/autka/actions/workflows/android-ci.yml"><img src="https://github.com/trvny/autka/actions/workflows/android-ci.yml/badge.svg" alt="Android CI"></a>
  <a href="https://github.com/trvny/autka/actions/workflows/backend-ci.yml"><img src="https://github.com/trvny/autka/actions/workflows/backend-ci.yml/badge.svg" alt="Backend CI"></a>
  <img src="https://img.shields.io/badge/kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.4.0">
  <img src="https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white" alt="minSdk 26">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT License"></a>
</p>

An Android app that aggregates used-car offers from Polish, EU, and US-import
marketplaces into one searchable list, with landed-cost estimation for cars imported
from the USA.

> Formerly **CarGate**. The user-facing name and Gradle project are now **Autka**; code
> identity is unchanged (package `com.autka`). Live Cloudflare resources keep the
> original `cargate-` prefix.

## Status

Runnable scaffold. Search → filter → list → detail → import-cost breakdown works
end-to-end today against a built-in sample data source. Live marketplace data comes
from the backend's compliant feeds plus deep-links into each site's own search — see
[Data sourcing](docs/INTEGRATION.md).

## Repository layout

This is a monorepo:

```
/            Android app (Autka) — Kotlin, Compose, root Gradle project
/backend     Cloudflare Workers backend — TypeScript, D1, R2 (see backend/README.md)
/docs        Architecture, data sourcing, releasing, open items
```

## Build & run

Requires Android Studio (Ladybug or newer) and JDK 17.

```bash
./gradlew assembleDebug      # build the debug APK
./gradlew installDebug       # install on a connected device/emulator
```

Or open the folder in Android Studio and hit Run. First sync downloads dependencies.

## Docs

| | |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | App layers, currency, import cost, map, de-dup, localization, versions, CI/CD |
| [`docs/INTEGRATION.md`](docs/INTEGRATION.md) | Data sourcing: compliant feeds vs. deep-links, and why Autka doesn't scrape |
| [`docs/RELEASING.md`](docs/RELEASING.md) | Cutting a release — signing, Google Play, F-Droid |
| [`docs/TODO.md`](docs/TODO.md) | Open `TODO(verify)` items and blockers |
| [`backend/README.md`](backend/README.md) | Backend service details |

## License

MIT — see [LICENSE](LICENSE).
