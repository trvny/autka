-- Prevent overlapping snapshots for the same source from racing each other.
CREATE TABLE IF NOT EXISTS ingest_locks (
  source_id       TEXT PRIMARY KEY,
  token           TEXT NOT NULL,
  acquired_at_ms  INTEGER NOT NULL
);

-- Production mock ingestion is disabled by default. Remove sample rows created by
-- earlier releases so they do not remain visible indefinitely after deployment.
DELETE FROM offers WHERE source_id = 'mock';
