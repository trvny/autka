# Autka Backend

Cloudflare Workers backend that aggregates used-car offers server-side and serves a
clean API to the Autka Android app. Built on **Workers + D1 + R2**, with a cron-triggered
ingestion pipeline. Aggregation, credentials, and feed access live here, never on the
device.

## Why a backend

The app deliberately does not scrape marketplaces. Compliant feeds are normalized into
one schema, ingested as complete per-source snapshots, and served through one API. A
failed source is isolated; a successful snapshot also removes listings from that source
that were not seen again, so sold offers do not remain forever.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Liveness check |
| GET | `/offers` | Search and pagination |
| GET | `/offers/:id` | Single offer |
| GET | `/sources` | Source list and enabled flags |
| GET | `/import-services` | Import-company directory |
| POST | `/admin/ingest` | Manually trigger ingestion; bearer-token protected |
| GET | `/images/<key>` | Serve an offer image from R2 |

`GET /offers` returns `{ offers: CarOffer[], count }`, where `count` is the total number
of matching rows or de-duplicated groups before `limit` and `offset` are applied. Query
parameters include `query, make, model, minYear, maxYear, maxMileageKm, fuelTypes,
transmissions, regions, sources, sort, dedup, limit, offset`.

Prices are stored in their native PLN/EUR/USD currencies. Until a normalized backend
price column lands, `minPrice`, `maxPrice`, `PRICE_ASC`, and `PRICE_DESC` are deliberately
not applied server-side. The Android app requests a broad page and performs those
operations after conversion with NBP rates. The response includes a `warnings` array
when an older client asks the backend to perform a price operation.

## Provisioned resources

- **D1**: `cargate-offers`, region EEUR
- **R2**: `cargate-images`, region ENAM

Both are wired into `wrangler.jsonc`.

## Local development

```bash
npm install
npm run db:migrate:local
npm run dev
npm test
npm run typecheck
```

The Android debug build points at `http://10.0.2.2:8787/`.

Sample ingestion is opt-in. Production has `ENABLE_MOCK_SOURCE=false`; for a local demo,
override it to `true` or use the test environment. This prevents example listings from
being mixed into a real catalogue.

## Deploy

```bash
npx wrangler login
npx wrangler secret put ADMIN_TOKEN
npm run db:migrate:remote
npm run deploy
```

The release app uses `https://cargate-backend.travny.workers.dev/`.

## Ingestion sources

`src/ingest/sources/` contains one opt-in mock source and disabled placeholders for
Otomoto, OLX, Facebook and US auctions. Real connectors must use licensed, partner, or
seller-provided feeds. The legal acquisition route, not adapter boilerplate, remains the
hard part.

Each enabled adapter must return a **complete snapshot** for its source. After a
successful upsert, rows from that source not refreshed during the run are deleted. An
adapter that can only provide deltas must be extended with explicit delta semantics
before it is enabled.

## Images

During ingestion, images are cached best-effort in R2 and offer URLs are rewritten to
backend-served paths. Failures retain the original URL. The image route streams objects,
uses cache headers and ETags, and never fetches arbitrary URLs on demand.

## Operations

- Cron runs hourly.
- `ingest_runs` retains the latest 500 source-run records.
- Secrets are configured through Wrangler and never committed.
- Keep `src/lib/types.ts` in sync with Android's `CarOffer` model.
