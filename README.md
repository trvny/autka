# CarGate

An Android app that aggregates used-car offers from multiple marketplaces across
**Poland, the rest of Europe, and US import sources** into one searchable list, with
landed-cost estimation for vehicles imported from the USA.

> Formerly **CarFinder**. CarFinder is retained as a secondary brand: a launchable
> Android `activity-alias` (label "CarFinder") and the internal code identity
> (package `com.carfinder`, `CarFinder*` classes). Only the user-facing name and the
> Gradle project name changed to CarGate.

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
full breakdown for any USA-region offer.

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

Kotlin 2.0.21, AGP 8.7.3, Gradle 8.11.1, Compose BOM 2024.12.01, Hilt 2.52, Room 2.6.1,
compileSdk 35, minSdk 26. Bump via the version catalog at `gradle/libs.versions.toml`.

## Next steps

- Stand up the aggregation backend and point the stub adapters at it.
- Cache fetched exchange rates across restarts (currently re-fetched each launch).
- Make US import shipping/engine-capacity inputs editable in the detail screen.
- Tests: repository merge/failure-isolation, import calculator, mapper round-trips,
  `applyFilter`/`sortComparator`.
