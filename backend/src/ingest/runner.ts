import type { IngestSource } from "./source";
import { mockSource } from "./sources/mock";
import { otomotoSource, olxSource, facebookSource, usAuctionSource } from "./sources/stubs";
import { upsertOffers } from "../db/offers";
import { cacheOfferImages } from "./images";

/** Every source the backend knows about. Add a marketplace by appending here. */
export const ALL_SOURCES: IngestSource[] = [
  mockSource,
  otomotoSource,
  olxSource,
  facebookSource,
  usAuctionSource,
];

/** How many ingest_runs rows to keep. The table is debug-only; without a cap it
 * grows unbounded toward the D1 storage limit. */
const INGEST_RUNS_KEEP = 500;

export interface IngestResult {
  sourceId: string;
  ok: boolean;
  upserted: number;
  error?: string;
}

/**
 * Run all enabled sources. Each source is isolated: one failing does not stop the
 * others. Records a row in ingest_runs per source for observability.
 */
export async function runIngestion(env: Env): Promise<IngestResult[]> {
  const enabled = ALL_SOURCES.filter((s) => s.isEnabled(env));
  const results = await Promise.all(enabled.map((s) => runOne(env, s)));
  // Keep the debug table bounded. Best-effort: a cleanup failure must never fail
  // the ingest itself.
  await pruneIngestRuns(env).catch((err) => {
    console.error(JSON.stringify({
      msg: "ingest_runs_prune_failed",
      error: err instanceof Error ? err.message : String(err),
    }));
  });
  return results;
}

async function runOne(env: Env, source: IngestSource): Promise<IngestResult> {
  const started = Date.now();
  try {
    const offers = await source.fetch(env);
    // Cache images to R2 and rewrite URLs (best-effort; never throws).
    const withImages = await Promise.all(offers.map((o) => cacheOfferImages(env, o)));
    const upserted = await upsertOffers(env.DB, withImages);
    await recordRun(env, source.sourceId, started, upserted, true, null);
    console.log(JSON.stringify({ msg: "ingest_ok", sourceId: source.sourceId, upserted }));
    return { sourceId: source.sourceId, ok: true, upserted };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    await recordRun(env, source.sourceId, started, 0, false, message);
    console.error(JSON.stringify({ msg: "ingest_failed", sourceId: source.sourceId, error: message }));
    return { sourceId: source.sourceId, ok: false, upserted: 0, error: message };
  }
}

async function recordRun(
  env: Env, sourceId: string, startedMs: number,
  upserted: number, ok: boolean, error: string | null,
): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO ingest_runs (source_id, started_at_ms, finished_at_ms, offers_upserted, ok, error)
     VALUES (?, ?, ?, ?, ?, ?)`,
  ).bind(sourceId, startedMs, Date.now(), upserted, ok ? 1 : 0, error).run();
}

/** Delete all but the most recent INGEST_RUNS_KEEP rows from ingest_runs. */
async function pruneIngestRuns(env: Env): Promise<void> {
  await env.DB.prepare(
    `DELETE FROM ingest_runs WHERE id NOT IN (
       SELECT id FROM ingest_runs ORDER BY started_at_ms DESC, id DESC LIMIT ?)`,
  ).bind(INGEST_RUNS_KEEP).run();
}
