# Release build — 1.2.0

Everything here runs on GitHub. This sandbox cannot produce a release APK: R8 needs more than the
2 GB of RAM available here and gets killed, so the signed APK always comes from the runner.

## 1. Create the signing key — once, ever

Actions → **Create signing key** → *Run workflow*.

It generates a 4096-bit RSA key (`alias memora`, valid ~27 years), stores it in four repository
Secrets, prints the certificate fingerprint, and uploads a backup:

```
memora-signing-key-backup-DOWNLOAD-AND-KEEP-OFFLINE.zip
```

**Download that artifact immediately and keep two copies offline.** Artifacts expire after 7 days,
and the keystore is deleted from the runner as soon as the job ends. If this key is lost:

- the app can never be updated again,
- every installed copy must be uninstalled to install a new one,
- uninstalling wipes the app's database (index, bin, queue).

The job refuses to run twice, and refuses to run at all on a public repository.

## 2. Build the signed APK

Push to a branch, or Actions → **Build APK** → *Run workflow*.

The build fails loudly if the signing Secrets are missing — it will never silently ship a
debug-signed APK as a release. Each run prints:

- `Signer #1 certificate SHA-256` — must always be the same value
- the APK's own SHA-256, saved next to it as `.apk.sha256`

Artifact: **memora-apk-1.2.0**.

## 3. What 1.2.0 changes for release

| Item | Before | Now |
|---|---|---|
| compileSdk / targetSdk | 34 | **35** (Android 15; Play's minimum for updates) |
| `READ_MEDIA_VISUAL_USER_SELECTED` | declared + requested | **removed** — a backup needs the whole library, partial access would silently hide items |
| Permissions | 10 | **9**, all used: INTERNET, READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_EXTERNAL_STORAGE (≤32), WRITE_EXTERNAL_STORAGE (≤28), MANAGE_EXTERNAL_STORAGE, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC |
| Signer check | none | `apksigner verify --print-certs` printed on every build |
| R8 + TDLib | keeps present | unchanged, and now guarded by a contract test |

**targetSdk 35 enforces edge-to-edge drawing.** After installing, check that the top bar and the
floating nav bar are not under the status bar or the gesture bar. If they are, that is the one
thing to report — it is fixed in the insets, not by reverting the SDK level.

## 4. Why Play Protect may flag this app, and what to do

The combination that triggers heuristics is: reads all media, holds all-files access, uploads in
the background over the network, and is signed with a key Google has never seen. That is also the
signature of an exfiltration trojan — the difference is intent, which a scanner cannot see.

What already reduces the risk, in this build:

- no accessibility service, no screen overlay, no SMS/contacts/location/camera permissions
- no `QUERY_ALL_PACKAGES`, no `REQUEST_INSTALL_PACKAGES`, nothing that installs other apps
- no cleartext traffic, `allowBackup="false"`
- only one exported component (the launcher activity)
- uploads always run in a **visible** foreground service with a progress notification
- no third-party analytics or ad SDKs at all; the only network endpoint is Telegram

If it is still blocked, file the appeal:

1. Scan the APK on **VirusTotal**, open the *Details* tab, copy the **SHA-256**.
2. Open the **Play Protect Appeals Submission Form**:
   <https://support.google.com/googleplay/android-developer/contact/protectappeals>
3. Enter your email, developer name, package name (`com.adnan.memora`), the VirusTotal SHA-256,
   and a two-sentence description: *"Personal media backup app. The user connects their own
   Telegram account and their own private channel as backup storage. All uploads run in a visible
   foreground service."*
4. Submit. Google normally answers only if there is a problem; a silent outcome means the appeal
   was accepted, usually within 3–5 days.

The permanent fix is distribution through Play itself — an app installed from Play is not treated
as unknown. Until then, "Install anyway" is one tap.

## 5. Install over an existing copy

Same key, so `adb install -r memora.apk` or a normal tap-install upgrades in place and keeps data.
If you ever see **"App not installed"**, that is a signature mismatch: do **not** uninstall —
report it, because it means a build was signed with a different key.
