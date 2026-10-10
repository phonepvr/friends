# Releasing Bondwidth

Bondwidth ships through **two separate channels**. Keeping them apart is
deliberate: day-to-day experimentation stays on GitHub, and only intentional
releases reach users.

| | Dev builds (experimentation) | Releases (users) |
| --- | --- | --- |
| Built by | our GitHub Actions CI (`build.yml`) | `release.yml` on GitHub; F-Droid's build servers reproduce it from source |
| Application id | `com.phonepvr.friends.dev`, labelled "Bondwidth (dev)" | `com.phonepvr.friends` |
| Signed with | our release key (see `SIGNING.md`) | the same key (F-Droid ships our signature after verifying its build matches) |
| Trigger | every push to a `claude/**` or `main` branch | a clean `vX.Y.Z` git tag |
| Version | `1.0.<run_number>`, versionCode `run_number * 100 + attempt` | committed `versionCode` / `versionName` |
| Tag | `v1.0.0-build.<N>.<attempt>` (GitHub pre-release) | `vX.Y.Z` |
| Audience | us, for on-device testing (sideload) | end users |

Dev builds use their **own application id**, so they install next to the real app
with separate data and can never block or be mistaken for a release. This matters
because their versionCode (run number × 100) is far higher than a release's: under
the same id, Android would refuse to install any release over one ("App not
installed") and F-Droid could never update it. Releases are signed with the same
key, so a GitHub-sideloaded release and an F-Droid install update over each other.

---

## Day-to-day experimentation (no action needed)

Push to a branch as usual. CI runs `testDebugUnitTest` + `assembleDebug` and,
on success, publishes a `v1.0.0-build.<N>.<attempt>` **pre-release** APK
(`Bondwidth-dev-v1.0.<N>.apk`) signed with our key. These builds are for our own
testing and are **invisible to F-Droid** — the recipe's
`UpdateCheckMode: Tags ^v[0-9.]+$` ignores any tag containing `-build.`. Being
pre-releases, they never take the "Latest" badge from the real release.

## Cutting an F-Droid release

1. **Bump the committed version** in `app/build.gradle.kts`:
   - `versionCode` → previous + 1 (must always increase)
   - `versionName` → the new public version, e.g. `1.1.0`
2. **Add a changelog** at
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
   (the filename is the *versionCode*, e.g. `2.txt`).
3. **Land it on `main`** via a normal PR.
4. **Tag the release commit** with the matching version and push the tag:
   ```sh
   git tag v1.1.0    # must equal versionName, prefixed with v
   git push origin v1.1.0
   ```
   Tags do not trigger our CI (it runs on branch pushes), so this adds no
   experimental build.
5. **F-Droid takes it from there.** Its `UpdateCheckMode`/`AutoUpdateMode`
   detect the new `vX.Y.Z` tag, build the tagged source, sign it, and publish
   — typically within ~24–48h. Nothing to upload.

> Keep `versionName` and the `vX.Y.Z` tag in lockstep. F-Droid reads the
> version from the committed gradle values at the tagged commit, not from the
> tag name.

---

## First-time F-Droid submission (one-off)

1. **Metadata** lives in this repo and is read by F-Droid automatically:
   - `fastlane/metadata/android/en-US/title.txt`, `short_description.txt`,
     `full_description.txt`, `changelogs/<versionCode>.txt`.
   - **Add screenshots** (these need a device/emulator) at
     `fastlane/metadata/android/en-US/images/phoneScreenshots/` (e.g.
     `1.png`, `2.png` …) and an icon at `images/icon.png`. Recommended before
     submitting.
2. **The build recipe** to submit is drafted at
   [`fdroid/com.phonepvr.friends.yml`](fdroid/com.phonepvr.friends.yml).
   Copy it to `metadata/com.phonepvr.friends.yml` in a fork of
   <https://gitlab.com/fdroid/fdroiddata>.
   - `License:` is **`GPL-3.0-only`** (decided): the `LICENSE` is GPL-3.0 and
     nothing grants "or any later version", so `-only` is accurate, and it's
     compatible with the GPL-3.0 Fossify upstreams.
3. **Validate locally** in the official build container:
   ```sh
   fdroid lint com.phonepvr.friends
   fdroid build -l com.phonepvr.friends
   ```
4. **Open a merge request** against `fdroiddata`. After it's reviewed and
   merged, the app appears on F-Droid within ~24–48h.

### Eligibility checklist (Bondwidth already meets these)
- [x] FOSS license with public source (GPL-3.0, this repo).
- [x] No proprietary dependencies (no Firebase/GMS — in fact no network at all).
- [x] Builds with open-source tooling only (Gradle/AGP/Kotlin).
- [x] Upstream attribution preserved (`NOTICE.md`, per-file headers) — required
      by the GPL and by F-Droid for derived works (Fossify Phone/Contacts).
- [ ] Screenshots added under `fastlane/.../images/phoneScreenshots/`.

---

## Optional, later: reproducible builds

F-Droid can verify that *its* build of a tag byte-for-byte matches an APK we
publish, and then ship **our** signature instead of F-Droid's — which would let
F-Droid and GitHub installs update over each other. It requires a reproducible
release-signing setup and a `Binaries:`/reproducible config in the recipe. Not
needed for the initial listing; revisit if we want a single signature across
both channels.
