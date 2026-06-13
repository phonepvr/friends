# Signing the Bondwidth APK

Bondwidth is built and released entirely from GitHub Actions. To install each
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

This is a **public** repository, and Actions artifacts on a public repo can be
downloaded by third parties. So the workflow never uploads the raw key: it
encrypts the whole bundle with a passphrase only you hold and uploads just the
ciphertext.

1. Pick a strong, random passphrase (e.g. 30+ characters from a password
   manager) and save it — you need it to decrypt the bundle.
2. Add it as a repository secret named **`KEYSTORE_EXPORT_PASSPHRASE`** under
   **Settings → Secrets and variables → Actions**.
3. Go to **Actions → "Generate signing keystore" → Run workflow → Run**. The
   run fails fast if that secret is not set.
4. When it finishes, download the artifact **`signing-keystore-encrypted`**
   (it contains one file, `keystore-bundle.enc`).
5. Decrypt it locally — the exact command is also printed in the run's job
   summary:
   ```sh
   read -rs EXPORT_PASSPHRASE && export EXPORT_PASSPHRASE   # type your passphrase
   openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 \
     -in keystore-bundle.enc -out bundle.tar -pass env:EXPORT_PASSPHRASE
   tar xf bundle.tar
   ```
   This yields `signing-keystore.b64` (the value for `SIGNING_KEYSTORE_BASE64`)
   and `credentials.txt` (the store/key passwords and the alias).

## B) Store four repository secrets

Under **Settings → Secrets and variables → Actions**, create these four
repository secrets. Names must match exactly:

- `SIGNING_KEYSTORE_BASE64` — paste the entire base64 string from
  `signing-keystore.b64`.
- `SIGNING_STORE_PASSWORD` — paste from `credentials.txt`.
- `SIGNING_KEY_PASSWORD` — paste from `credentials.txt` (same value as the
  store password, by convention).
- `SIGNING_KEY_ALIAS` — paste from `credentials.txt` (`bondwidth`).

The encrypted artifact auto-deletes after 7 days and is harmless even before
then. Once the secrets are saved you can re-run the workflow at any time to
produce a fresh encrypted bundle.

## C) Back up the keystore — CRITICAL

Save the base64 blob and the three passwords in your password manager. If the
keystore is ever lost you cannot publish an update that installs over the
existing app. You would have to uninstall, reinstall, and restore from backup
every time you wanted a new build.

## D) One-time migration on your phone

Existing builds were signed with ephemeral debug keys, so the first
properly-signed build will not install over them. Do this once:

1. Open the currently-installed Bondwidth app and export a backup
   (**Settings → Backup & restore → Export…**) to a safe location
   (USB drive, encrypted email to yourself, etc.).
2. Uninstall Bondwidth.
3. Wait for the next CI build to publish a Release (tagged
   `v1.0.0-build.N.1` for some new `N`).
4. Install the new signed APK.
5. Open Bondwidth and import the backup file.

From this point on, every CI build installs as a normal update with no data
loss.

## E) If the keystore is ever lost

- The existing install keeps working forever; you just cannot update it.
- To resume updates: re-run step **A** to generate a fresh keystore, replace
  the four secrets, and repeat step **D** once.

## F) Rotating the key (after a suspected exposure)

If the signing key may have leaked, rotate it:

1. Regenerate by repeating steps **A**–**B**: a fresh keystore and new secrets,
   which means a new signing certificate.
2. Do the one-time on-device migration in step **D** once (export a backup,
   uninstall, install the next signed build, import the backup). Android treats
   the new certificate as a different signer, so an in-place update across the
   rotation is not possible — this is expected.

Older releases signed with the previous key keep working until uninstalled;
they just can't be updated by builds signed with the new key.

## Local development

Local Gradle builds do **not** require the signing secrets. The
`signingConfigs.debug` block in `app/build.gradle.kts` uses the keystore only
when all four `SIGNING_*` environment variables are set and the keystore file
exists (as they are in CI). Locally it falls back to AGP's auto-generated debug
key.
