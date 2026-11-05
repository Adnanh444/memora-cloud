# Memora Cloud 0.9.4 — dark contrast / legacy Telegram guide

## Scope selected by the user

**Keep the 0.9.3 interface. Restore the Telegram system from 0.9.2. Fix dark-mode readability.** No new credential server, Telegram credential-note system, AI or owner-authorization service is added.

Version **0.9.4 / code 18** allows an update over 0.9.3/code 17 using the same original signing identity. Local branch `work/dark-contrast`, based on preserved local 0.9.3 commit `223fdcf`. That local snapshot is not recovered remote Git history. Both earlier delivered ZIPs and the 0.9.3 branch are retained. No remote repository changes have been made.

## Dark text fix

The Day/Night palettes already defined dark/light foreground roles, but MaterialTheme alone does not set LocalContentColor. Plain Text/Icon content under a transparent scaffold could therefore inherit default black in dark mode.

The theme now supplies `LocalContentColor` from the active `onBackground` role; the main transparent Scaffold explicitly uses that foreground too. Existing surfaces, input fields, buttons, errors and disabled controls keep their appropriate component colors. Day mode stays dark-on-light; dark mode becomes light-on-dark. Android's own permission/consent screens are not restyled. No preference reset or new theme card is added.

`tools/verify_dark_contrast.py` checks the wiring plus 38 explicit light/dark foreground/background palette pairs at >=4.5:1. This is static color math, not proof of every rendered pixel, alpha blend, overlay, accessibility setting or OEM screen. Phone validation remains required.

## Telegram implementation is 0.9.2

Nine files are restored byte-for-byte from the delivered 0.9.2 ZIP: the entire Telegram package, BackupEngine, RecoveryEngine, TelegramScreen and RecoveryScreen. `docs/verification/telegram-0.9.2-baseline.json` records those baseline hashes; the source contracts verify each. SecureKeys, storage notes/key formats, organization metadata, Room schema 2, downloader and original uncertainty-held upload behavior are not rewritten.

The 0.9.3-only CredentialPolicy, HistoryScanRules and DestinationScope helpers and their now-unwired tests are removed from this new branch; they remain in the preserved 0.9.3 source/branch. The lower test count is an explicit rollback, not a claim that the removed behavior is still tested or present.

### In-app login

1. Open **Cloud → Connect**, or **Settings → Telegram storage**.
2. On a fresh, unconfigured installation, the API ID/hash form appears directly, as in 0.9.2—not behind Advanced.
3. Enter the application credentials obtained from Telegram's developer portal. They are stored encrypted locally using the existing Android Keystore-backed implementation.
4. Continue with your own phone number, Telegram code and two-step password if enabled.
5. Existing saved manual credentials and session data remain; no clear-data migration is requested. If an older build supplied API credentials only inside the APK, this intentionally empty-API CI build will ask for API ID/hash once in the app, even though the existing session database is retained.

The normal CI workflow deliberately does not inject API ID/hash. It builds without those two Secrets, even if old API Secrets remain in the repository. The unchanged TelegramConfig still supports optional credentials supplied by a local/custom build, just as 0.9.2 did. No real credentials are added to source.

### Replacement phone / restore

Keep an independent secure record of API ID/hash. On a new phone/reinstall, enter them again **before** Telegram login. Telegram cannot supply those credentials before authenticating its client. There is no automatic fetch from Saved Messages and no new API-credential export/import feature.

After login to the same Telegram account, choose **Restore existing backup**, select the discovered existing storage and rebuild the index. Do not create another storage channel if the goal is recovery. Keep the channel and existing Saved Messages recovery notes. Restoring an index does not download every original; use Cloud downloads separately.

### Explicit rollback boundaries

Per the user's request, these 0.9.3 additions are **not retained**: strict/bounded discovery/history scanning, single-flight/shared recovery-upload operation gating, positive-page incremental persistence, explicit metadata-restoration warning state, held-positive-message rediscovery correction, automatic backup pause on logout, thumbnail-cache clearing on auth closure and active-destination-scoped backend visibility/coverage.

The 0.9.2 recovery implementation has weaker failure/reporting/resume behavior and retains its original limitations, including the held-ID rediscovery issue. A completed scan does not prove every original or all album/archive metadata is recoverable. **These are known limits, not newly fixed in this patch.** Do not rely on one cloud copy for irreplaceable originals. Metadata/key notes remain account-based recovery, not independent end-to-end media encryption.

Before logout, **pause backup manually**. Use a single Telegram account/destination per installation; neither 0.9.2 nor this patch provides full account-scoped queue/dedup/organization isolation. Do not switch valuable pending queues to another account. Existing local cache can remain in the installation. The optional app lock protects the app UI, not another gallery or a rooted/modified device.

## Interface retained

- Gallery / Cloud / Albums / Settings, no Home dashboard.
- Animated Gallery photo/video coverage rings and compact bars; backup strip follows collapse state. Denominator is readable local media, numerator is the legacy ledger's VERIFIED keys, not global cloud totals. Signed/unverified state and stale/multi-account ledger limits still apply; these are not a destructive-deletion authorization.
- Separate Cloud library totals, small New album header icon, top fixed 2×2 Albums utilities and distinct selection/delete glyphs.
- Scoped metadata search (not visual AI), native favorites, bins/previews, independent persisted grid pinch/theme, archive/album behavior and optional system-credential app lock retained.

## Safe test-branch installation

1. Keep your working app, old test branch and signing-key backup. Create **`dark-contrast-test` from main**. Do not merge into main yet.
2. Upload only **`memora-cloud-0.9.4-dark-contrast.zip`** as the root matching source ZIP. Back up older matching root ZIPs before removing them from this test branch; the wildcard importer needs one input. Do not delete unrelated files.
3. Run **Actions → Unzip project**, selecting **`dark-contrast-test`**. The ZIP has one `memora-cloud/` root.
4. The original importer strips `.github`. **Manually replace `.github/workflows/build.yml` on the test branch with the version from this ZIP.** Otherwise the old API Secrets error will continue. Keep original importer/signing-key workflows.
5. Retain existing signing Secrets: **KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD**. Do not generate a replacement key. API ID/hash Secrets are not required by this workflow.
6. Run **Build APK** on the imported branch commit. Download artifact **`memora-apk-0.9.4`**, containing the APK and SHA-256. The outer artifact uses compression level 0; CI checks APK ZIP integrity. This does not repair an interrupted download.
7. Extract completely and install as an **update**. Stop on signature mismatch; never uninstall or clear data as a workaround. Enter API credentials in the app only if this installation needs them.
8. After phone approval, merge actual source/workflow into main, build again from main, install and smoke-test, create a version tag/Release, then delete the merged test branch. Do not delete a branch with unmerged work.

## Phone acceptance checklist

Use disposable media and a spare installation for recovery. Never wipe the only working copy to test restore.

- Upgrade: same signer, retained session/albums/archive/favorites/theme/grid preferences; installed version 0.9.4.
- Dark mode: Gallery, Cloud, Albums, Settings, Telegram forms, Backup, Search, bins, recovery, dialogs/sheets, details and selection headers. Inspect titles, counts, body text, hints/input text, action/error/disabled labels. Switch repeatedly, restart, rotate and use large text. Day mode must remain readable too.
- Motion: rings/compact bars/backup strip; normal scroll, pinch, long press, selection and pull-to-refresh remain functional.
- Telegram: fresh installation shows API setup directly; save once, authenticate, reopen without repeated setup. Existing installation retains its credentials/session. Replacement-phone restore requires API credentials first.
- Backup: two photos/one short video upload; Cloud originals download/open. Test short network loss, pause/resume and uncertain-upload checks. Do not blindly release held uploads.
- Recovery: confirm known originals from a spare installation and inspect album/archive metadata separately. Legacy Done is not a full integrity guarantee. Avoid concurrent recovery/upload; pause manually first.
- Existing favorites, bin previews/restore/delete consent, duplicates, album/archive actions and app lock still work. Test app lock cold start, background grace, rotation, consent return and screenshots.
- Do not test destructive bulk Free up space on valuable originals. Current local hash freshness and complete remote hash verification remain audit items.

## Repeatable local checks

JDK 17, SDK/build-tools 34 and Gradle 8.9:

```sh
./gradlew testDebugUnitTest assembleDebug --no-daemon --max-workers=1
python3 tools/verify_contracts.py
python3 tools/verify_dark_contrast.py
python3 tools/check_integrity.py
```

Use the **existing owner signing environment** for a real update build; local sandbox APKs use a sandbox debug certificate and are not offered as updates. See `docs/TEST-REPORT.md` for actual results, staged release JVM settings and untested device/network boundaries.

Visual AI/people search, deployed owner approval and production profile/security isolation remain pending in `docs/LAUNCH-AND-AI.md`. This is not public-launch approval.
