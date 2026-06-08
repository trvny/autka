/// <reference types="@cloudflare/vitest-pool-workers/types" />
import type { D1Migration } from "@cloudflare/vitest-pool-workers";

// vitest-pool-workers 0.16+ types `env` (from "cloudflare:test") as Cloudflare.Env,
// so test-only bindings are added by augmenting that interface rather than ProvidedEnv.
declare global {
  namespace Cloudflare {
    interface Env {
      TEST_MIGRATIONS: D1Migration[];
      ADMIN_TOKEN: string;
    }
  }
}
