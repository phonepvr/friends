# Bondwidth

**A privacy-first, fully offline phone & contacts app that helps you keep up with the people who matter.**

Bondwidth is an all-in-one Android **dialer**, **contacts manager**, and **relationship companion**. It replaces your phone and contacts apps with something that works entirely on your device — no accounts, no servers, no tracking — and quietly helps you stay close to the people you care about.

![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)
![Platform: Android](https://img.shields.io/badge/platform-Android-3DDC84.svg)
![minSdk 26](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-orange.svg)
![Network: none](https://img.shields.io/badge/network-none-success.svg)

---

## The idea

Friendship is small attentions, often. It's easy to let weeks slide by without realising you haven't spoken to someone who matters. Bondwidth is a tiny ledger of the people you care about: set a check-in **cadence** for each person, and the app gently flags who's getting too quiet — so you can reach out before silence becomes a habit. Birthdays and anniversaries are remembered for you, and the calls you make are counted automatically.

It's also a genuinely capable phone and contacts app, so the "did I call them recently?" question answers itself.

## Private by design

Privacy isn't a setting in Bondwidth — it's the architecture.

- **No internet permission. At all.** The app does not request `INTERNET` or `ACCESS_NETWORK_STATE`. They're explicitly *removed* from the merged manifest, and **the CI build fails if the final APK declares either one**, so a stray dependency can never quietly add networking.
- **No accounts, no servers, no cloud.** Everything lives in your device's app sandbox. There is nothing to sign into and nowhere for your data to go.
- **No analytics, no ads, no trackers.** No third-party SDKs phoning home.
- **Backups stay yours.** Export is a single local file you place wherever you choose, and it can be sealed with a passphrase using **AES-256-GCM** (key stretched with PBKDF2-HMAC-SHA256). Auto Backup and device-transfer extraction are disabled, so the OS won't copy your data off-device either.
- **Lockable & shoulder-surf resistant.** Optional biometric / device-credential **app lock**, plus a "hide from screenshots" mode that blocks screenshots, screen recording, the recents thumbnail, and casting — including the in-call screen.

Because there's no network code, the only way data leaves the device is a file *you* deliberately export or a contact *you* deliberately share.

## Features

### 📞 Phone
- **Default dialer** with its own in-call UI — incoming-call screen over the lock screen, Picture-in-Picture, per-contact ringtones, and proper ringer-silence handling.
- **T9 smart dial** with ranked results, and **speed dial** on dialpad keys 1–9.
- **Dual-SIM aware** — a "Call with" picker when you have two call-capable SIMs and no default set.
- **Block & reject** numbers (system blocked-numbers list), with **missed-call notifications** offering one-tap *Call back* / *Message*.
- **Call history** per number and a recents list with **in-tab contact search**.
- **Colour-coded call directions** — blue outgoing, green incoming, red missed — consistent across recents, history, and each person's timeline.
- **WhatsApp / Signal** quick actions on a number (shown only when the app is installed).

### 👥 Contacts
- All-in-one **contacts browser** with **group filtering**, reading and writing the system Contacts provider.
- **Create / edit / delete** contacts with typed **phone numbers, emails, postal address, website, organisation, notes**, and **birthday / anniversary**, plus contact **photos**.
- **Find & merge duplicates** (shows each candidate's numbers before you confirm).
- **vCard import** (open a `.vcf` from anywhere) and **export / share** a contact as a `.vcf`.

### 🤝 Bonds
- Per-person **check-in cadence** (every 7 / 14 / 30 days, or whatever fits); overdue people are surfaced and colour-coded.
- **Birthday & anniversary reminders** a few days ahead, with a *Mark as wished* button right on the notification.
- An **interaction timeline** — calls are logged automatically from your call log; meets, messages, and notes you can add by hand.
- A **Width dashboard** with at-a-glance health: cadence breakdown, talk-time, connection health, and a year-in-review.
- A scrollable **home-screen widget** for upcoming occasions and who you haven't spoken to in a while.

### ✨ Polish
- Material 3 with optional dynamic colour, light/dark themes, a curated warm palette, and tasteful haptics.
- A daily quote you can add your own lines to.

## Permissions, and why

Bondwidth asks only for what a phone-and-contacts app genuinely needs, and explains each at first use:

| Permission | Why |
| --- | --- |
| `READ_CONTACTS` / `WRITE_CONTACTS` | Browse, add to, and edit your address book |
| `READ_CALL_LOG` | Detect recent calls so check-ins count automatically |
| `WRITE_CALL_LOG` | Manage the call log as the default dialer — delete a call, clear or re-import history |
| `CALL_PHONE` | Place calls from the in-app dialer |
| `READ_PHONE_STATE` / `READ_PHONE_NUMBERS` | Route calls through the right SIM and surface call state |
| `MANAGE_OWN_CALLS` / `ANSWER_PHONE_CALLS` | Act as the default phone app |
| `FOREGROUND_SERVICE` (+ `_PHONE_CALL`) | Keep the in-call service alive during a call |
| `WAKE_LOCK`, `MODIFY_AUDIO_SETTINGS` | Stay awake during a call and route its audio |
| `USE_FULL_SCREEN_INTENT` | Show the incoming-call screen |
| `POST_NOTIFICATIONS` | Reminders and call notifications |

**Deliberately absent:** `INTERNET`, `ACCESS_NETWORK_STATE`. (The manifest also declares narrow `<queries>` for `tel:` / `sms:` and the WhatsApp/Signal packages — no broad package visibility.)

## Tech

Kotlin · Jetpack Compose (Material 3) · Hilt · Room · Preferences DataStore · WorkManager · Glance (widget) · Coil · AndroidX Biometric. **minSdk 26 (Android 8.0), targetSdk 35.**

## Install

Each successful CI build publishes a signed APK to [**Releases**](../../releases) named `Bondwidth-v1.0.<build>.apk`. Download it and sideload (you'll be prompted to allow installs from your browser/file manager the first time).

> On Android 13+, making a sideloaded app your default phone app requires a one-time **Settings → App info → ⋮ → Allow restricted settings** step; Bondwidth detects when this blocks the request and walks you through it.

## Build

```sh
./gradlew assembleDebug      # build the APK
./gradlew testDebugUnitTest  # run unit tests
```

No SDKs or keys are required for a local debug build. Stable release signing (so each build installs as an update of the last) is documented in [`SIGNING.md`](SIGNING.md).

## A note on the name

The app is **Bondwidth** everywhere you see it. Some internal identifiers still use the project's original name — the package is `com.phonepvr.friends`, the database is `friends.db` (`FriendsDatabase`), and the theme is `Theme.Friends`. These are kept deliberately: renaming them would orphan existing installs' data. It's the same app.

## License & attribution

Bondwidth is licensed under the **GNU General Public License v3.0 only** (`GPL-3.0-only`) — see [`LICENSE`](LICENSE).

Its phone and contacts surfaces are derived from the excellent GPL-3.0 **[Fossify Phone](https://github.com/FossifyOrg/Phone)** and **[Fossify Contacts](https://github.com/FossifyOrg/Contacts)** projects; full attribution is in [`NOTICE.md`](NOTICE.md).
