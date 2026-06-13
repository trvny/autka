# Autka

An Android app that aggregates used-car offers from multiple marketplaces across
**Poland, the rest of Europe, and US import sources** into one searchable list, with
landed-cost estimation for vehicles imported from the USA.

> Formerly **CarGate**. The user-facing name and Gradle project are now **Autka**;
> the code identity is unchanged (package `com.autka`). The backend keeps the original
> resource names (`cargate-backend` Worker, `cargate-offers` D1, `cargate-images` R2).

## Repository layout

This is a monorepo:

```
/            Android app (Autka) — Kotlin, Compose, root Gradle project
/backend     Cloudflare Workers backend — TypeScript, D1, R2 (see backend/README.md)
```

The app talks to the backend through a single `BackendCarOfferSource` (replacing the
per-marketplace client adapters) that calls `GET /offers`; the backend is where compliant
data feeds are aggregated server-side. The two share a data model: backend
`src/lib/types.ts` mirrors the app's `CarOffer`. The app's backend URL is the
`BACKEND_BASE_URL` `buildConfigField` in `app/build.gradle.kts` — debug uses
`http://10.0.2.2:8787/` (emulator loopback to `wrangler dev`), release the deployed
Worker URL. Offer images are cached to R2 by the backend and rendered in the app with Coil (thumbnails on the list, an image strip on the detail screen); relative `/images/...` URLs resolve against `BACKEND_BASE_URL`.

## Status

Runnable scaffold. The app builds and runs today against a built-in **sample data
source**, so you can see the full flow (search -> filter -> list -> detail -> import
cost breakdown) immediately. Filtering by make, price, year, mileage, fuel, region,
source and sort order is wired end-to-end (live local filtering over the cache plus a
network refresh), with multi-currency conversion so PLN/EUR/USD offers compare and sort
correctly. Live marketplace data comes from the backend (compliant feeds) and from
deep-links into each site's own search; see **Data sourcing** below.

## Architecture

Single-module app organized by Google's recommended layers (UI / Data), Kotlin +
Jetpack Compose, Hilt DI, Room (offline-first), Kotlin Flow.

```
core/model        Normalized domain model (CarOffer, Money, SearchFilter, ImportCostEstimate)
data/local        Room database, DAO, entity + mappers (local cache = source of truth)
data/remote       CarOfferSource interface; BackendCarOfferSource (live) + MockCarOfferSource (demo)
data/repository   OfflineFirstCarOfferRepository: fans queries to all sources, merges, caches
feature/listings  Search + results screen (ViewModel + Compose)
feature/detail    Offer detail + US import cost breakdown
di                Hilt modules (database, repository, sources multibinding)
ui                Theme, navigation host, shared components
```

The key design point: the app consumes **one** `BackendCarOfferSource` (plus the demo
`MockCarOfferSource`), both contributed into a multibound `Set` in `di/SourcesModule.kt`;
the repository merges whatever is enabled and isolates a failing source so the others
still return. Per-marketplace aggregation happens **server-side** in the backend's
`ALL_SOURCES`, not on the device — adding a marketplace is a new ingest adapter +
`runner.ts` registration, not an app change.

## Data sourcing — read this

The app's architecture is the easy part; lawfully obtaining listing data is the real
work. **Autka does not scrape.** Listing data is handled in two layers (see
`docs/INTEGRATION.md` for the full design):

1. **Compliant feeds → backend.** The Cloudflare Worker (`/backend`) ingests whatever
   licensed/partner feeds are available, normalizes them to `CarOffer`, caches them in
   D1, and serves `GET /offers`. Each adapter lives in `backend/src/ingest/sources/`
   and is registered in `runner.ts`'s `ALL_SOURCES`, with per-source failure isolation.
   The app reads this through a single `BackendCarOfferSource` (plus a built-in
   `MockCarOfferSource` for the demo flow).
2. **No-feed sources → deep-links.** For marketplaces that prohibit ingestion or expose
   no feed (Otomoto, OLX, Facebook, Autoplac, AutoTrader.pl, AutoScout24, mobile.de,
   AutoUncle, and US auction/retail sites — Copart, IAAI, Cars.com, AutoTrader US), the
   app sends the user into that site's **own pre-filled search** via
   `feature/external/MarketplaceSearchLinks.kt`. This is client-only UX: deep-links
   never touch the backend and are never `CarOfferSource`s.

URL parameter vocabularies for the deep-link providers are verified against live URLs
where possible; unverified parameters are marked `TODO(verify)` in source and tracked
in `TODO.md`.

## US import cost

`core/model/ImportCostEstimate.kt` estimates landed cost into Poland (shipping +
EU customs duty + PL excise/akcyza + 23% VAT). The excise rate is drivetrain- and
capacity-aware (2026 akcyza table); duty, VAT and the default shipping figure are still
indicative constants — verify them before relying on the numbers. The detail screen
shows the full breakdown for any USA-region offer, with the shipping cost and engine
capacity editable inline so the landed-cost total recomputes live.

## Currency

Offers come in PLN, EUR or USD. `core/model/ExchangeRates.kt` is a pure value object
that converts between any pair (routing through a PLN base). Filtering and sorting by
price convert every offer into the user's chosen display currency first, so mixed-
currency results rank correctly; the price-range filter is interpreted in that same
currency. Cards show the original price plus an approximate converted figure, and a
currency switcher lives in the toolbar.

Rates come from `ExchangeRateRepository` (offline-first): it seeds with built-in
indicative rates (`StaticRateProvider`, flagged as stale in the UI) and refreshes on
launch from the **NBP (Narodowy Bank Polski) public API** via `NbpRateProvider` -- free,
keyless, and not part of the deferred aggregation backend. A failed fetch silently keeps
the last good rates, and the most recent successful fetch is persisted via DataStore so
a cold start shows real rates instead of the static seed.

The chosen display currency is persisted app-wide via Preferences DataStore (`SettingsRepository`), so it survives restarts and is shared across the listings and detail screens. The detail screen shows the converted price and the converted import landed-cost total alongside the originals.

## Map

Offers carry optional coordinates (`latitude`/`longitude`); a map screen (osmdroid,
OpenStreetMap tiles) plots them as markers, and tapping a marker opens the offer. Reach
it from the map icon in the listings toolbar. Real source adapters would geocode the
location string server-side — the sample data ships with coordinates. osmdroid needs no
API key or billing account, so the map works out of the box with no extra setup.

## De-duplication

The same car is often listed on several marketplaces. The backend computes a heuristic `dedup_key` per offer and `GET /offers` collapses duplicates into one result annotated with how many listings (and which sources) it represents; the app shows a "Listed on N sites" badge. `?dedup=false` returns raw rows.

## Localization

UI strings live in `res/values/strings.xml` with a full Polish translation in `res/values-pl/` (the app targets the PL market).


## Build & run

Requires Android Studio (Ladybug or newer) and JDK 17.

```bash
./gradlew assembleDebug      # build the debug APK
./gradlew installDebug       # install on a connected device/emulator
```

Or open the folder in Android Studio and hit Run. First sync downloads dependencies.

## Continuous integration

`.github/workflows/android-ci.yml` runs on every push and PR to `main`: sets up JDK 17
and Gradle (which validates the wrapper-jar checksum automatically and caches builds),
then runs `lintDebug assembleDebug testDebugUnitTest`. The debug APK and lint report are
uploaded as build artifacts. The `testDebugUnitTest` step runs the JVM unit tests under
`app/src/test` (import-cost calculator and listings filtering/sorting).

`.github/workflows/backend-ci.yml` covers the Worker (`/backend`): typecheck, `vitest`,
and a `wrangler deploy --dry-run`. `worker-configuration.d.ts` is generated in CI, not
committed. `.github/workflows/release.yml` builds a signed APK + AAB and cuts a GitHub
release on a version tag (see `docs/RELEASING.md`).

`.github/dependabot.yml` opens weekly PRs for both Gradle/Kotlin dependencies (via the
version catalog) and the workflow's GitHub Actions, grouped so related bumps arrive
together. CI builds each PR, so you review a green check rather than re-auditing
versions by hand. `dependency-submission.yml` feeds the dependency graph for alerts.

## Versions

Kotlin 2.4.0, AGP 9.2.1, KSP 2.3.9, Gradle 9.5.1, Compose BOM 2026.05.01,
Hilt 2.59.2, Room 2.8.4, compileSdk 37, minSdk 26. Bump via the version catalog at
`gradle/libs.versions.toml`; Dependabot keeps these current and CI gates each bump.

## Next steps

- Tests: repository merge/failure-isolation, mapper round-trips. (Import calculator and
  `applyFilter`/`sortComparator` unit tests have landed; exchange rates are now cached
  across restarts via DataStore.)
