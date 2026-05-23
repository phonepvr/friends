# Signing the Friends APK

Friends is built and released entirely from GitHub Actions. To install each
new build as a normal update of the previous one, every APK must be signed
with the **same** key. This document is the one-time setup and the recovery
procedure.

## Why this matters

Android refuses to install an APK whose signing certificate differs from the
one currently installed. Without a persistent keystore reused across CI runs,
every new build would be signed by an ephemeral debug key generated on that
runner, and the OS would block the install with "App not installed".

The build is also wired so that `versionCode` auto-increments from
`github.run_number`, so each CI build looks like a distinct version to the OS.

## A) Generate the keystore on GitHub Actions

1. Merge the new `generate-keystore.yml` workflow to the working branch.
2. Go to **Actions → "Generate signing keystore" → Run workflow → Run**.
3. After the run completes, open the run page and:
   - Download the keystore artifact **`signing-keystore`** (a zip containing
     `signing-keystore.b64` and `credentials.txt`).
   - Open `signing-keystore.b64`: it contains exactly one line of base64 —
     that is the value for `SIGNING_KEYSTORE_BASE64`.
   - Open `credentials.txt`: it lists the values for `SIGNING_STORE_PASSWORD`,
     `SIGNING_KEY_PASSWORD`, and `SIGNING_KEY_ALIAS`.
   - The job summary on the workflow run page also prints these values for
     convenience.

## B) Store four repository secrets

Under **Settings → Secrets and variables → Actions**, create these four
repository secrets. Names must match exactly:

- `SIGNING_KEYSTORE_BASE64` — paste the entire base64 string from
  `signing-keystore.b64`.
- `SIGNING_STORE_PASSWORD` — paste from `credentials.txt`.
- `SIGNING_KEY_PASSWORD` — paste from `credentials.txt` (same value as the
  store password, by convention).
- `SIGNING_KEY_ALIAS` — paste from `credentials.txt` (`friends`).

The artifact auto-deletes after 7 days. Once the secrets are saved, you can
also re-run the workflow at any time to download the credentials again.

## C) Back up the keystore — CRITICAL

Save the base64 blob and the three passwords in your password manager. If the
keystore is ever lost you cannot publish an update that installs over the
existing app. You would have to uninstall, reinstall, and restore from backup
every time you wanted a new build.

## D) One-time migration on your phone

Existing builds were signed with ephemeral debug keys, so the first
properly-signed build will not install over them. Do this once:

1. Open the currently-installed Friends app and export a backup
   (**Settings → Backup & restore → Export…**) to a safe location
   (USB drive, encrypted email to yourself, etc.).
2. Uninstall Friends.
3. Wait for the next CI build to publish a Release (tagged
   `v1.0.0-build.N.1` for some new `N`).
4. Install the new signed APK.
5. Open Friends and import the backup file.

From this point on, every CI build installs as a normal update with no data
loss.

## E) If the keystore is ever lost

- The existing install keeps working forever; you just cannot update it.
- To resume updates: re-run step **A** to generate a fresh keystore, replace
  the four secrets, and repeat step **D** once.

## Local development

Local Gradle builds do **not** require the signing secrets. The
`signingConfigs.debug` block in `app/build.gradle.kts` only enforces the
keystore when `GITHUB_ACTIONS=true`. Locally it falls back to AGP's
auto-generated debug key.
