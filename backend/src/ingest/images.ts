import type { CarOffer } from "../lib/types";

const MAX_IMAGE_BYTES = 5 * 1024 * 1024; // don't cache anything absurdly large

/** Make an offer id safe to use as an R2 key / URL path segment (e.g. "mock:1" -> "mock_1"). */
function safeId(id: string): string {
  return id.replace(/[^a-zA-Z0-9._-]/g, "_");
}

/**
 * Best-effort image caching. Fetches each of an offer's images and stores it in R2,
 * rewriting the URLs to backend-served `/images/...` paths so the app gets stable,
 * CDN-backed URLs instead of (possibly hotlink-protected or expiring) marketplace ones.
 *
 * Every failure is swallowed and the original URL kept, so a bad image never breaks
 * ingestion. The fetched URLs come from the source adapter, not from user input.
 */
export async function cacheOfferImages(env: Env, offer: CarOffer): Promise<CarOffer> {
  const urls = offer.imageUrls.length
    ? offer.imageUrls
    : offer.thumbnailUrl
      ? [offer.thumbnailUrl]
      : [];
  if (urls.length === 0) return offer;

  const out: string[] = [];
  for (let i = 0; i < urls.length; i++) {
    out.push((await cacheOne(env, offer.id, i, urls[i])) ?? urls[i]);
  }
  return { ...offer, imageUrls: out, thumbnailUrl: out[0] ?? offer.thumbnailUrl };
}

async function cacheOne(env: Env, offerId: string, index: number, url: string): Promise<string | null> {
  try {
    if (!/^https?:\/\//i.test(url)) return null; // already relative/cached

    const key = `offers/${safeId(offerId)}/${index}`;
    // Already cached on a previous run? Skip the source refetch and the R2 write.
    // head() is a cheap Class B op; the put() it replaces is a Class A op (plus the
    // outbound image fetch), so this removes the bulk of per-run R2 cost. Cached
    // images are treated as immutable — a listing whose photos change normally
    // gets a new offer id (hence a new key) anyway.
    if (await env.IMAGES.head(key)) return `/images/${key}`;

    const res = await fetch(url, { signal: AbortSignal.timeout(5000) });
    if (!res.ok || !res.body) return null;
    const contentType = res.headers.get("content-type") ?? "";
    if (!contentType.startsWith("image/")) return null;
    const declaredLen = Number(res.headers.get("content-length") ?? "0");
    if (declaredLen > MAX_IMAGE_BYTES) return null;

    await env.IMAGES.put(key, res.body, { httpMetadata: { contentType } });
    return `/images/${key}`;
  } catch {
    return null; // network/parse/storage error — keep the original URL
  }
}
