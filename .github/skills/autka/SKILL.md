---
name: autka
description: Work on the trvny/autka project (Kotlin/Compose Android app + Cloudflare Worker backend, used-car aggregator) — review changes against the load-bearing invariants, fix a dead/empty/stale marketplace source, add a new ingest source, do Android work (screens, ViewModels, repositories, DI, Gradle/version catalog), or poke the D1 database. Use whenever autka comes up — "review autka", "check this PR", "autka has no offers", "Otomoto stopped showing up", "the feed broke", "add a source", "ingest X", any screen/ViewModel/Compose/Gradle change, D1/ingest_runs questions, or general Android/Kotlin/Compose work in any repo (the android reference generalizes) — even when the request just names a file or class. Read the matching reference file before acting.
license: Complete terms in LICENSE.txt
---

# autka (trvny/autka)

A used-car aggregator: Kotlin/Compose Android app (`/app`, package `com.autka`) reading from a Cloudflare Workers backend (`/backend`, TypeScript + D1 + R2) that aggregates marketplaces **server-side**. The app binds a single `BackendCarOfferSource` (+ `MockCarOfferSource`); per-marketplace adapters live in `backend/src/ingest/sources/`, registered in `runner.ts`'s `ALL_SOURCES`. Credentials and feeds stay off the device.

The invariants in one breath: offline-first (Room/D1 are the source of truth); source isolation (per-source try/catch in `runIngestion`; one feed can't sink the rest; each run records an `ingest_runs` row); **CarOffer parity** between `backend/src/lib/types.ts` and `com.autka.core.model.CarOffer` (the #1 seam bug); **no scraping** of ToS-protected sites — no compliant feed → disabled stub; currency converted through PLN before compare/sort; `core/` stays Android-free; DataStore not SharedPreferences; PL + default string parity; AGP 9 (`kotlin { compilerOptions {} }`, not the removed `android.kotlinOptions`); versions only via `gradle/libs.versions.toml`; maps via osmdroid (no Google Maps key).

## Working from claude.ai chat

The repo isn't on disk and `gh`/`wrangler`/Gradle aren't available — you can't build, run, lint, or emulate. Two ways to work:

- **github connector** (`github:get_file_contents`, `github:push_files`, `github:create_pull_request`) — preferred. Read before you write; branch, don't commit to `main`, for app changes; put an adapter file + its `runner.ts` registration in **one commit**. Run `github:run_secret_scanning` on anything that could carry a key.
- **`git clone` in the bash sandbox** only for local checks (`npm --prefix backend ci && npx --prefix backend tsc --noEmit`); no GitHub auth, so public-only. If private, stay connector-only and watch CI (`android-ci.yml`: `lintDebug assembleDebug testDebugUnitTest`). Never paste a token into chat.

Never claim it compiles — point build signal at CI and report commit SHA + run conclusion.

## Pick the task

| Task | Read |
|---|---|
| Review a change/PR against the eleven load-bearing invariants | `references/review.md` |
| A source is dead/empty/stale; app shows old results — diagnose from `ingest_runs`, minimal adapter fix | `references/source-fix.md` |
| Add a marketplace/source — compliance gate, adapter, registration, CarOffer parity | `references/add-source.md` |
| Android work — architecture map, stack/versions, UDF/ViewModel/Screen/repository patterns, Gradle, testing | `references/android.md` |
| Query or maintain the D1 database — diagnostic SQL, schema changes | `references/d1-ops.md` |

Read the reference fully before editing; the invariants are enforced nowhere else.
