# D1 Operations (travino/autka backend)

D1 is the backend's served store: the API reads it, ingestion writes it. Two tables matter most: the offers table (canonical `CarOffer` rows, upserted by `runIngestion`) and `ingest_runs` (one diagnostic row per source per run: `source_id, ok, offers_upserted, error, started_at_ms`, pruned to `INGEST_RUNS_KEEP`).

## Ground rules

- **Chat has no Cloudflare auth.** You cannot run `wrangler d1 execute` here. Write the exact command for the user to run locally, or read state through whatever CI/route exposes it. Don't imply you queried the database.
- **Read the actual schema first** (`github:get_file_contents` on the backend's schema/migration files and `wrangler.jsonc` for the DB binding/name) — don't guess table or column names beyond the two known tables above.
- **Bounded writes.** Anything that writes on the request/cron path must be capped or pruned (the `ingest_runs` prune is the model). Flag unbounded growth.

## Diagnostic queries

```bash
# Recent ingest health (the first thing to look at — see references/source-fix.md):
npx wrangler d1 execute <DB> --remote --command \
  "SELECT source_id, ok, offers_upserted, error, started_at_ms FROM ingest_runs ORDER BY started_at_ms DESC LIMIT 20"

# Offer counts per source (column names: verify against the schema first):
npx wrangler d1 execute <DB> --remote --command \
  "SELECT sourceId, COUNT(*) FROM offers GROUP BY sourceId"
```

`--remote` hits the production DB; without it you get the local dev copy under `wrangler dev`. Be explicit about which one a command targets.

## Schema changes

1. Check how the repo manages schema (a `migrations/` dir + `wrangler d1 migrations apply`, or an init SQL file). Match the existing mechanism — don't introduce a second one.
2. A column on the offers table is almost always a `CarOffer` shape change → sync `backend/src/lib/types.ts`, the upsert SQL, **and** `com.autka.core.model.CarOffer` (+ DTO/Room entity/mapper) in the same PR. See `references/add-source.md`, "If the shared shape changes."
3. Migrations must be additive/backward-compatible while old Worker code may still run (deploys aren't atomic with schema). Add nullable columns; don't rename/drop in the same step as the code change.
4. Hand the user the apply command; verify by re-querying and by the next ingest run writing cleanly.

## Cleanup / maintenance

- Stale offers: if the repo has a retention rule, follow it; if not, propose one (e.g. delete offers not seen in N runs) as a cron step with a bounded `DELETE ... LIMIT`, never an unbounded scan on the request path.
- `ingest_runs` is self-pruning via `INGEST_RUNS_KEEP` — don't add a second pruning mechanism.
