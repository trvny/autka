import { env, createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { describe, it, expect, beforeAll } from "vitest";
import worker from "../src/index";
import { upsertOffers } from "../src/db/offers";

async function call(path: string, init?: RequestInit) {
  const ctx = createExecutionContext();
  const res = await worker.fetch(new Request(`https://x${path}`, init), env, ctx);
  await waitOnExecutionContext(ctx);
  return res;
}

async function getBytes(path: string, init?: RequestInit) {
  const ctx = createExecutionContext();
  const res = await worker.fetch(new Request(`https://x${path}`, init), env, ctx);
  const buf = await res.arrayBuffer();
  await waitOnExecutionContext(ctx);
  return { res, buf };
}

describe("backend", () => {
  beforeAll(async () => {
    const mutableEnv = env as unknown as {
      ADMIN_TOKEN: string;
      ENABLE_MOCK_SOURCE: string;
    };
    mutableEnv.ADMIN_TOKEN = "test-token";
    mutableEnv.ENABLE_MOCK_SOURCE = "true";
    const res = await call("/admin/ingest", {
      method: "POST",
      headers: { authorization: "Bearer test-token" },
    });
    expect(res.status).toBe(200);
  });

  it("health responds ok", async () => {
    const res = await call("/health");
    expect(res.status).toBe(200);
    expect(await res.json()).toMatchObject({ status: "ok" });
  });

  it("lists sources with mock enabled", async () => {
    const res = await call("/sources");
    const body = await res.json() as { sources: { id: string; enabled: boolean }[] };
    expect(body.sources.find((s) => s.id === "mock")?.enabled).toBe(true);
  });

  it("filters by make", async () => {
    const res = await call("/offers?make=BMW");
    const body = await res.json() as { offers: { make: string }[]; count: number };
    expect(body.count).toBeGreaterThan(0);
    expect(body.offers.every((o) => o.make === "BMW")).toBe(true);
  });

  it("filters by fuelType", async () => {
    const diesel = await call("/offers?fuelTypes=DIESEL");
    const body = await diesel.json() as { offers: { fuelType: string }[]; count: number };
    expect(body.count).toBeGreaterThan(0);
    expect(body.offers.every((o) => o.fuelType === "DIESEL")).toBe(true);

    const lpg = await call("/offers?fuelTypes=LPG");
    expect((await lpg.json() as { count: number }).count).toBe(0);
  });

  it("filters by transmission", async () => {
    const auto = await call("/offers?transmissions=AUTOMATIC");
    const body = await auto.json() as { offers: { transmission: string }[]; count: number };
    expect(body.count).toBeGreaterThan(0);
    expect(body.offers.every((o) => o.transmission === "AUTOMATIC")).toBe(true);

    const manual = await call("/offers?transmissions=MANUAL");
    expect((await manual.json() as { count: number }).count).toBe(0);

    const bad = await call("/offers?transmissions=NOPE");
    expect(bad.status).toBe(200);
    expect((await bad.json() as { count: number }).count).toBeGreaterThan(0);
  });

  it("tolerates invalid enum and numeric params", async () => {
    const badSort = await call("/offers?sort=garbage&limit=NaN&offset=nope");
    expect(badSort.status).toBe(200);
    expect((await badSort.json() as { count: number }).count).toBeGreaterThan(0);

    const badFuel = await call("/offers?fuelTypes=NOPE");
    expect(badFuel.status).toBe(200);
    expect((await badFuel.json() as { count: number }).count).toBeGreaterThan(0);
  });

  it("returns total count before limit and offset", async () => {
    const res = await call("/offers?dedup=false&limit=1&offset=0");
    const body = await res.json() as { offers: unknown[]; count: number };
    expect(body.offers).toHaveLength(1);
    expect(body.count).toBeGreaterThan(body.offers.length);
  });

  it("uses a deterministic id tie-breaker across offset pages", async () => {
    const tied = ["e", "a", "d", "b", "c"].map((suffix) => ({
      id: `tie:${suffix}`,
      sourceId: "tie",
      title: `Tie ${suffix}`,
      make: "TieMake",
      model: suffix,
      year: 2020,
      mileageKm: 10_000,
      price: { amount: 50_000, currency: "PLN" as const },
      fuelType: "PETROL" as const,
      transmission: "MANUAL" as const,
      powerHp: null,
      location: null,
      region: "POLAND" as const,
      thumbnailUrl: null,
      imageUrls: [] as string[],
      listingUrl: `https://example.test/tie/${suffix}`,
      postedAtEpochMs: null,
      latitude: null,
      longitude: null,
    }));
    await upsertOffers(env.DB, tied);

    const first = await call("/offers?make=TieMake&dedup=false&sort=NEWEST&limit=2&offset=0");
    const second = await call("/offers?make=TieMake&dedup=false&sort=NEWEST&limit=2&offset=2");
    const third = await call("/offers?make=TieMake&dedup=false&sort=NEWEST&limit=2&offset=4");
    const ids = [first, second, third].flatMap(async () => []);
    void ids;

    const page1 = await first.json() as { offers: { id: string }[] };
    const page2 = await second.json() as { offers: { id: string }[] };
    const page3 = await third.json() as { offers: { id: string }[] };
    expect([...page1.offers, ...page2.offers, ...page3.offers].map((o) => o.id))
      .toEqual(["tie:a", "tie:b", "tie:c", "tie:d", "tie:e"]);
  });

  it("does not silently compare native amounts across currencies", async () => {
    const res = await call("/offers?minPrice=100000&sort=PRICE_ASC&limit=200");
    const body = await res.json() as { offers: unknown[]; warnings?: string[] };
    expect(res.status).toBe(200);
    expect(body.offers.length).toBeGreaterThan(0);
    expect(body.warnings).toEqual(expect.arrayContaining([
      "price_filter_applied_client_side",
      "price_sort_applied_client_side",
    ]));
  });

  it("serves images from R2 with caching headers and 304 support", async () => {
    const key = "offers/test_offer/0";
    await env.IMAGES.put(key, new Uint8Array([0x89, 0x50, 0x4e, 0x47]), {
      httpMetadata: { contentType: "image/png" },
    });

    const ok = await getBytes(`/images/${key}`);
    expect(ok.res.status).toBe(200);
    expect(ok.res.headers.get("content-type")).toBe("image/png");
    expect(ok.res.headers.get("cache-control")).toContain("max-age");
    expect(new Uint8Array(ok.buf).length).toBe(4);

    const etag = ok.res.headers.get("etag")!;
    const notModified = await getBytes(`/images/${key}`, { headers: { "if-none-match": etag } });
    expect(notModified.res.status).toBe(304);

    const missing = await getBytes("/images/offers/does_not_exist/0");
    expect(missing.res.status).toBe(404);
  });

  it("collapses cross-source duplicates", async () => {
    const base = {
      title: "VW Golf 1.6 TDI 2017", make: "VW", model: "Golf", year: 2017,
      mileageKm: 120_000, fuelType: "DIESEL" as const, transmission: "MANUAL" as const,
      powerHp: 110, location: "Wroclaw, PL", region: "POLAND" as const,
      thumbnailUrl: null, imageUrls: [] as string[], latitude: null, longitude: null,
    };
    await upsertOffers(env.DB, [
      { ...base, id: "otomoto:dup1", sourceId: "otomoto", price: { amount: 45_000, currency: "PLN" }, listingUrl: "https://o/1", postedAtEpochMs: 2 },
      { ...base, id: "olx:dup1", sourceId: "olx", price: { amount: 44_500, currency: "PLN" }, listingUrl: "https://x/1", postedAtEpochMs: 1 },
    ]);

    const deduped = await call("/offers?make=VW");
    const body = await deduped.json() as { offers: { id: string; listingCount?: number; otherSources?: string[] }[]; count: number };
    expect(body.count).toBe(1);
    expect(body.offers[0].listingCount).toBe(2);
    expect(body.offers[0].otherSources?.sort()).toEqual(["olx", "otomoto"]);

    const raw = await call("/offers?make=VW&dedup=false");
    expect((await raw.json() as { count: number }).count).toBe(2);
  });

  it("removes source rows missing from the next successful snapshot", async () => {
    await upsertOffers(env.DB, [{
      id: "mock:stale", sourceId: "mock", title: "Sold car", make: "Gone", model: "Now",
      year: 2020, mileageKm: 1, price: { amount: 1, currency: "PLN" },
      fuelType: "PETROL", transmission: "MANUAL", powerHp: null,
      location: null, region: "POLAND", thumbnailUrl: null, imageUrls: [],
      listingUrl: "https://example.test/stale", postedAtEpochMs: 1,
      latitude: null, longitude: null,
    }]);
    await env.DB.prepare("UPDATE offers SET fetched_at_ms = 0 WHERE id = ?")
      .bind("mock:stale").run();

    const ingest = await call("/admin/ingest", {
      method: "POST",
      headers: { authorization: "Bearer test-token" },
    });
    expect(ingest.status).toBe(200);
    expect((await call("/offers/mock:stale")).status).toBe(404);
  });

  it("rejects unauthorized ingest", async () => {
    const res = await call("/admin/ingest", { method: "POST" });
    expect(res.status).toBe(401);
  });
});
