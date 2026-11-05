# Memora Cloud 0.9.2 — implementation and safe installation

## 1. What this package is

A source update based on main commit **596acfbfc2a0b8f2b2f27272373d89849b19279b** (0.9.0/code 14). The local working branch is `work/clear-gallery`; the new app version is **0.9.2 / code 16**. The completed Phases 1–7 are retained, not offered as new work.

There are **no remote commits, no main-branch merge, no signing-key replacement and no deletion of old deliveries** performed by this package. Import to a test branch first. Phone approval comes before main integration and final repository cleanup.

## 2. Implemented changes

- Gallery opens first. Four tabs: **Gallery, Cloud, Albums, Settings**. No Home dashboard or People bottom-navigation button. Unrelated existing feature source is retained.
- Sun/moon button at the top of each main tab; light/dark choice persists. Settings no longer contains appearance/style controls. Readable high-contrast surfaces, separate action rows, consistent icons and action accents. A small, renamed Manrope-derived font subset is bundled; Bengali uses Android's glyph fallback. Its OFL license is included.
- Pinch together for smaller tiles/more columns; spread for bigger tiles/fewer columns. Default two columns. Preferences are separate for Gallery, Cloud, Albums/folder media/collections, device bin and cloud bin. Preferences are stored in SharedPreferences. A narrower window clamps displayed columns without overwriting the preference. Normal one-finger scrolling, taps and long-press selection remain separate from two-finger handling.
- Photo/video/total totals appear only in Cloud's main header. They use the indexed cloud library, not the visible filter and not upload progress. Local favorites/folder item counts remain contextual item counts. Items externally added/deleted in Telegram need the existing recovery/reindex workflow before the local cloud index can reflect them.
- **Albums → Utilities** contains duplicate scanner, device bin and cloud bin. Duplicate scanner is no longer in Settings.
- Bins start with two columns and offer tap-to-preview, long-press selection, Select All/Deselect All, and compact side-by-side Restore/Delete forever buttons. Preview may be unavailable for a trashed device item until restored because of Android/OEM restrictions.
- App-owned prompts use Move to bin or Delete forever, with Cancel. No added `MANAGE_MEDIA`, all-files permission, or Ocean Media permissions card. Android-owned consent dialogs keep Android's own wording; Allow/Deny cannot be renamed or bypassed.

## 3. Phone favorites — exactly what is supported

On **Android 11/API 30+**, Memora reads `MediaStore.IS_FAVORITE` for locally readable photos/videos. The viewer and Favorites collection use that flag, not Memora's historical SHA-based favorite list. Updates use `MediaStore.createFavoriteRequest` with Android consent; bulk requests are split into groups of at most 500. Canceling does not trigger a private favorite fallback. Return from consent, return to the app and MediaStore changes refresh the data. A refresh requested during an existing query is coalesced, not dropped.

**Google Photos and Infinix AI Gallery must be checked on your phone.** If they publish the same local MediaStore flag, this implementation can share it. Their account-cloud favorites, private databases or OEM-private flags are not readable through this API and are not promised. Android 8–10 cannot use this native favorite API; no separate Memora-only system is substituted there. Cloud-only items cannot be marked as phone favorites until downloaded locally. Limited media access also limits which local favorites can be listed.

**Old Memora favorite records are preserved** in the unchanged organization database and metadata format. They are not silently deleted, copied into phone storage or mixed into the new Favorites view. Existing archive and album behavior is retained.

## 4. Upload reliability and held transfers

Changes are targeted to the existing backup/upload path:

1. A process-wide coroutine gate serializes backup runs, including their reset/scan/send phase. Unique per-attempt cache directories prevent filename collisions. Cancellation releases the gate and removes only that attempt's temporary files.
2. The worker enters the foreground **before** the scan/hash work. Foreground-service refusal returns a retry instead of quietly proceeding as an unprotected long background upload. A lazily started upload child is registered before it can be paused, avoiding the registration/cancellation race.
3. The SendMessage request has a 60-second bound. After it is accepted, actual upload progress extends a two-minute idle deadline; an overall two-hour deadline remains. Telegram message verification is separately bounded at 60 seconds. Unrelated download/recovery calls retain their prior default behavior.
4. SHA, actual copied size and destination are stored in **CONFIRMING before the send request**. Lost acknowledgment, timeout, cancellation after that boundary, verification uncertainty or process death cannot turn into a blind automatic resend. The existing Room schema remains version 2: CONFIRMING is an additional value of the existing string state, not a table/schema change.
5. Known rejected/failed uploads remain eligible for bounded automatic retry (up to the existing attempt limit) with WorkManager exponential backoff. Manual enqueues append work so a request racing an active worker is not lost behind KEEP. Failed/held work no longer appears as a fully completed backup.
6. The UI distinguishes Preparing, Sending and Checking confirmation. A 100% byte bar alone is not the same as verified backup.

### If the app says Needs confirmation

- Open **Settings → Backup**. Connect to Telegram and wait until any current backup run finishes.
- Tap **Check confirmation**. Only positive remote SHA-caption and file-size matches in the recorded destination mark a held entry verified. A timeout, partial history result or missing match leaves it held; the app does **not** claim it was absent or resend it automatically.
- If still held, inspect the destination in Telegram. Only after that, use **Retry held uploads** and explicitly accept its duplicate-risk warning. This retries all held entries. If backup is paused, resume it as well.
- Do not use repeated retry taps as a substitute for checking Telegram. SHA-caption/file-size checking is not a download-and-rehash of every remote original.

This is conservative duplicate prevention, not a guarantee that Telegram/OEM/network failures cannot occur. A send canceled just before acceptance may still need the same confirmation procedure. Phone originals are not deleted by upload recovery.

## 5. Safe GitHub import — phone-friendly

**Do not import directly into main yet.**

1. Create a branch such as `clear-gallery-test` from your current main. Keep a backup of the current source and a known-working signed APK. Do not uninstall Memora or clear its data.
2. Keep your **existing** repository Secrets: `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `TELEGRAM_API_ID`, `TELEGRAM_API_HASH`. Restore missing originals; **do not run Create signing key again for an existing app**. A replacement key cannot update the installed app and uninstalling would erase local data/session state.
3. Upload `memora-cloud-0.9.2-clear-gallery.zip` to the **test branch**. The archive has one `memora-cloud/` root. Keep only one matching source ZIP on that branch when using the existing wildcard Unzip workflow; safely download any older root ZIP before removing it from the test branch. Do not delete unrelated files.
4. Run **Actions → Unzip project → Run workflow**, selecting the **test branch**, not main. The retained original importer replaces `app/src`, so obsolete Ocean UI classes do not survive alongside the new source.
5. **Important: that importer intentionally strips the archive's `.github` directory.** On the test branch, manually replace `.github/workflows/build.yml` with the exact file from this ZIP. Keep the existing `unzip.yml` and `keygen.yml`. The new build workflow runs unit tests, supplies the Telegram API Secrets, and refuses a missing release key instead of silently shipping a differently signed update.
6. Run **Actions → Build APK** on the test branch, using the imported 0.9.2 commit. Ignore any automatic build started from a ZIP-upload commit before the import. Wait for its tests and `assembleRelease` to pass. Download the **memora-apk** artifact and extract its release APK.
7. Install that owner-signed APK as an **update** to the existing app. If Android reports a signature mismatch, stop. Do not uninstall, regenerate a key or clear data to force installation. Check the workflow Secrets against the original key instead.
8. Complete the phone checklist below. Only after your approval should this test branch merge into main. After the main build and update work, do the cleanup in section 8.

The locally built verification APK uses the sandbox debug key and has no owner API credentials. It is **not** the production update and is intentionally not offered for installation over your current app.

## 6. Local build and repeatable checks

Use JDK 17, Android platform/build-tools 34, Gradle 8.9 (wrapper included). No real signing key or Telegram account is needed for unit tests:

```sh
./gradlew testDebugUnitTest assembleDebug --no-daemon --max-workers=1
python3 tools/verify_contracts.py
python3 tools/check_integrity.py
```

For a real release, supply the existing signing environment variables and Telegram API credentials securely, then run `./gradlew testDebugUnitTest assembleRelease`. Never commit them. Release shrinking/obfuscation and resource shrinking stay enabled, and the existing arm64-only ABI and supported-language restrictions are retained.

See `docs/TEST-REPORT.md` for the actual sandbox results and limitations. The CSV inventory records unchanged and specifically authorized backend differences relative to the baseline.

## 7. Required phone acceptance checks

Use a few **disposable test photos/videos**, not irreplaceable originals.

- **Upgrade/data:** update installs without uninstall; Telegram session, destination, existing backups, archive and albums remain. Historical Memora favorites are not silently erased.
- **Tabs/theme:** Gallery first; only four intended bottom tabs; top toggle works in all four; both themes are readable. Change theme, force-stop and reopen; it stays selected. Settings has no appearance or media-management permission card.
- **Grid:** in each category pinch inward/outward; scroll, tap and long-press afterwards. Set different column preferences in Gallery/Cloud/Albums/device bin/cloud bin. Force-stop/reopen and verify each. Rotate to a narrow/wide layout and back; it must not overwrite the saved preference. Also check large font/display-size settings.
- **Native favorites:** in Google Photos favorite a local test item, then return to Memora. Repeat from Infinix AI Gallery. Favorite and unfavorite from Memora, accepting the Android prompt, and check the other apps. Cancel once and verify no unexpected change. Test a cloud-only photo separately: it must not turn into a private Memora favorite. Report Android version and gallery versions if they do not share the flag.
- **Delete/bin:** move a disposable local/cloud item to its bin; cancel once. Preview photo/video, long-press, Select All/Deselect All, Restore, and Delete forever/Cancel. Confirm buttons remain usable in both themes. Check that OS prompts retain system wording and that previews unavailable under OEM restrictions fail safely.
- **Cloud counts:** compare cloud photo/video/total counts to the indexed cloud library; switching to Gallery does not show those totals. Compare separately with upload progress. Recovery/reindex after an external Telegram edit should update the index as before.
- **Uploads:** upload a photo and video; watch prepare/send/confirm and verified completion. Start another manual selection during a run. Test two equal filenames in different folders, a network interruption, Wi-Fi/charging constraints, pause/resume, process/force-stop interruption, and a later restart. Verify no silent resend of held entries. Test Check confirmation and explicit held retry on disposable media only. Check that a held/failed item is not eligible for freeing its only local original.
- **Preserved behavior:** test archive/unarchive, add/remove album membership, cloud download, recovery/reindex, duplicate scan, and existing Free up verified-only safeguards. Do not run destructive bulk free-up on valuable files merely to test the update.

## 8. Main integration and cleanup — last

Only after the phone checklist and your explicit approval:

1. Merge the test branch into main and let the owner-signed main build pass.
2. Confirm that APK updates the phone and retains its data.
3. Remove obsolete root source ZIPs, accidental generated APK/build/cache files, and genuinely unused assets, **after** keeping a known-good delivery/backup outside the repository.
4. Keep active source, tests, wrapper, licenses, implementation/test evidence and the build/import/signing workflows. Do not delete keystores/Secrets, sessions, user media or remote Telegram items.
5. Measure the final **release APK** separately from source ZIP size. Deleting ZIPs or documentation from GitHub does not shrink the APK; native TDLib is a major size contributor. No unsupported APK-reduction percentage is claimed.

No destructive repository cleanup or main-branch integration has been performed on your behalf.
