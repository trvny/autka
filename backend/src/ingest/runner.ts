import type { IngestSource } from "./source";
import { mockSource } from "./sources/mock";
import { otomotoSource, olxSource, facebookSource, usAuctionSource } from "./sources/stubs";
import { deleteOffersNotSeenSince, upsertOffers } from "../db/offers";
import { cacheOfferImages } from "./images";

/** Every source the backend knows about. Add a marketplace by appending here. */
export const ALL_SOURCES: IngestSource[] = [
  mockSource,
  otomotoSource,
  olxSource,
  facebookSource,
  usAuctionSource,
];

/** How many ingest_runs rows to keep. */
const INGEST_RUNS_KEEP = 500;
/** A crashed run releases itself eventually; a live run refreshes the lease before writes. */
const INGEST_LOCK_TTL_MS = 30 * 60 * 1000;

export interface IngestResult {
  sourceId: string;
  ok: boolean;
  upserted: number;
  skipped?: boolean;
  error?: string;
}

/** Run all enabled sources. A failure in one source does not stop the others. */
export async function runIngestion(
  env: Env,
  sources: readonly IngestSource[] = ALL_SOURCES,
): Promise<IngestResult[]> {
  const enabled = sources.filter((s) => s.isEnabled(env));
  const results = await Promise.all(enabled.map((s) => runOne(env, s)));
  await pruneIngestRuns(env).catch((err) => {
    console.error(JSON.stringify({
      msg: "ingest_runs_prune_failed",
      error: err instanceof Error ? err.message : String(err),
    }));
  });
  return results;
}

async function runOne(env: Env, source: IngestSource): Promise<IngestResult> {
  const lockToken = crypto.randomUUID();
  if (!await acquireSourceLock(env.DB, source.sourceId, lockToken)) {
    console.log(JSON.stringify({ msg: "ingest_skipped_overlap", sourceId: source.sourceId }));
    return { sourceId: source.sourceId, ok: true, upserted: 0, skipped: true };
  }

  const started = Date.now();
  try {
    // Source.fetch is a complete snapshot contract. Only after the fetch, image cache,
    // and upsert all succeed do we remove rows that were not seen in this run.
    const offers = await source.fetch(env);
    const withImages = await Promise.all(offers.map((o) => cacheOfferImages(env, o)));

    // A long fetch may outlive its lease and be superseded. Refreshing by token proves
    // this run still owns the source immediately before it mutates the snapshot.
    if (!await refreshSourceLock(env.DB, source.sourceId, lockToken)) {
      console.log(JSON.stringify({ msg: "ingest_discarded_superseded", sourceId: source.sourceId }));
      return { sourceId: source.sourceId, ok: true, upserted: 0, skipped: true };
    }

    const upserted = await upsertOffers(env.DB, withImages);
    await deleteOffersNotSeenSince(env.DB, source.sourceId, started);
    await recordRun(env, source.sourceId, started, upserted, true, null);
    console.log(JSON.stringify({ msg: "ingest_ok", sourceId: source.sourceId, upserted }));
    return { sourceId: source.sourceId, ok: true, upserted };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    await recordRun(env, source.sourceId, started, 0, false, message);
    console.error(JSON.stringify({ msg: "ingest_failed", sourceId: source.sourceId, error: message }));
    return { sourceId: source.sourceId, ok: false, upserted: 0, error: message };
  } finally {
    await releaseSourceLock(env.DB, source.sourceId, lockToken).catch((err) => {
      console.error(JSON.stringify({
        msg: "ingest_lock_release_failed",
        sourceId: source.sourceId,
        error: err instanceof Error ? err.message : String(err),
      }));
    });
  }
}

async function acquireSourceLock(
  db: D1Database,
  sourceId: string,
  token: string,
): Promise<boolean> {
  const now = Date.now();
  const result = await db.prepare(
    `INSERT INTO ingest_locks (source_id, token, acquired_at_ms)
     VALUES (?, ?, ?)
     ON CONFLICT(source_id) DO UPDATE SET
       token = excluded.token,
       acquired_at_ms = excluded.acquired_at_ms
     WHERE ingest_locks.acquired_at_ms < ?`,
  ).bind(sourceId, token, now, now - INGEST_LOCK_TTL_MS).run();
  return Number(result.meta.changes ?? 0) > 0;
}

async function refreshSourceLock(
  db: D1Database,
  sourceId: string,
  token: string,
): Promise<boolean> {
  const result = await db.prepare(
    "UPDATE ingest_locks SET acquired_at_ms = ? WHERE source_id = ? AND token = ?",
  ).bind(Date.now(), sourceId, token).run();
  return Number(result.meta.changes ?? 0) > 0;
}

async function releaseSourceLock(
  db: D1Database,
  sourceId: string,
  token: string,
): Promise<void> {
  await db.prepare(
    "DELETE FROM ingest_locks WHERE source_id = ? AND token = ?",
  ).bind(sourceId, token).run();
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

async function pruneIngestRuns(env: Env): Promise<void> {
  await env.DB.prepare(
    `DELETE FROM ingest_runs WHERE id NOT IN (
       SELECT id FROM ingest_runs ORDER BY started_at_ms DESC, id DESC LIMIT ?)`,
  ).bind(INGEST_RUNS_KEEP).run();
}
