# Autka Backend

Cloudflare Workers backend that aggregates used-car offers server-side and serves a
clean API to the Autka Android app. Built on **Workers + D1** (SQL) **+ R2** (images),
with a cron-triggered ingestion pipeline. Aggregation, credentials, and any feed access
live here — never on the device.

## Why a backend

The app deliberately does not scrape marketplaces. This Worker is where compliant feeds
are normalized into one schema and served. Each marketplace is an `IngestSource`
(mirroring the app's old adapter pattern); the cron trigger runs every enabled source,
isolates failures, and upserts results into D1. The app then talks to one endpoint.

## API

| Method | Path            | Purpose |
|--------|-----------------|---------|
| GET    | `/health`       | Liveness check |
| GET    | `/offers`       | Search; query params mirror the app's `SearchFilter` (`query, make, model, minPrice, maxPrice, minYear, maxYear, maxMileageKm, fuelTypes, regions, sources, sort, limit, offset`) |
| GET    | `/offers/:id`   | Single offer |
| GET    | `/sources`      | Source list + enabled flags (for app UI toggles) |
| POST   | `/admin/ingest` | Manually trigger ingestion; `Authorization: Bearer <ADMIN_TOKEN>` |
| GET    | `/images/<key>` | Serve an offer image from R2 (streamed, cached, ETag/304) |

`/offers` returns `{ offers: CarOffer[], count }`. The `CarOffer` shape in
`src/lib/types.ts` mirrors the Android `com.autka.core.model.CarOffer` — keep them
in sync.

## Provisioned resources (Cloudflare account: travny)

- **D1**: `cargate-offers` — id `50fde2d6-d2f2-4607-9961-729b462b115b` (region EEUR)
- **R2**: `cargate-images` (region ENAM)

Both are already wired into `wrangler.jsonc`.

## Local development

```bash
npm install
npm run db:migrate:local      # apply migrations to the local D1
npm run dev                   # wrangler dev — http://localhost:8787
npm test                      # vitest (Workers pool) — runs the API against Miniflare
npm run typecheck             # tsc --noEmit
```

The Android debug build points at `http://10.0.2.2:8787/` (emulator loopback), so
`npm run dev` is reachable from the app with no extra config.

## Deploy

```bash
# one-time: authenticate wrangler to your Cloudflare account
npx wrangler login

# set the admin token secret (guards POST /admin/ingest)
npx wrangler secret put ADMIN_TOKEN

# apply migrations to the REMOTE (production) D1, then deploy
npm run db:migrate:remote
npm run deploy
```

After deploy, set the app's release `BACKEND_BASE_URL` (in `app/build.gradle.kts`) to the
printed `https://cargate-backend.<your-subdomain>.workers.dev/` URL.

## Ingestion sources

`src/ingest/sources/` — `mock.ts` is always-on sample data so the API has content with
zero config. `stubs.ts` holds the disabled real connectors (Otomoto, OLX, Facebook,
US auctions), each documenting the compliant data path required before enabling. The
legal acquisition route, not the code, is the hard part — see comments in `stubs.ts`.

## Images

During ingestion, each offer's images are fetched best-effort and stored in the
`cargate-images` R2 bucket (`src/ingest/images.ts`), and the offer's URLs are
rewritten to backend-served `/images/<key>` paths. This gives the app stable,
CDN-backed image URLs instead of marketplace URLs that may expire or block
hotlinking. Failures (bad URL, timeout, non-image) are swallowed and the original
URL kept, so a bad image never breaks ingestion. `GET /images/<key>` streams the
object straight from R2 (never buffered) with long cache headers and 304 support.
It is read-only — it never fetches arbitrary URLs on demand, so there is no
open-proxy / SSRF surface.

## Notes

- Cron runs ingestion every 30 min (`triggers.crons` in `wrangler.jsonc`).
- `ingest_runs` table records each run (source, timing, count, ok/error) for debugging.
- Secrets (`ADMIN_TOKEN`) are set via `wrangler secret put`, never committed.
