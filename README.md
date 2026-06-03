# CarGate

An Android app that aggregates used-car offers from multiple marketplaces across
**Poland, the rest of Europe, and US import sources** into one searchable list, with
landed-cost estimation for vehicles imported from the USA.

> Formerly **CarFinder**. CarFinder is retained as a secondary brand: a launchable
> Android `activity-alias` (label "CarFinder") and the internal code identity
> (package `com.carfinder`, `CarFinder*` classes). Only the user-facing name and the
> Gradle project name changed to CarGate.

## Repository layout

This is a monorepo:

```
/            Android app (CarGate) — Kotlin, Compose, root Gradle project
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
correctly. Real marketplace adapters are present as documented stubs.

## Architecture

Single-module app organized by Google's recommended layers (UI / Data), Kotlin +
Jetpack Compose, Hilt DI, Room (offline-first), Kotlin Flow.

```
core/model        Normalized domain model (CarOffer, Money, SearchFilter, ImportCostEstimate)
data/local        Room database, DAO, entity + mappers (local cache = source of truth)
data/remote       CarOfferSource adapter interface + one adapter per marketplace
data/repository   OfflineFirstCarOfferRepository: fans queries to all sources, merges, caches
feature/listings  Search + results screen (ViewModel + Compose)
feature/detail    Offer detail + US import cost breakdown
di                Hilt modules (database, repository, sources multibinding)
ui                Theme, navigation host, shared components
```

The key design point: **every marketplace is a `CarOfferSource` adapter** contributed
into a multibound `Set` in `di/SourcesModule.kt`. Adding a marketplace is one `@Binds
@IntoSet` line; the repository merges whatever is enabled. A source failing (network,
auth) is isolated so the others still return.

## Data sourcing — read this

The app's architecture is the easy part; lawfully obtaining listing data is the real
work, and it's your responsibility. Summary of each adapter's status:

| Source | Adapter | Status | Notes |
|--------|---------|--------|-------|
| Sample | `MockCarOfferSource` | Enabled | Built-in demo data, zero config |
| Otomoto | `OtomotoCarOfferSource` | Stub (disabled) | OLX Group; no open public API. Needs a partner/dealer-feed agreement or licensed data. Scraping restricted by ToS. |
| OLX | `OlxCarOfferSource` | Stub (disabled) | Partner/affiliate API under agreement only. |
| Facebook Marketplace | `FacebookMarketplaceSource` | Disabled by design | Meta ToS prohibits scraping; no listings API. Consider deep-linking the user into a pre-filled Marketplace search instead of ingesting. |
| US auctions (import) | `UsAuctionCarOfferSource` | Stub (disabled) | Copart/IAAI etc. require membership or a licensed broker API. |

The realistic production shape is a **backend** that holds the compliant feeds,
normalizes them, and serves one clean API the app consumes — not direct scraping from
the device. The adapter interface is designed so each adapter can simply call your
backend endpoint for that source.

## US import cost

`core/model/ImportCostCalculator.kt` estimates landed cost into Poland (shipping +
EU customs duty + PL excise/akcyza + 23% VAT). Rates are indicative constants —
externalize and verify them before relying on the numbers. The detail screen shows the
full breakdown for any USA-region offer, with the shipping cost and engine capacity
editable inline so the landed-cost total recomputes live.

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
the last good rates.

The chosen display currency is persisted app-wide via Preferences DataStore (`SettingsRepository`), so it survives restarts and is shared across the listings and detail screens. The detail screen shows the converted price and the converted import landed-cost total alongside the originals.

## Map

Offers carry optional coordinates (`latitude`/`longitude`); a map view (Google Maps Compose) plots them as markers, tapping a marker opens the offer. Real source adapters would geocode the location string server-side — the sample data ships with coordinates. The map needs a Google Maps API key: add `MAPS_API_KEY=...` to `local.properties` (never committed). Without a key the map screen shows a short message instead of a blank map.

## De-duplication

The same car is often listed on several marketplaces. The backend computes a heuristic `dedup_key` per offer and `GET /offers` collapses duplicates into one result annotated with how many listings (and which sources) it represents; the app shows a "Listed on N sites" badge. `?dedup=false` returns raw rows.

## Localization

UI strings live in `res/values/strings.xml` with a full Polish translation in `res/values-pl/` (the app targets the PL market).


## Map view

A map screen (Google Maps Compose) plots offers that carry coordinates; tapping a
marker's info window opens the offer. Reach it from the map icon in the listings
toolbar. Offers expose optional `latitude`/`longitude` (the backend stores them and
the mock provides coords; real adapters would geocode server-side).

The Maps SDK needs an API key. Add `MAPS_API_KEY=<your key>` to `local.properties`
(never committed) — it's wired in as a manifest placeholder. Without a key the map
screen shows a message instead of a blank map.

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
uploaded as build artifacts. (There are no unit tests yet, so the test step is a no-op
until you add them.)

`.github/dependabot.yml` opens weekly PRs for both Gradle/Kotlin dependencies (via the version catalog) and the workflow's GitHub Actions, grouped so related bumps arrive together. CI builds each PR, so you review a green check rather than re-auditing versions by hand.

## Versions

Kotlin 2.1.21, AGP 8.10.1, KSP 2.1.21-2.0.1, Gradle 8.11.1, Compose BOM 2024.12.01,
Hilt 2.56.2, Room 2.6.1, compileSdk 35, minSdk 26. These are a verified-compatible set
(KSP is pinned to its matching Kotlin+AGP build, and Hilt 2.56.2 supports Kotlin 2.1).
Bump via the version catalog at `gradle/libs.versions.toml`.

## Next steps

- Cache fetched exchange rates across restarts (currently re-fetched each launch).
- Tests: repository merge/failure-isolation, import calculator, mapper round-trips,
  `applyFilter`/`sortComparator`.
