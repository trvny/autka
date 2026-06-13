import type { CarOffer, SearchFilter } from "../lib/types";

// Row shape as stored in D1 (snake_case columns).
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

const SORT_SQL: Record<NonNullable<SearchFilter["sort"]>, string> = {
  NEWEST: "posted_at_ms DESC",
  PRICE_ASC: "price_amount ASC",
  PRICE_DESC: "price_amount DESC",
  MILEAGE_ASC: "mileage_km ASC",
  YEAR_DESC: "year DESC",
};

/**
 * Heuristic de-duplication fingerprint. The same car listed on multiple marketplaces
 * should collapse into one. Uses normalized make/model/year/fuel/transmission and a
 * coarse mileage bucket. Price is intentionally excluded (it legitimately differs
 * across sellers/sites, and currencies differ with no rates here). When core fields
 * are missing we fall back to the offer's own id so incomplete rows never merge.
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
  if (f.minPrice != null) { where.push("price_amount >= ?"); binds.push(f.minPrice); }
  if (f.maxPrice != null) { where.push("price_amount <= ?"); binds.push(f.maxPrice); }
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

/**
 * Query offers with a parameterized filter. All inputs are bound, never interpolated
 * (LIMIT/OFFSET are clamped numbers, safe to inline).
 *
 * By default duplicates across sources are collapsed: one representative per dedup_key
 * (the most recently posted), annotated with listingCount and the distinct sources.
 * Pass `dedup: false` to get every raw row.
 */
export async function queryOffers(db: D1Database, f: SearchFilter): Promise<CarOffer[]> {
  const { where, binds } = buildWhere(f);
  const whereSql = where.length ? ` WHERE ${where.join(" AND ")}` : "";
  const sort = SORT_SQL[f.sort ?? "NEWEST"] ?? SORT_SQL.NEWEST;
  const limit = Math.min(Math.max(f.limit ?? 50, 1), 200);
  const offset = Math.max(f.offset ?? 0, 0);

  if (f.dedup === false) {
    const sql = `SELECT * FROM offers${whereSql} ORDER BY ${sort} LIMIT ${limit} OFFSET ${offset}`;
    const { results } = await db.prepare(sql).bind(...binds).all<OfferRow>();
    return results.map(rowToOffer);
  }

  // Collapse duplicates with window functions: pick the newest row per dedup_key,
  // and carry the group size + concatenated sources alongside it.
  const sql =
    `WITH filtered AS (SELECT * FROM offers${whereSql}), ` +
    `ranked AS (SELECT *, ` +
    `ROW_NUMBER() OVER (PARTITION BY COALESCE(dedup_key, id) ORDER BY posted_at_ms DESC, id) AS _rn, ` +
    `COUNT(*) OVER (PARTITION BY COALESCE(dedup_key, id)) AS _dup_count, ` +
    `GROUP_CONCAT(source_id) OVER (PARTITION BY COALESCE(dedup_key, id)) AS _dup_sources ` +
    `FROM filtered) ` +
    `SELECT * FROM ranked WHERE _rn = 1 ORDER BY ${sort} LIMIT ${limit} OFFSET ${offset}`;

  type DedupRow = OfferRow & { _dup_count: number; _dup_sources: string | null };
  const { results } = await db.prepare(sql).bind(...binds).all<DedupRow>();
  return results.map((r) => {
    const offer = rowToOffer(r);
    const count = r._dup_count ?? 1;
    if (count > 1) {
      offer.listingCount = count;
      offer.otherSources = [...new Set((r._dup_sources ?? "").split(",").filter(Boolean))];
    }
    return offer;
  });
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
