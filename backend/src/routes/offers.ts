import { Hono } from "hono";
import type { SearchFilter, FuelType, Transmission, Region } from "../lib/types";
import { countOffers, queryOffers, getOffer } from "../db/offers";
import { ALL_SOURCES } from "../ingest/runner";

export const offersRouter = new Hono<{ Bindings: Env }>();

const FUEL_TYPES: readonly FuelType[] = [
  "PETROL", "DIESEL", "HYBRID", "PLUGIN_HYBRID", "ELECTRIC", "LPG", "OTHER", "UNKNOWN",
];
const REGIONS: readonly Region[] = ["POLAND", "EUROPE", "USA"];
const TRANSMISSIONS: readonly Transmission[] = ["MANUAL", "AUTOMATIC", "UNKNOWN"];
const SORTS: readonly NonNullable<SearchFilter["sort"]>[] = [
  "NEWEST", "PRICE_ASC", "PRICE_DESC", "MILEAGE_ASC", "YEAR_DESC",
];

// GET /offers — search with query params mirroring the app's SearchFilter.
offersRouter.get("/offers", async (c) => {
  const q = c.req.query();
  const num = (v: string | undefined) => {
    if (v == null || v === "") return undefined;
    const parsed = Number(v);
    return Number.isFinite(parsed) ? parsed : undefined;
  };
  const list = (v: string | undefined) => {
    const items = v?.split(",").map((item) => item.trim()).filter(Boolean);
    return items?.length ? items : undefined;
  };
  const subset = <T extends string>(v: string | undefined, allowed: readonly T[]): T[] | undefined => {
    const items = list(v)?.filter((x): x is T => (allowed as readonly string[]).includes(x));
    return items?.length ? items : undefined;
  };

  const sortRaw = q.sort;
  const requestedSort = (SORTS as readonly string[]).includes(sortRaw)
    ? (sortRaw as SearchFilter["sort"])
    : "NEWEST";
  const priceSortRequested = requestedSort === "PRICE_ASC" || requestedSort === "PRICE_DESC";
  const priceFilterRequested = num(q.minPrice) != null || num(q.maxPrice) != null;

  const filter: SearchFilter = {
    query: q.query || undefined,
    make: q.make || undefined,
    model: q.model || undefined,
    // Native prices are mixed PLN/EUR/USD. These stay undefined until the backend
    // stores a normalized price; current Android clients filter after NBP conversion.
    minPrice: undefined,
    maxPrice: undefined,
    minYear: num(q.minYear),
    maxYear: num(q.maxYear),
    maxMileageKm: num(q.maxMileageKm),
    fuelTypes: subset(q.fuelTypes, FUEL_TYPES),
    transmissions: subset(q.transmissions, TRANSMISSIONS),
    regions: subset(q.regions, REGIONS),
    sourceIds: list(q.sources),
    sort: priceSortRequested ? "NEWEST" : requestedSort,
    dedup: q.dedup !== "false",
    limit: num(q.limit),
    offset: num(q.offset),
  };

  const [offers, count] = await Promise.all([
    queryOffers(c.env.DB, filter),
    countOffers(c.env.DB, filter),
  ]);
  const warnings = [
    ...(priceFilterRequested ? ["price_filter_applied_client_side"] : []),
    ...(priceSortRequested ? ["price_sort_applied_client_side"] : []),
  ];
  return c.json({ offers, count, ...(warnings.length ? { warnings } : {}) });
});

// GET /offers/:id — single offer.
offersRouter.get("/offers/:id", async (c) => {
  const offer = await getOffer(c.env.DB, c.req.param("id"));
  if (!offer) return c.json({ error: "not_found" }, 404);
  return c.json(offer);
});

// GET /sources — which marketplace sources exist and whether they're enabled.
offersRouter.get("/sources", (c) => {
  const sources = ALL_SOURCES.map((s) => ({
    id: s.sourceId,
    displayName: s.displayName,
    enabled: s.isEnabled(c.env),
  }));
  return c.json({ sources });
});
