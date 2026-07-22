# Autka Backend

Cloudflare Workers backend that aggregates used-car offers server-side and serves a
clean API to the Autka Android app. Built on **Workers + D1** (SQL) **+ R2** (images),
with a cron-triggered ingestion pipeline. Aggregation, credentials, and feed access live
here — never on the device.

## Why a backend

The app deliberately does not scrape marketplaces. This Worker is where compliant feeds
are normalized into one schema and served. Each marketplace is an `IngestSource`; the
cron trigger runs every enabled source, isolates failures, and upserts results into D1.
The app then talks to one endpoint.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Liveness check |
| GET | `/offers` | Search and pagination; query params mirror `SearchFilter` |
| GET | `/offers/:id` | Single offer |
| GET | `/sources` | Source list and enabled flags |
| POST | `/admin/ingest` | Manually trigger ingestion; bearer-token protected |
| GET | `/images/<key>` | Stream a cached offer image from R2 |
| GET | `/import-services` | Import/sourcing companies, optionally filtered by region |

`/offers` returns `{ offers: CarOffer[], count, warnings? }`. In regular mode, `count` is
the total number of matching rows or de-duplicated groups before `limit` and `offset`.
`complete=true` instead returns the full matching set from one SQL statement, ignoring
pagination parameters, so ingestion cannot shift page boundaries between client requests.
Complete responses are capped at 5,000 rows and return HTTP 422 rather than silently
truncating a larger catalogue.

Native prices may be PLN, EUR or USD, so server-side price filters and price ordering
remain disabled until a normalized-price column lands. Android requests `complete=true`
and performs those operations after currency conversion.

The `CarOffer` shape in `src/lib/types.ts` mirrors Android
`com.autka.core.model.CarOffer`; keep them in sync.

## Provisioned resources

- **D1**: `cargate-offers`
- **R2**: `cargate-images`

Both are wired in `wrangler.jsonc`.

## Local development

```bash
npm install
npm run db:migrate:local
npm run dev
npm test
npm run typecheck
```

The Android debug build points at `http://10.0.2.2:8787/`, the emulator loopback to the
host running `wrangler dev`.

Mock ingestion is opt-in. Set `ENABLE_MOCK_SOURCE=true` for a local/demo Worker. The
production configuration keeps it false.

## Deploy

```bash
npx wrangler login
npx wrangler secret put ADMIN_TOKEN
npm run db:migrate:remote
npm run deploy
```

The migration step is required. Migrations create the offer schema, de-duplication and
coordinate columns, add per-source ingest leases, and remove mock rows left by older
production deployments.

## Ingestion semantics

`src/ingest/sources/` contains the source adapters. `mock.ts` is a local/demo source;
`stubs.ts` documents the disabled real connectors and their required compliant data paths.

Every currently enabled adapter is treated as a **complete snapshot**:

1. acquire a per-source D1 lease;
2. fetch and normalize the source snapshot;
3. cache images in R2 best-effort;
4. refresh the lease to prove the run was not superseded;
5. upsert the snapshot;
6. remove source rows not seen in that successful run;
7. release the lease.

A concurrent scheduled/manual run for the same source is skipped, while different sources
still run in parallel. A failed fetch does not delete the previous snapshot. Future delta
feeds must declare different cleanup semantics instead of using the snapshot path unchanged.

## Images

During ingestion, images are fetched best-effort and stored in R2. Offer URLs are rewritten
to backend-served `/images/<key>` paths. Failed downloads retain their original URL and do
not fail the whole ingest. The image route streams objects, supports ETag/304, and never
fetches arbitrary URLs on demand.

## Operations

- Cron runs hourly.
- `ingest_runs` retains the latest 500 diagnostic rows.
- `ingest_locks` prevents overlapping snapshots for one source and uses expiring leases so
  a crashed Worker cannot block ingestion forever.
- `ADMIN_TOKEN` is a Worker secret and is never committed.
