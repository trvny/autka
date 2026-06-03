-- Autka offers store. One row per normalized listing across all sources.
CREATE TABLE IF NOT EXISTS offers (
  id                TEXT PRIMARY KEY,        -- "source:nativeId"
  source_id         TEXT NOT NULL,
  title             TEXT NOT NULL,
  make              TEXT NOT NULL,
  model             TEXT NOT NULL,
  year              INTEGER,
  mileage_km        INTEGER,
  price_amount      REAL NOT NULL,
  price_currency    TEXT NOT NULL,
  fuel_type         TEXT NOT NULL DEFAULT 'UNKNOWN',
  transmission      TEXT NOT NULL DEFAULT 'UNKNOWN',
  power_hp          INTEGER,
  location          TEXT,
  region            TEXT NOT NULL,
  thumbnail_url     TEXT,
  image_urls        TEXT NOT NULL DEFAULT '',  -- ';'-joined
  listing_url       TEXT NOT NULL,
  posted_at_ms      INTEGER,
  fetched_at_ms     INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_offers_make        ON offers (make);
CREATE INDEX IF NOT EXISTS idx_offers_region      ON offers (region);
CREATE INDEX IF NOT EXISTS idx_offers_price       ON offers (price_amount);
CREATE INDEX IF NOT EXISTS idx_offers_year        ON offers (year);
CREATE INDEX IF NOT EXISTS idx_offers_posted_at   ON offers (posted_at_ms);
CREATE INDEX IF NOT EXISTS idx_offers_source      ON offers (source_id);

-- Tracks each ingestion run for observability / debugging.
CREATE TABLE IF NOT EXISTS ingest_runs (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  source_id     TEXT NOT NULL,
  started_at_ms INTEGER NOT NULL,
  finished_at_ms INTEGER,
  offers_upserted INTEGER NOT NULL DEFAULT 0,
  ok            INTEGER NOT NULL DEFAULT 0,  -- 0/1
  error         TEXT
);
