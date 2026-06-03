import { Hono } from "hono";
import { offersRouter } from "./routes/offers";
import { adminRouter } from "./routes/admin";
import { imagesRouter } from "./routes/images";
import { runIngestion } from "./ingest/runner";

const app = new Hono<{ Bindings: Env }>();

app.get("/", (c) => c.json({ service: "cargate-backend", status: "ok" }));
app.get("/health", (c) => c.json({ status: "ok", time: new Date().toISOString() }));

app.route("/", offersRouter);
app.route("/", adminRouter);
app.route("/", imagesRouter);

// Structured error response — no passThroughOnException; explicit handling.
app.onError((err, c) => {
  console.error(JSON.stringify({ msg: "unhandled_error", error: err.message }));
  return c.json({ error: "internal_error" }, 500);
});

app.notFound((c) => c.json({ error: "not_found" }, 404));

export default {
  fetch: app.fetch,

  // Cron trigger (configured in wrangler.jsonc). Runs ingestion on schedule.
  async scheduled(_event: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    // waitUntil keeps the run alive for the duration of the async work.
    ctx.waitUntil(
      runIngestion(env)
        .then((results) => {
          console.log(JSON.stringify({ msg: "scheduled_ingest_done", results }));
        })
        .catch((err) => {
          // Defensive: runOne isolates per-source failures, but a DB write in
          // recordRun could still reject. Log rather than drop it silently.
          console.error(JSON.stringify({
            msg: "scheduled_ingest_error",
            error: err instanceof Error ? err.message : String(err),
          }));
        }),
    );
  },
} satisfies ExportedHandler<Env>;
