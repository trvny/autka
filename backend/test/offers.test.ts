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

// R2-backed responses stream their body; drain it inside the execution context.
async function getBytes(path: string, init?: RequestInit) {
  const ctx = createExecutionContext();
  const res = await worker.fetch(new Request(`https://x${path}`, init), env, ctx);
  const buf = await res.arrayBuffer();
  await waitOnExecutionContext(ctx);
  return { res, buf };
}

describe("backend", () => {
  beforeAll(async () => {
    (env as unknown as { ADMIN_TOKEN: string }).ADMIN_TOKEN = "test-token";
    // Populate the DB once for all read tests (isolated storage is disabled).
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

  it("filters by fuelType (regression: filter was previously ignored)", async () => {
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

    // Mock data has no manual cars, so this is empty (filter is applied, not ignored).
    const manual = await call("/offers?transmissions=MANUAL");
    expect((await manual.json() as { count: number }).count).toBe(0);

    // Invalid value is dropped, not asserted -> all results.
    const bad = await call("/offers?transmissions=NOPE");
    expect(bad.status).toBe(200);
    expect((await bad.json() as { count: number }).count).toBeGreaterThan(0);
  });

  it("tolerates invalid sort/enum params (validate, don't assert)", async () => {
    const badSort = await call("/offers?sort=garbage");
    expect(badSort.status).toBe(200);
    expect((await badSort.json() as { count: number }).count).toBeGreaterThan(0);

    const badFuel = await call("/offers?fuelTypes=NOPE");
    expect(badFuel.status).toBe(200);
    expect((await badFuel.json() as { count: number }).count).toBeGreaterThan(0);
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

  it("collapses cross-source duplicates (dedup)", async () => {
    // Same car, two marketplaces: identical make/model/year/fuel/trans/mileage-bucket.
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
    expect(body.count).toBe(1); // two listings collapsed into one
    expect(body.offers[0].listingCount).toBe(2);
    expect(body.offers[0].otherSources?.sort()).toEqual(["olx", "otomoto"]);

    // Opting out returns both raw rows.
    const raw = await call("/offers?make=VW&dedup=false");
    expect((await raw.json() as { count: number }).count).toBe(2);
  });

  it("rejects unauthorized ingest", async () => {
    const res = await call("/admin/ingest", { method: "POST" });
    expect(res.status).toBe(401);
  });
});
