import { Hono } from "hono";
import { runIngestion } from "../ingest/runner";
import { timingSafeEqualStr } from "../lib/auth";

export const adminRouter = new Hono<{ Bindings: Env }>();

// POST /admin/ingest — manually trigger an ingestion run. Protected by a bearer token
// (ADMIN_TOKEN secret). The cron trigger runs the same logic automatically.
adminRouter.post("/admin/ingest", async (c) => {
  const auth = c.req.header("authorization") ?? "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!c.env.ADMIN_TOKEN || !(await timingSafeEqualStr(token, c.env.ADMIN_TOKEN))) {
    return c.json({ error: "unauthorized" }, 401);
  }
  const results = await runIngestion(c.env);
  return c.json({ results });
});
