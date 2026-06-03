-- Cross-source de-duplication: the same car is often listed on several marketplaces.
-- dedup_key is a heuristic fingerprint (make|model|year|fuel|trans|mileage-bucket)
-- computed at ingest; offers sharing a key are collapsed at query time. VIN would be
-- authoritative but no current source provides it — this is the honest best-effort.
ALTER TABLE offers ADD COLUMN dedup_key TEXT;
-- Backfill existing rows to their own id (no accidental merging until re-ingested).
UPDATE offers SET dedup_key = id WHERE dedup_key IS NULL;
CREATE INDEX IF NOT EXISTS idx_offers_dedup_key ON offers (dedup_key);
