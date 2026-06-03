import { Hono } from "hono";
import type { SearchFilter, FuelType, Region } from "../lib/types";
import { queryOffers, getOffer } from "../db/offers";
import { ALL_SOURCES } from "../ingest/runner";

export const offersRouter = new Hono<{ Bindings: Env }>();

// Whitelists for validating untyped query input (validate, don't assert).
const FUEL_TYPES: readonly FuelType[] = [
  "PETROL", "DIESEL", "HYBRID", "PLUGIN_HYBRID", "ELECTRIC", "LPG", "OTHER", "UNKNOWN",
];
const REGIONS: readonly Region[] = ["POLAND", "EUROPE", "USA"];
const SORTS: readonly NonNullable<SearchFilter["sort"]>[] = [
  "NEWEST", "PRICE_ASC", "PRICE_DESC", "MILEAGE_ASC", "YEAR_DESC",
];

// GET /offers — search with query params mirroring the app's SearchFilter.
offersRouter.get("/offers", async (c) => {
  const q = c.req.query();
  const num = (v: string | undefined) => (v != null && v !== "" ? Number(v) : undefined);
  const list = (v: string | undefined) => (v ? v.split(",").filter(Boolean) : undefined);
  // Keep only values that are members of the allowed set; drop anything unknown.
  const subset = <T extends string>(v: string | undefined, allowed: readonly T[]): T[] | undefined => {
    const items = list(v)?.filter((x): x is T => (allowed as readonly string[]).includes(x));
    return items && items.length ? items : undefined;
  };

  const sortRaw = q.sort;
  const filter: SearchFilter = {
    query: q.query || undefined,
    make: q.make || undefined,
    model: q.model || undefined,
    minPrice: num(q.minPrice),
    maxPrice: num(q.maxPrice),
    minYear: num(q.minYear),
    maxYear: num(q.maxYear),
    maxMileageKm: num(q.maxMileageKm),
    fuelTypes: subset(q.fuelTypes, FUEL_TYPES),
    regions: subset(q.regions, REGIONS),
    sourceIds: list(q.sources),
    sort: (SORTS as readonly string[]).includes(sortRaw) ? (sortRaw as SearchFilter["sort"]) : "NEWEST",
    dedup: q.dedup !== "false", // collapse duplicates unless explicitly disabled
    limit: num(q.limit),
    offset: num(q.offset),
  };

  const offers = await queryOffers(c.env.DB, filter);
  return c.json({ offers, count: offers.length });
});

// GET /offers/:id — single offer.
offersRouter.get("/offers/:id", async (c) => {
  const offer = await getOffer(c.env.DB, c.req.param("id"));
  if (!offer) return c.json({ error: "not_found" }, 404);
  return c.json(offer);
});

// GET /sources — which sources exist and whether they're enabled (for app UI toggles).
offersRouter.get("/sources", (c) => {
  const sources = ALL_SOURCES.map((s) => ({
    id: s.sourceId,
    displayName: s.displayName,
    enabled: s.isEnabled(c.env),
  }));
  return c.json({ sources });
});
