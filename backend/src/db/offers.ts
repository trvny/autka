import type { CarOffer, SearchFilter } from "../lib/types";

interface OfferRow {
  id: string;
  source_id: string;
  title: string;
  make: string;
  model: string;
  year: number | null;
  mileage_km: number | null;
  price_amount: number;
  price_currency: string;
  fuel_type: string;
  transmission: string;
  power_hp: number | null;
  location: string | null;
  region: string;
  thumbnail_url: string | null;
  image_urls: string;
  listing_url: string;
  posted_at_ms: number | null;
  latitude: number | null;
  longitude: number | null;
}

type QueryRow = OfferRow & {
  _dup_count?: number;
  _dup_sources?: string | null;
};

function rowToOffer(r: OfferRow): CarOffer {
  return {
    id: r.id,
    sourceId: r.source_id,
    title: r.title,
    make: r.make,
    model: r.model,
    year: r.year,
    mileageKm: r.mileage_km,
    price: { amount: r.price_amount, currency: r.price_currency as CarOffer["price"]["currency"] },
    fuelType: r.fuel_type as CarOffer["fuelType"],
    transmission: r.transmission as CarOffer["transmission"],
    powerHp: r.power_hp,
    location: r.location,
    region: r.region as CarOffer["region"],
    thumbnailUrl: r.thumbnail_url,
    imageUrls: r.image_urls ? r.image_urls.split(";") : [],
    listingUrl: r.listing_url,
    postedAtEpochMs: r.posted_at_ms,
    latitude: r.latitude,
    longitude: r.longitude,
  };
}

// Every order ends with a unique key. This also keeps the legacy offset API deterministic,
// though the Android correctness path uses one complete SQL statement instead of paging.
const SORT_SQL: Record<NonNullable<SearchFilter["sort"]>, string> = {
  NEWEST: "posted_at_ms DESC, id ASC",
  PRICE_ASC: "price_amount ASC, id ASC",
  PRICE_DESC: "price_amount DESC, id ASC",
  MILEAGE_ASC: "mileage_km ASC, id ASC",
  YEAR_DESC: "year DESC, id ASC",
};

/**
 * Heuristic de-duplication fingerprint. Price is intentionally excluded because it may
 * differ between sites and currencies. Incomplete records remain unique.
 */
function dedupKey(o: CarOffer): string {
  if (o.make && o.model && o.year != null) {
    const norm = (s: string) => s.toLowerCase().trim();
    const bucket = o.mileageKm == null ? "" : Math.round(o.mileageKm / 5000) * 5000;
    return [norm(o.make), norm(o.model), o.year, o.fuelType, o.transmission, bucket].join("|");
  }
  return `unique:${o.id}`;
}

function buildWhere(f: SearchFilter): { where: string[]; binds: unknown[] } {
  const where: string[] = [];
  const binds: unknown[] = [];
  if (f.query) {
    where.push("(title LIKE ? OR make LIKE ? OR model LIKE ?)");
    const like = `%${f.query}%`;
    binds.push(like, like, like);
  }
  if (f.make) { where.push("make = ?"); binds.push(f.make); }
  if (f.model) { where.push("model = ?"); binds.push(f.model); }
  // Price filters are deliberately not applied here. price_amount contains native PLN,
  // EUR or USD values, so comparing it without a normalized price column is incorrect.
  if (f.minYear != null) { where.push("year >= ?"); binds.push(f.minYear); }
  if (f.maxYear != null) { where.push("year <= ?"); binds.push(f.maxYear); }
  if (f.maxMileageKm != null) { where.push("mileage_km <= ?"); binds.push(f.maxMileageKm); }
  if (f.fuelTypes?.length) {
    where.push(`fuel_type IN (${f.fuelTypes.map(() => "?").join(",")})`);
    binds.push(...f.fuelTypes);
  }
  if (f.transmissions?.length) {
    where.push(`transmission IN (${f.transmissions.map(() => "?").join(",")})`);
    binds.push(...f.transmissions);
  }
  if (f.regions?.length) {
    where.push(`region IN (${f.regions.map(() => "?").join(",")})`);
    binds.push(...f.regions);
  }
  if (f.sourceIds?.length) {
    where.push(`source_id IN (${f.sourceIds.map(() => "?").join(",")})`);
    binds.push(...f.sourceIds);
  }
  return { where, binds };
}

function whereSqlFor(f: SearchFilter): { sql: string; binds: unknown[] } {
  const { where, binds } = buildWhere(f);
  return { sql: where.length ? ` WHERE ${where.join(" AND ")}` : "", binds };
}

function selectSqlFor(
  f: SearchFilter,
  whereSql: string,
  sort: string,
  tail: string,
): string {
  if (f.dedup === false) {
    return `SELECT * FROM offers${whereSql} ORDER BY ${sort}${tail}`;
  }
  return (
    `WITH filtered AS (SELECT * FROM offers${whereSql}), ` +
    `ranked AS (SELECT *, ` +
    `ROW_NUMBER() OVER (PARTITION BY COALESCE(dedup_key, id) ` +
    `ORDER BY posted_at_ms DESC, id) AS _rn, ` +
    `COUNT(*) OVER (PARTITION BY COALESCE(dedup_key, id)) AS _dup_count, ` +
    `GROUP_CONCAT(source_id) OVER (PARTITION BY COALESCE(dedup_key, id)) AS _dup_sources ` +
    `FROM filtered) ` +
    `SELECT * FROM ranked WHERE _rn = 1 ORDER BY ${sort}${tail}`
  );
}

function rowsToOffers(rows: QueryRow[]): CarOffer[] {
  return rows.map((r) => {
    const offer = rowToOffer(r);
    const count = r._dup_count ?? 1;
    if (count > 1) {
      offer.listingCount = count;
      offer.otherSources = [...new Set((r._dup_sources ?? "").split(",").filter(Boolean))];
    }
    return offer;
  });
}

/** Query one legacy offset page of offers. */
export async function queryOffers(db: D1Database, f: SearchFilter): Promise<CarOffer[]> {
  const { sql: whereSql, binds } = whereSqlFor(f);
  const sort = SORT_SQL[f.sort ?? "NEWEST"] ?? SORT_SQL.NEWEST;
  const limit = Math.min(Math.max(Math.trunc(f.limit ?? 50), 1), 200);
  const offset = Math.max(Math.trunc(f.offset ?? 0), 0);
  const sql = selectSqlFor(f, whereSql, sort, ` LIMIT ${limit} OFFSET ${offset}`);
  const { results } = await db.prepare(sql).bind(...binds).all<QueryRow>();
  return rowsToOffers(results);
}

/**
 * Query one stable matching set in a single SQL statement. One extra row is requested so
 * callers can reject oversized result sets instead of silently returning a partial list.
 */
export async function queryCompleteOffers(
  db: D1Database,
  f: SearchFilter,
  maxResults: number,
): Promise<CarOffer[]> {
  const { sql: whereSql, binds } = whereSqlFor(f);
  const sort = SORT_SQL[f.sort ?? "NEWEST"] ?? SORT_SQL.NEWEST;
  const safeMax = Math.max(Math.trunc(maxResults), 1);
  const sql = selectSqlFor(f, whereSql, sort, ` LIMIT ${safeMax + 1}`);
  const { results } = await db.prepare(sql).bind(...binds).all<QueryRow>();
  return rowsToOffers(results);
}

/** Count all matching raw rows or de-duplicated groups before pagination. */
export async function countOffers(db: D1Database, f: SearchFilter): Promise<number> {
  const { sql: whereSql, binds } = whereSqlFor(f);
  const expression = f.dedup === false
    ? "COUNT(*)"
    : "COUNT(DISTINCT COALESCE(dedup_key, id))";
  const row = await db.prepare(
    `SELECT ${expression} AS count FROM offers${whereSql}`,
  ).bind(...binds).first<{ count: number }>();
  return Number(row?.count ?? 0);
}

export async function getOffer(db: D1Database, id: string): Promise<CarOffer | null> {
  const row = await db.prepare("SELECT * FROM offers WHERE id = ?").bind(id).first<OfferRow>();
  return row ? rowToOffer(row) : null;
}

/** Batch upsert. D1 supports batching prepared statements in one round trip. */
export async function upsertOffers(db: D1Database, offers: CarOffer[]): Promise<number> {
  if (offers.length === 0) return 0;
  const now = Date.now();
  const stmt = db.prepare(
    `INSERT INTO offers (
       id, source_id, title, make, model, year, mileage_km,
       price_amount, price_currency, fuel_type, transmission, power_hp,
       location, region, thumbnail_url, image_urls, listing_url,
       posted_at_ms, fetched_at_ms, dedup_key, latitude, longitude
     ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
     ON CONFLICT(id) DO UPDATE SET
       source_id=excluded.source_id,
       title=excluded.title, make=excluded.make, model=excluded.model,
       year=excluded.year, mileage_km=excluded.mileage_km,
       price_amount=excluded.price_amount, price_currency=excluded.price_currency,
       fuel_type=excluded.fuel_type, transmission=excluded.transmission,
       power_hp=excluded.power_hp, location=excluded.location, region=excluded.region,
       thumbnail_url=excluded.thumbnail_url, image_urls=excluded.image_urls,
       listing_url=excluded.listing_url, posted_at_ms=excluded.posted_at_ms,
       fetched_at_ms=excluded.fetched_at_ms, dedup_key=excluded.dedup_key,
       latitude=excluded.latitude, longitude=excluded.longitude`,
  );
  const batch = offers.map((o) =>
    stmt.bind(
      o.id, o.sourceId, o.title, o.make, o.model, o.year, o.mileageKm,
      o.price.amount, o.price.currency, o.fuelType, o.transmission, o.powerHp,
      o.location, o.region, o.thumbnailUrl, o.imageUrls.join(";"), o.listingUrl,
      o.postedAtEpochMs, now, dedupKey(o), o.latitude, o.longitude,
    ),
  );
  await db.batch(batch);
  return offers.length;
}

/**
 * A successful source fetch is treated as a complete snapshot. Remove rows from that
 * source that were not refreshed during the run, so sold/deleted listings disappear.
 */
export async function deleteOffersNotSeenSince(
  db: D1Database,
  sourceId: string,
  runStartedAtMs: number,
): Promise<void> {
  await db.prepare(
    "DELETE FROM offers WHERE source_id = ? AND fetched_at_ms < ?",
  ).bind(sourceId, runStartedAtMs).run();
}
