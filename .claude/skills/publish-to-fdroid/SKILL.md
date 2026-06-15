---
name: publish-to-fdroid
description: >-
  Deep, battle-tested playbook for publishing a GitHub-hosted Android app to
  F-Droid via a fdroiddata GitLab merge request, including reproducible builds.
  Covers app-repo prerequisites (fastlane metadata, screenshots, F-Droid-readable
  version literals, release signing), the recipe and its canonical format, the
  GitLab MR workflow, a debugging guide for every fdroiddata CI job (fdroid build,
  rewritemeta, checkupdates, schema validation, lint), and the full
  reproducible-build setup (release-variant signing, Binaries,
  AllowedAPKSigningKeys, a signed-release GitHub Action). Use whenever preparing
  or submitting an app to F-Droid, fixing a red fdroiddata pipeline, debugging
  checkupdates/reproducibility, or cutting a new F-Droid release.
---

# Publishing a GitHub Android app to F-Droid (with reproducible builds)

This is the end-to-end runbook for taking an Android app whose **source lives on
GitHub** and getting it onto **F-Droid** — including the reproducible-build setup
that lets your own signature ship through F-Droid. Every gotcha below was hit and
fixed for real; the regexes and field orders are quoted from `fdroidserver`'s
actual source (`common.py` / `metadata.py`), not from memory.

> **Two repos are always in play:**
> - **Your app repo** (GitHub): source, fastlane metadata, screenshots, the `vX.Y.Z` release tag, and (for reproducible builds) a published signed APK.
> - **fdroiddata** (`gitlab.com/fdroid/fdroiddata`): one recipe file, `metadata/<applicationId>.yml`, submitted as a merge request (MR).

## How to drive this as the assistant (operating rules)

1. **Validate locally before every push.** Install `fdroidserver` (§8) and run
   `fdroid rewritemeta`, `fdroid lint`, and the version-parser/`remove_signing_keys`
   simulations. Catching things locally saves a slow GitLab pipeline round-trip.
2. **Edit the GitLab recipe with "Replace" (raw upload), never the web text
   editor**, or you will re-introduce CRLF (§6). Hand the user a clean LF file.
3. **One change at a time, then read the pipeline.** The fdroiddata pipeline has
   ~9 jobs; fix the first failing one, push, re-read.
4. **Anything that changes the *built APK* must be in the tagged commit.** F-Droid
   builds the tag. If you change `build.gradle.kts`, you must **re-point the tag**
   (§3) and bump the recipe's `commit:`.
5. **You usually can't push git tags from CI/bots** — the *user* creates/moves
   tags in the GitHub UI. Plan for that handoff.
6. **Wait for CI between dependent steps** rather than polling in a tight loop.

---

## 1. The F-Droid model (know this or you'll fight it)

- F-Droid **builds the app from source on its own servers** and, by default,
  **signs it with F-Droid's key** — not yours. So an F-Droid install and a
  sideloaded GitHub-release install normally **can't update over each other**
  (different signatures). **Reproducible builds (§7)** fix that: F-Droid verifies
  its from-source build byte-matches *your* published signed APK and then ships
  *your* signature.
- F-Droid tracks releases by **clean `vX.Y.Z` git tags** (configurable via
  `UpdateCheckMode`). Keep any CI/experimental tags **out** of that pattern.
- The recipe's `commit:` should be a **full 40-char commit hash**, not a tag or
  branch (maintainers enforce this — mutable refs are rejected).
- It is normal to have **two channels**: a GitHub experimental channel (your CI
  builds + signs debug/nightly APKs on branch pushes) and the **F-Droid release
  channel** (clean `vX.Y.Z` tags). Keep their version schemes from colliding.

## 2. App-repo prerequisites (GitHub side)

### 2a. Eligibility (F-Droid inclusion criteria)
- **FOSS license** with public source. If you derive from another GPL app, keep
  the **attribution** (NOTICE + per-file headers) — required by the GPL and by
  F-Droid.
- **No proprietary dependencies** (no Google Play Services/Firebase/etc.). Such
  things, plus tracking/ads/non-free network deps, are **Anti-Features**
  (`NonFreeNet`, `NonFreeDep`, `Tracking`, `NonFreeAssets`, `Ads`, …). Declare
  unavoidable ones in the recipe's `AntiFeatures:`; avoidable ones will block
  inclusion.
- **Builds entirely with open tooling** (Gradle/AGP/Kotlin). No network at build
  time beyond declared dependencies.

### 2b. Fastlane metadata + screenshots (scraped automatically from the default branch)
```
fastlane/metadata/android/en-US/
  title.txt
  short_description.txt
  full_description.txt
  changelogs/<versionCode>.txt        # filename IS the versionCode, e.g. 1.txt, 2.txt
  images/phoneScreenshots/1.png 2.png ...   # shown in filename order; 1.png is the hero
  images/icon.png                     # optional
```
- 3–8 screenshots is the sweet spot. F-Droid **re-scrapes**, so they can be added
  or changed later without a new MR.
- **In a web/remote session, images pasted into chat are view-only** (not on disk).
  Have the user upload them to the repo (GitHub "Upload files") or push them; then
  curate/rename with `git mv`.

### 2c. THE critical gotcha — make the version F-Droid-readable
`fdroid checkupdates` (auto-update detection) extracts the version with these
exact regexes (`fdroidserver/common.py`):
```
versionCode:  \b[Vv]ersionCode\s*=?\s*["'(]*([0-9][0-9_]*)["')]*
versionName:  \b[Vv]ersionName\s*=?\s*\(?(["'])((?:...)*?)\1
```
It reads the gradle **line by line, first match wins**, and only uses the result
if the file also contains an `id("com.android.application")` / `apply plugin`
line. It needs a **literal** `versionCode = <int>` and `versionName = "<str>"`
with a word boundary before "version" and a digit/quote right after `=`.

**It cannot follow a variable or expression.** All of these yield
`version=None` → checkupdates fails with *"Couldn't find any version information"*:
```kotlin
val releaseVersionCode = 1                                    // 'release' kills the \b before "Version"
versionCode = System.getenv("X")?.toInt() ?: releaseVersionCode   // no digit right after '='
```
**Correct pattern** — literal first (F-Droid reads it), env/CI override *after*:
```kotlin
defaultConfig {
    versionCode = 1
    versionName = "1.0.0"
    // CI override for nightly/experimental builds runs AFTER the literals,
    // so the literals stay the value F-Droid's scanner reads.
    System.getenv("APP_VERSION_CODE")?.toIntOrNull()?.let { versionCode = it }
    System.getenv("APP_VERSION_NAME")?.let { versionName = it }
}
```
Add a comment so nobody "refactors" them back into a variable. Verify locally:
```python
import fdroidserver.common as c, fdroidserver.metadata as m
from pathlib import Path
app = m.App(); app['id'] = 'com.example.app'
print(c.parse_androidmanifests([Path('app/build.gradle.kts')], app))  # want ('1.0.0', 1, 'com.example.app')
```

## 3. The release tag

- Tag = `v` + exactly the `versionName` (e.g. `v1.0.0`). It must match the recipe's
  `UpdateCheckMode: Tags ^v[0-9.]+$`. Keep CI tags (e.g. `v1.0.0-build.<n>`) OUT of
  that pattern.
- In the recipe, `commit:` = the **full 40-char hash** the tag points at.
- **The user creates the tag** (GitHub → Releases → "Draft a new release" →
  "Create new tag … on publish" → Target the right commit → Publish). Bots usually
  can't `git push` tags.
- **Re-pointing a tag** (needed whenever a build-affecting change must land in the
  release): delete the **Release**, delete the **tag** (`/tags`), then recreate the
  release on the new commit. Then update `commit:` in the recipe.
- **Re-tag discipline:** every time you change `build.gradle.kts` (or anything that
  changes the built APK), the tag must move to include it, and the recipe `commit:`
  must follow. Batch app changes to minimise re-tags.

## 4. The fdroiddata recipe

`metadata/<applicationId>.yml`. **Canonical field order** (from
`metadata.py:yaml_app_field_order`, blank lines significant):
```
Disabled, AntiFeatures, Categories, License, AuthorName, AuthorEmail,
AuthorWebSite, WebSite, SourceCode, IssueTracker, Translation, Changelog,
Donate, …, <blank>, Name, AutoName, Summary, Description, <blank>, RequiresRoot,
<blank>, RepoType, Repo, Binaries, <blank>, Builds, <blank>,
AllowedAPKSigningKeys, <blank>, MaintainerNotes, <blank>, ArchivePolicy,
AutoUpdateMode, UpdateCheckMode, UpdateCheckIgnore, VercodeOperation,
UpdateCheckName, UpdateCheckData, CurrentVersion, CurrentVersionCode, <blank>,
NoSourceSince
```
**Always normalise with `fdroid rewritemeta <appid>` and commit its output** —
don't hand-place fields.

### Minimal new-app recipe (F-Droid signs)
```yaml
Categories:
  - Connectivity        # alphabetical; must be categories fdroiddata knows (§ note)
  - Phone & SMS
License: GPL-3.0-only   # accurate SPDX id
AuthorName: yourname
SourceCode: https://github.com/you/app
IssueTracker: https://github.com/you/app/issues
Changelog: https://github.com/you/app/releases

AutoName: AppDisplayName   # what `fdroid checkupdates --auto` fills in (§6)

RepoType: git
Repo: https://github.com/you/app.git

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: <full-40-char-hash>     # the commit vX.Y.Z points at — NOT the tag
    subdir: app                     # the gradle module
    gradle:
      - yes                         # 'yes' = no product flavors (builds assembleRelease)

AutoUpdateMode: Version             # bare "Version" (NOT "Version v%v" — schema-invalid)
UpdateCheckMode: Tags ^v[0-9.]+$
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```
- **Drop `WebSite:` if it equals `SourceCode:`** (redundant; reviewers remove it).
- **Categories** have no hard-coded list — they come from fdroiddata's own
  `categories` config and the set evolves (Games was split into ~17; "meta"
  categories were added). Use ones already in fdroiddata; if a reviewer suggests a
  different one (e.g. `Contact` for a contacts app), apply their suggestion. List
  them **alphabetically** or rewritemeta will reorder them.
- `UpdateCheckMode` ∈ `{Tags, Tags <regex>, RepoManifest, RepoManifest/<branch>,
  HTTP, None, Static}`. `AutoUpdateMode` ∈ `{None, Static, Version, Version +<suffix>}`.
- `gradle: [yes]` builds `assembleRelease`. Other `Builds` sub-fields you may need:
  `subdir`, `prebuild`, `rm` (delete files pre-build), `scandelete`/`scanignore`
  (satisfy the scanner), `srclibs`, `ndk`, `output`.

### Reproducible-build recipe (F-Droid ships YOUR signature) — see §7
Add to the above: `Binaries:` (after `Repo:`) and `AllowedAPKSigningKeys:` (after
`Builds:`):
```yaml
RepoType: git
Repo: https://github.com/you/app.git
Binaries: https://github.com/you/app/releases/download/v%v/App-%v.apk

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: <hash>
    subdir: app
    gradle:
      - yes

AllowedAPKSigningKeys: <your apk signing cert SHA-256, lowercase hex, no colons>
```
- `Binaries` is a URL template: **`%v` = versionName, `%c` = versionCode**. It must
  resolve to *your* signed APK for the exact version F-Droid builds.

## 5. Submitting the merge request (GitLab)

1. **Fork** `gitlab.com/fdroid/fdroiddata` (your namespace, Public). A new account
   may need phone/identity verification before CI runs.
2. Add `metadata/<applicationId>.yml` on a **new branch named after the app id**
   (never commit to `master`). Tick "Start a new merge request".
3. **New MR:** source `you/fdroiddata:<branch>` → target **`fdroid/fdroiddata:master`**.
   Title `New app: <Name> (<applicationId>)`. Choose the **App inclusion** template,
   delete its "remove these lines" preamble, tick what's true, keep `/label ~"New App"`.
4. **Reviewer suggestions** (e.g. drop `WebSite`, change a category): click **"Add
   suggestion to batch"** on each, then **"Apply N suggestions"** — one commit,
   preserves LF.
5. If GitLab demands phone/credit-card for CI runners, **don't pay** — leave a note;
   a maintainer triggers CI.

## 6. Pipeline debugging playbook (the hard-won part)

The fdroiddata MR pipeline runs ~9 jobs: `fdroid build`, `checkupdates`,
`fdroid rewritemeta`, `fdroid lint`, `schema validation`, `check apk`,
`check source code`, `git redirect`, `tools check scripts`. **Edit the recipe with
"Replace" (raw upload), not the inline editor**, so you don't reintroduce CRLF.

- **`schema validation` fails** → an invalid field value. Classic:
  `AutoUpdateMode: 'Version v%v' does not match` → use bare **`Version`**.

- **`fdroid rewritemeta` fails** → the file isn't byte-canonical. The job prints a
  unified diff of what it wants. Causes, in order of likelihood:
  - **CRLF line endings** — every `-` line ends in `^M`, the `+` lines are identical
    text with LF. Web editors (esp. on Windows) save CRLF. **Fix: LF + a trailing
    newline.** Deliver a clean file and **Replace**-upload it (don't paste into the
    editor). Detect with `grep -c $'\r' file` (want 0) and `file <name>` (want "ASCII
    text", not "…with CRLF line terminators").
  - **Categories not alphabetical**, or any field out of canonical order.
  - Run `fdroid rewritemeta <appid>` locally and commit its exact output.

- **`fdroid build` is red but the log says `BUILD SUCCESSFUL` / `1 build
  succeeded`** → the build worked; a *post-build* check failed. We saw
  `grep '^    versionCode: <n>$'` return `location=-1` purely because the file was
  **CRLF** (`versionCode: 1\r` doesn't end at `1`). The LF fix clears it.

- **`fdroid build` → `SigningConfig with name 'release' not found`** → F-Droid runs
  `remove_signing_keys` before building: it **deletes the whole `signingConfigs { }`
  block and every `signingConfig = …` line** (comments survive). If any *surviving*
  line still references `signingConfigs.getByName("release")` (e.g. a
  `val x = signingConfigs.getByName("release")`), it now points at a deleted config
  → crash. **Fix:** guard signing on an **env var**, so the only line touching the
  config is the `signingConfig = …` line that gets stripped (§7). Verify by running
  the real stripper locally:
  ```python
  import fdroidserver.common as c; c.remove_signing_keys('/path/to/checkout')
  ```
  then confirm no live `signingConfig` reference remains.

- **`checkupdates` → "Couldn't find any version information"** → version isn't a
  literal (§2c). Fix the gradle, **re-point the tag**, bump `commit:`. Debug line in
  the log: `Parsing manifest at 'app/build.gradle.kts' ..got package=…, version=None,
  vercode=None`.

- **`checkupdates` red with a diff that adds `AutoName: <Name>`** →
  `fdroid checkupdates --auto` auto-detected the app's display name and the job fails
  on the uncommitted change. **Add `AutoName: <Name>`** (the value is in the log:
  `got autoname '<Name>'`) in canonical position (after the links block).

- Maintainer comment **"use the full commit hash, not the tag/branch"** → set
  `commit:` to the 40-char hash.

## 7. Reproducible builds — the full setup

Goal: F-Droid builds your tag from source **unsigned**, byte-compares it to *your*
published signed APK, and if identical ships **your** signature.

**The variant trap:** F-Droid builds **`assembleRelease`**. If your CI publishes a
**debug**-variant APK (common for nightly channels), it will never match. You must
**sign and publish the *release* variant**.

### 7a. App: a release signingConfig that survives the strip
```kotlin
android {
    signingConfigs {
        getByName("debug") { /* … your existing debug keystore-from-env … */ }
        create("release") {
            // Same CI keystore as debug; applied only when the SIGNING_* secrets
            // are present. Absent on F-Droid's servers → assembleRelease unsigned.
            val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
            val storePass = System.getenv("SIGNING_STORE_PASSWORD")
            val alias = System.getenv("SIGNING_KEY_ALIAS")
            val keyPass = System.getenv("SIGNING_KEY_PASSWORD")
            if (!keystorePath.isNullOrBlank() && !storePass.isNullOrBlank() &&
                !alias.isNullOrBlank() && !keyPass.isNullOrBlank() &&
                file(keystorePath).exists()
            ) {
                storeFile = file(keystorePath); storePassword = storePass
                keyAlias = alias; keyPassword = keyPass
            }
        }
    }

    buildTypes {
        getByName("release") {
            // CRITICAL: guard on the ENV VAR, not on signingConfigs.getByName(...).
            // F-Droid's remove_signing_keys deletes the signingConfigs block and the
            // 'signingConfig = …' line; a surviving getByName("release") would crash
            // its build. After stripping, this leaves an empty `if` → unsigned. minify
            // stays OFF for determinism.
            if (!System.getenv("SIGNING_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Reproducibility: drop AGP's Google-signed dependency-metadata blob.
    dependenciesInfo { includeInApk = false; includeInBundle = false }
}
```

### 7b. CI: publish the signed release APK on clean tags (`.github/workflows/release.yml`)
```yaml
name: Release APK (signed)
on:
  push:
    tags: ['v[0-9]+.[0-9]+.[0-9]+']     # clean release tags only
  workflow_dispatch: {}                  # to (re)publish an existing tag's APK
permissions:
  contents: write
jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - name: Decode signing keystore
        env: { SIGNING_KEYSTORE_BASE64: '${{ secrets.SIGNING_KEYSTORE_BASE64 }}' }
        run: |
          set -euo pipefail
          echo "$SIGNING_KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/k.jks"
          echo "SIGNING_KEYSTORE_PATH=$RUNNER_TEMP/k.jks" >> "$GITHUB_ENV"
      - name: Build signed release APK
        env:
          SIGNING_STORE_PASSWORD: '${{ secrets.SIGNING_STORE_PASSWORD }}'
          SIGNING_KEY_PASSWORD: '${{ secrets.SIGNING_KEY_PASSWORD }}'
          SIGNING_KEY_ALIAS: '${{ secrets.SIGNING_KEY_ALIAS }}'
          # NOTE: do NOT set APP_VERSION_* — use the committed literals, so this APK
          # is the exact version (versionCode/Name) F-Droid builds and reproduces.
        run: ./gradlew assembleRelease --stacktrace
      - name: Stage + print cert SHA-256 (for AllowedAPKSigningKeys)
        id: s
        run: |
          set -euo pipefail
          V="${GITHUB_REF_NAME#v}"; cp app/build/outputs/apk/release/app-release.apk "App-$V.apk"
          echo "apk=App-$V.apk" >> "$GITHUB_OUTPUT"; echo "tag=$GITHUB_REF_NAME" >> "$GITHUB_OUTPUT"
          A="$(find "$ANDROID_HOME/build-tools" -name apksigner | sort | tail -1)"
          "$A" verify --print-certs "App-$V.apk" | grep -i "SHA-256"   # -> AllowedAPKSigningKeys (drop the label, lowercase, no colons)
      - uses: softprops/action-gh-release@v2
        with: { tag_name: '${{ steps.s.outputs.tag }}', files: '${{ steps.s.outputs.apk }}', fail_on_unmatched_files: true }
```

### 7c. Wire-up order (and the cert SHA-256)
1. Merge 7a+7b to `main`. **Re-point `vX.Y.Z`** to that commit → publishing the tag
   auto-runs `release.yml`, which uploads the signed APK **and prints the cert
   SHA-256** in its log.
2. Put that SHA-256 (lowercase hex, no colons) in `AllowedAPKSigningKeys:`, set
   `Binaries:` to the asset URL (`…/download/v%v/App-%v.apk`), set `commit:` to the
   re-tagged hash, **Replace** the recipe.
3. F-Droid's RB bot then builds the tag and compares. **Reproduces →** RB enabled,
   merged, F-Droid ships your signature. **Diff →** patch determinism and re-tag.
- The cert SHA-256 is the **same across debug and release** (same key), so you can
  read it from any already-signed APK with `apksigner verify --print-certs`.

### 7d. Common non-reproducibility causes (and fixes)
- **AGP dependency-info block** → `dependenciesInfo { includeInApk=false; includeInBundle=false }` (done above).
- **R8/minify** → keep `isMinifyEnabled = false` (or ensure deterministic R8); simpler = more reproducible.
- **Native code** → biggest source of nondeterminism; if present, expect iteration.
  (Pure Kotlin/Compose apps usually reproduce cleanly.)
- **Baseline profiles** → fine if the profile is a *committed* file; bad if generated at build time.
- F-Droid strips your signature before comparing, so v2/v3 signature blocks don't matter.

## 8. Local validation (do this before every push)

Quickest is the official Docker image (no local Android SDK needed):
```bash
git clone https://gitlab.com/<you>/fdroiddata.git && cd fdroiddata
docker run --rm -itu vagrant --entrypoint /bin/bash -v "$PWD":/repo \
  registry.gitlab.com/fdroid/fdroidserver:buildserver
# inside: cd /repo
fdroid rewritemeta <appid>      # normalise formatting (commit the result)
fdroid checkupdates <appid>     # fills AutoName / CurrentVersion
fdroid lint <appid>             # expect no output
fdroid build -v -l <appid>      # full build from the tag
```
`pip install fdroidserver` also works for `rewritemeta`/`lint`/the Python API,
though native deps (`mutf8`) and a broken system Pillow can block it; the Docker
image avoids that. Useful API calls:
- Version parse: `fdroidserver.common.parse_androidmanifests([Path], app)`.
- Strip simulation: `fdroidserver.common.remove_signing_keys('/checkout')` then inspect.
- Canonical order: `fdroidserver.metadata.yaml_app_field_order`.
**False positive to ignore:** outside a real fdroiddata checkout, `fdroid lint`
flags every category as "not valid" (it lacks the category config). CI lint is
authoritative.

## 9. GitHub specifics
- Releases UI creates the tag; **re-pointing = delete release → delete tag (`/tags`)
  → recreate** on the new target. User action (bots can't push tags).
- A **user-created** tag triggers `on: push: tags` workflows; a `GITHUB_TOKEN`-created
  tag does **not** (anti-recursion) — hence `workflow_dispatch` in `release.yml`.
- Read the signing cert SHA-256 straight from a CI job (`apksigner verify
  --print-certs`) instead of asking the user to run keytool.
- Don't delete a published release/APK that `Binaries:` points at — F-Droid fetches it.

## 10. GitLab specifics
- Fork → branch (per app id) → edit → MR into `fdroid/fdroiddata:master`.
- **Reviewer "Suggested changes":** "Add suggestion to batch" → "Apply N suggestions"
  (one commit, keeps LF).
- **Editing files:** the inline single-file editor can save **CRLF** (esp. on
  Windows). Prefer the file's **"Replace"** action (uploads raw bytes → LF
  preserved) or the **Web IDE** (status-bar `CRLF`→`LF` toggle). Don't open the file
  in a desktop editor and re-save (may re-add CRLF).
- You're editing the file on **your fork's MR branch**
  (`gitlab.com/<you>/fdroiddata/-/blob/<branch>/metadata/<appid>.yml`), not on
  upstream `fdroid/fdroiddata` (protected).

## 11. Timeline & future releases
- **Review/merge:** volunteer-paced — days to ~2 weeks for a new app; faster once a
  maintainer is engaged and the pipeline is green. RB adds a verification step.
- **After merge:** appears in the F-Droid client in ~24–48h.
- **Future releases (hands-off once §2c + §7 are in place):** bump the literal
  `versionCode` (+1) and `versionName`, add `changelogs/<versionCode>.txt`, land on
  `main`, tag `vX.Y.Z`. `release.yml` auto-publishes the signed APK; F-Droid's
  `UpdateCheckMode: Tags` + `AutoUpdateMode: Version` auto-detect and build the tag.
  No new fdroiddata MR needed.

## 12. Quick-reference: symptom → cause → fix
| Symptom | Cause | Fix |
|---|---|---|
| `checkupdates`: "Couldn't find any version information" | version is a var/expr | literal `versionCode`/`versionName` (§2c) + re-tag |
| `checkupdates`: diff adds `AutoName` | auto-detected name not committed | add `AutoName: <Name>` |
| `rewritemeta` diff, `-` lines end in `^M` | CRLF line endings | LF + trailing newline; Replace-upload |
| `rewritemeta` diff on Categories | not alphabetical / wrong order | run `fdroid rewritemeta`, commit output |
| `schema validation`: AutoUpdateMode | `Version v%v` invalid | bare `Version` |
| `fdroid build` red but "BUILD SUCCESSFUL" | CRLF breaks a post-build `grep` | LF fix |
| `fdroid build`: SigningConfig 'release' not found | strip removed config, live ref survived | guard signing on env var (§7a) |
| RB diff reported | non-deterministic build | `dependenciesInfo` off, no minify, check natives (§7d) |
| Maintainer: "use commit hash" | tag/branch in `commit:` | full 40-char hash |

## Sources
- F-Droid contributor guide: <https://github.com/f-droid/fdroiddata/blob/master/CONTRIBUTING.md>
- Build metadata reference: <https://f-droid.org/docs/Build_Metadata_Reference/>
- Reproducible builds: <https://f-droid.org/docs/Reproducible_Builds/>
- Inclusion how-to: <https://f-droid.org/docs/Inclusion_How-To/>
- Submitting quick-start: <https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/>
- Categories taxonomy update (2026): <https://f-droid.org/2026/05/22/twif.html>
- Authoritative behaviour: `fdroidserver` source — `common.py` (`parse_androidmanifests`, `remove_signing_keys`, version regexes), `metadata.py` (`yaml_app_field_order`), `checkupdates.py`.
