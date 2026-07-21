# Autka — external marketplace deep-links: integration

How the deep-link feature fits the monorepo (`trvny/autka`: Android app + Cloudflare
Workers backend), and what's left to do.

## The decision, in one paragraph

Autka does **not** scrape. Listing data is obtained one of two ways: (1) **compliant
feeds** normalized server-side by the Cloudflare backend and served to the app as
`CarOffer`s; (2) for sources with **no feed** (Otomoto, OLX, Facebook, AutoScout24,
US auctions, AutoUncle), the app **deep-links the user into that site's own pre-filled
search** — we send the user, we ingest nothing. Deep-links are a client-only UX
affordance; they never touch the backend and are never `CarOfferSource`s.

## Two layers, one boundary

```
┌── Android app ────────────────────────────────────────────────┐
│ feature/external/   ← NEW. Deep-links. Returns URLs, not data. │
│   MarketplaceSearchLinks.kt   SearchFilter -> marketplace URLs │
│   MarketplaceLinksRow.kt      Compose chips, fire ACTION_VIEW  │
│                                                                │
│ data/remote/BackendCarOfferSource  ← the ONLY real data path   │
│        │  GET /offers                                          │
└────────┼───────────────────────────────────────────────────────┘
         ▼
┌── Cloudflare Worker (backend/) ───────────────────────────────┐
│ ingest/sources/  compliant feeds only (mock on; stubs off)    │
│ D1 offers + R2 images, cron upsert, GET /offers               │
└───────────────────────────────────────────────────────────────┘
```

The line that matters: **anything that produces a `CarOffer` goes through the backend.
Anything that produces a link stays in `feature/external` on the device.** Don't bind a
deep-link provider into `di/SourcesModule` and don't add a deep-link "source" to the
Worker — that would blur the one boundary that keeps the data path compliant.

## App-side changes

New package `com.autka.feature.external` (2 files, provided):

- **MarketplaceSearchLinks.kt** — pure `SearchFilter -> List<MarketplaceLink>`,
  region-aware. Providers: AutoUncle, AutoScout24 (PL+EU), Otomoto, OLX, Facebook (PL),
  mobile.de (EU), Copart, IAAI, Cars.com, AutoTrader (USA). `all(filter)` returns only
  providers whose regions intersect `filter.regions`.
- **MarketplaceLinksRow.kt** — `LazyRow` of `AssistChip`s; each fires
  `Intent.ACTION_VIEW` (marketplace app if installed, else browser),
  `ActivityNotFoundException` caught.

Wiring (see `HOOKUP.txt` for exact diffs):

1. `ListingsScreen.kt`: import `MarketplaceLinksRow`; show it inside the empty-results
   `Column` and as a footer `item {}` under the results `LazyColumn`. Uses the existing
   `uiState.filter` — no ViewModel change.
2. `MarketplaceLinksRow.kt`: add the two imports left out earlier
   (`androidx.compose.runtime.remember`, `androidx.compose.foundation.layout.size`).
3. `res/values/strings.xml` + `res/values-pl/strings.xml`: add `continue_search_on`
   and `no_browser`.

Affiliate IDs are plumbed (`MarketplaceLinksRow(filter, affiliateId = "…")`) but unused
until programs are joined; the Otomoto builder appends it as a TODO-marked param.

## Backend-side changes

**None required.** The backend stays exactly as designed: compliant feeds in, one clean
`/offers` API out. The deep-link layer deliberately lives only on the client, so the
Worker, D1, R2, cron and `stubs.ts` are untouched.

One optional consistency note: `backend/src/ingest/sources/stubs.ts` documents the same
sources as disabled-until-a-feed connectors. That comment block and the app's deep-link
providers describe the same reality from two sides (no feed yet → backend returns
nothing, app offers a deep-link instead). Keep the two in sync when a source flips from
"deep-link only" to "has a real feed": at that point you enable its `IngestSource` in the
backend, and you may drop or keep its deep-link chip.

## TODO(verify) before relying on filtering

Path shapes are confirmed; many query keys are best-guess and marked `TODO(verify)` in
code. A wrong key lands the user on an **unfiltered** page — worse than no chip — so
confirm each against a live filtered URL (open the site, apply filters, read the address
bar).

| Provider     | Confidence              | Verify |
|--------------|-------------------------|--------|
| AutoScout24  | path + params confirmed | fuel codes, sort tokens, free-text key |
| AutoUncle    | base path confirmed     | all query keys (brands/models/price/year/odometer/q) |
| Otomoto      | path confirmed          | all `search[...]` keys, fuel slugs, order tokens, affiliate param |
| OLX          | path confirmed          | `search[...]` keys (note: it spells it `milage`) |
| Facebook     | by design: query+price  | `minPrice`/`maxPrice` keys |
| mobile.de    | host confirmed          | every key; make needs a numeric id, so free-text only |
| Copart/IAAI  | host confirmed          | keyword param names |
| Cars.com     | host confirmed          | makes/models/price/year keys |
| AutoTrader   | host confirmed          | keyword/price/year keys |

## Strategic note (the part a chip doesn't fix)

**AutoUncle already is what Autka aspires to be** for the local market: ~11M EU listings
from ~1,900 sites with price ratings, live in Poland. Competing on "aggregate more PL
listings" is a losing race.

The gap AutoUncle leaves: it's **EU-listings only — no US auctions, no import-to-PL
landed cost.** Autka already has that (`core/model/ImportCostCalculator.kt`: shipping +
EU duty + PL akcyza + 23% VAT, editable in the detail screen). That is Autka's real
differentiator, not the aggregation.

Implied product direction: make the **USA → PL** axis primary. Keep AutoUncle/Otomoto/OLX
as deep-links for the local market (the user goes there anyway); have Autka do the thing
no one on the PL market does well — tell the buyer whether a given Copart/IAAI car lands
cheaper in Poland than a comparable Otomoto listing.

Worth a separate investigation: AutoUncle runs a B2B product (AutoUncle for Dealers).
Ask whether they offer a partner/affiliate or data program — one licensed feed from an
aggregator could replace N per-marketplace deals (the "Ceneo feed", already built).
This is a question for their BD, not an assumption.

## File manifest (this drop)

- `MarketplaceSearchLinks.kt`  → `app/src/main/java/com/autka/feature/external/`
- `MarketplaceLinksRow.kt`     → `app/src/main/java/com/autka/feature/external/`
- `HOOKUP.txt`                 → apply the diffs, then delete
- `INTEGRATION.md`             → keep in repo (e.g. `docs/`) or fold into README
