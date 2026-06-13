# Releasing Autka

How to cut a release and publish to GitHub, Google Play and F-Droid. The app is a sandboxed
Android client with only `INTERNET` + `ACCESS_NETWORK_STATE` permissions and no native code,
so it cannot damage a device. The one irreversible risk in release engineering is **losing the
signing key** — read the keystore section before you ship anything signed.

## 1. Bump the version

In `app/build.gradle.kts`, bump both together (Play and F-Droid reject a build whose
`versionCode` did not increase):

```kotlin
versionCode = 2          // +1 every release, never reused
versionName = "0.2.0"    // human-facing semver
```

Add a matching changelog for the new `versionCode`:

```
fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
fastlane/metadata/android/pl-PL/changelogs/<versionCode>.txt
```

## 2. Activate the release workflow (one-time)

The workflow ships as `docs/release.workflow.yml` because the connector that authored it
cannot write under `.github/workflows/`. Move it into place once:

```bash
git mv docs/release.workflow.yml .github/workflows/release.yml
git commit -m "ci: add release workflow"
```

It triggers on a `v*` tag (or manual dispatch), builds a release APK + AAB, and attaches
them to a GitHub release.

## 3. Signing key (do this once, guard it forever)

`assembleRelease` produces an **unsigned** APK unless a keystore is supplied. Unsigned is
fine for F-Droid (it re-signs every build) but not installable directly and not accepted by
Play. To sign, generate a keystore **once** and reuse it for the life of the app:

```bash
keytool -genkeypair -v \
  -keystore autka-release.keystore \
  -alias autka \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass '<STORE_PASS>' -keypass '<KEY_PASS>' \
  -dname "CN=Autka, O=travino, C=PL"
```

> **Back it up offline before going further.** If you lose this keystore or its passwords you
> can never publish an update to the same Play listing again (you would have to ship a new app
> under a new package). Store the file + passwords in a password manager and an offline copy.
> Never commit it — `.gitignore` already excludes `*.keystore`.

Add four Actions secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 autka-release.keystore` output |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | `autka` |
| `KEY_PASSWORD` | the key password |

The workflow decodes the keystore only when `KEYSTORE_BASE64` is set; the Gradle config wires
up signing only when `AUTKA_KEYSTORE` is present. No key material ever touches the repo.

## 4. Ship it

```bash
git tag v0.2.0
git push origin v0.2.0
```

The workflow builds and creates the GitHub release with the APK + AAB attached. Verify the
run is green and the artifacts are present.

## 5. Google Play

* Upload the **AAB** (`app-release.aab`) — Play requires App Bundles for new apps.
* Listing text and graphics come from `fastlane/metadata/android/` (usable via `fastlane supply`
  or copy/paste into the console).
* Required 512x512 icon and 1024x500 feature graphic live under each locale's `images/`.
* Data safety form: the app collects **no** personal data; it sends only search/filter
  parameters to its own backend. No third-party SDKs, ads or trackers.

## 6. F-Droid

The app is F-Droid-eligible: MIT-licensed, all dependencies are FOSS, maps use osmdroid
(no Google Play Services / Maps key), and there is no proprietary blob.

* Listing metadata is read directly from `fastlane/metadata/android/`.
* Submit by opening a merge request on `fdroiddata` with a build recipe pointing at this repo
  and the `v*` tag, or test locally with `fdroidserver`.
* Expect F-Droid to apply the **NonFreeNet** anti-feature, since the app depends on the hosted
  `cargate-backend` service. The backend source is MIT in this repo, but the label reflects the
  runtime dependency on a network service; it is informational, not a rejection.

## Safety checklist (per release)

* Permissions unchanged (`INTERNET`, `ACCESS_NETWORK_STATE` only) — review the manifest diff.
* `network_security_config.xml` keeps `cleartextTrafficPermitted=false` in `base-config`
  (cleartext is allowed only for `10.0.2.2`/`localhost` dev loopback, never production).
* `versionCode` incremented.
* Keystore + passwords confirmed backed up.
