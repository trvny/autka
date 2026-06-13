# Autka — open work

Tracks the load-bearing gaps. Architecture is settled; what's left is data, verified
constants, and verified deep-link parameters. See `INTEGRATION.md` for the
data-sourcing design and the root `README.md` for the overview.

## Hard product blockers

These two gate any real (non-demo) usefulness:

1. **Compliant feed acquisition.** `backend/src/ingest/runner.ts` `ALL_SOURCES` is
   `mock`, `otomoto`, `olx`, `facebook`, `usAuction` — all but `mock` are disabled
   stubs with no licensed/partner feed behind them. Until at least one real feed lands,
   the live `GET /offers` path returns nothing and the app runs on sample data + the
   deep-link layer only. No scraping (OLX Group ToS, EU database rights, Meta ToS).

2. **Verified import-cost constants + shipping ranges.** `core/model/ImportCostEstimate.kt`
   uses indicative constants: `EU_CUSTOMS_DUTY_RATE = 0.10`, `PL_VAT_RATE = 0.23`, and a
   caller-supplied `shippingUsd`. The 2026 excise (akcyza) table is drivetrain-/capacity-
   aware and considered good; duty, VAT applicability edge cases, and realistic
   door-to-door shipping bands still need sourcing before the landed-cost total is
   trustworthy.

> **Strategic note:** partnering with a US-import broker plausibly closes both at once
> (a feed, real shipping figures, and lead-gen revenue). Brokers (usaimport.pl,
> usacars.net.pl, mattyusa.pl, autopan.pl) are brochure/service sites surfaced on the
> offer detail screen — not results-screen sources.

## Deep-link parameter verification

`feature/external/MarketplaceSearchLinks.kt` — confirmed params are marked verified;
the rest carry `TODO(verify)` (a wrong key silently lands on an unfiltered page):

- **Otomoto:** `utm_source` affiliate param unconfirmed (line ~90); real affiliate
  param TBD on joining the program.
- **AutoUncle:** remaining fuel values + `s[max_km]` / `s[min_km]` parity; plug-in
  hybrid slug.
- **AutoTrader.pl:** `diesel`/`lpg`/plug-in fuel values (petrol/electric/`hybrydowy`
  verified — note `hybrydowy`, **not** `hybryda`).
- **Autoplac:** fuel values beyond GASOLINE/DIESEL/HYBRID.
- **OLX:** free-text search key; sort keys (`mileage`/`year`).
- **US sites (IAAI, Cars.com, AutoTrader.com):** make/model path form and price-bucket
  params.

## Backend hygiene

- `worker-configuration.d.ts` stays generated in CI, never committed (it's ~541KB and
  drifts on every wrangler bump).
- Keep `CarOffer` parity between `backend/src/lib/types.ts` and
  `com.autka.core.model.CarOffer` — the #1 seam bug.

## Tests still wanted

- Repository merge / source failure-isolation.
- Mapper round-trips (entity ↔ domain).

(Import-cost calculator and `applyFilter`/`sortComparator` unit tests have landed;
exchange rates are cached across restarts via DataStore.)
