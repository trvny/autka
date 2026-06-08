import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-pool-workers";
import { defineConfig } from "vitest/config";

export default defineConfig(async () => {
  // Read migrations at config time (Node side), then apply them in a setup file.
  const migrations = await readD1Migrations("./migrations");
  return {
    // vitest-pool-workers 0.16 (vitest 4) replaces defineWorkersConfig + test.poolOptions.workers
    // with the cloudflareTest() plugin; what was poolOptions.workers is now its argument.
    plugins: [
      cloudflareTest({
        singleWorker: true,
        // R2 streamed bodies trip isolated-storage teardown (known pool issue);
        // tests are written to be order-independent so this is safe to disable.
        isolatedStorage: false,
        wrangler: { configPath: "./wrangler.jsonc" },
        miniflare: {
          bindings: { TEST_MIGRATIONS: migrations },
        },
      }),
    ],
    test: {
      setupFiles: ["./test/apply-migrations.ts"],
    },
  };
});
