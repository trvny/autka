import { Hono } from "hono";

export const imagesRouter = new Hono<{ Bindings: Env }>();

/**
 * GET /images/<key> — serve an offer image from R2 by key.
 *
 * Read-only: it only returns objects already stored by the ingestion pipeline, so
 * there is no on-demand fetch of arbitrary URLs (no open proxy / SSRF surface).
 * The body is streamed straight from R2 — never buffered into memory.
 */
imagesRouter.get("/images/:key{.+}", async (c) => {
  const key = c.req.param("key");
  const object = await c.env.IMAGES.get(key);
  if (!object) return c.json({ error: "not_found" }, 404);

  const headers = new Headers();
  object.writeHttpMetadata(headers); // content-type etc. from stored metadata
  headers.set("etag", object.httpEtag);
  headers.set("cache-control", "public, max-age=86400, immutable");

  // Conditional request support — return 304 when the client already has it.
  if (c.req.header("if-none-match") === object.httpEtag) {
    return new Response(null, { status: 304, headers });
  }
  return new Response(object.body, { headers });
});
