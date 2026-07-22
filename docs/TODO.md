# Autka — open work

Tracks the load-bearing gaps. Architecture is settled; what's left is licensed data,
normalized server-side pricing, verified import inputs and a few deep-link parameters.
See `INTEGRATION.md` for the sourcing boundary and `SOURCES.md` for vetted candidates.

## Hard product blockers

1. **Compliant feed acquisition.** `backend/src/ingest/runner.ts` knows about `mock`,
   `otomoto`, `olx`, `facebook` and `usAuction`, but the real marketplace adapters remain
   disabled placeholders. Mock data is opt-in for debug/tests and disabled in release and
   production. Until at least one licensed, partner, or seller-provided feed lands, the
   production `GET /offers` catalogue is empty and the app relies on deep-links.

   Recommended order: direct importer/dealer snapshot + Auto.dev for the first USA MVP,
   mobile.de Search API for Europe, then evaluate MarketCheck or AutoUncle B2B. See
   `SOURCES.md` for access constraints and links.

2. **Normalized price + scalable pagination.** Native `price_amount` values are mixed
   PLN/EUR/USD. Android now fetches every offset page and correctly applies NBP conversion,
   price filters and price sorting over the complete matching set. That preserves correctness
   for the first live feeds, but downloading the entire result set is not a scalable catalogue
   design. Add a backend normalized price column (with rate and rate timestamp), cursor
   pagination, and tests proving that price order remains correct across page boundaries.

3. **Verified import-cost inputs + shipping ranges.** `ImportCostEstimate.kt` uses an
   indicative 10% customs rate, 23% VAT and caller-supplied shipping. The 2026 Polish
   excise table is drivetrain/capacity-aware; customs classification, origin relief,
   VAT edge cases and realistic door-to-door shipping bands still require authoritative
   inputs before the total can be presented as more than an estimate.

> **Strategic note:** a US-import broker partnership can close several gaps at once:
> unique inventory, real shipping figures, lead attribution and a revenue model.

## Deep-link parameter verification

`feature/external/MarketplaceSearchLinks.kt` marks uncertain parameters with
`TODO(verify)`. A wrong key silently opens an unfiltered page, so do not emit guesses.

- **Otomoto:** affiliate parameter pending programme access.
- **AutoUncle:** remaining fuel values, mileage parity and plug-in hybrid slug.
- **AutoTrader.pl:** diesel/LPG/plug-in values.
- **Autoplac:** fuel values beyond gasoline/diesel/hybrid.
- **OLX:** free-text and mileage/year sort keys.
- **US sites:** make/model paths and price buckets for IAAI, Cars.com and AutoTrader.com.

## Backend hygiene

- `worker-configuration.d.ts` remains generated in CI and uncommitted.
- Keep `backend/src/lib/types.ts` and Android `CarOffer` in sync.
- Every enabled ingest adapter must explicitly declare full-snapshot or delta semantics;
  current cleanup assumes a complete snapshot after each successful fetch.

## Tests still wanted

- Repository merge and source failure-isolation.
- Mapper round-trips (entity ↔ domain).
- Normalized-price pagination across currencies once that backend work lands.
