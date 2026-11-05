# 0.9.5 — what is in this build

Version **0.9.5 (code 19)**, based on your 0.9.4 install. Photos and videos only. **No AI:** no model, no OCR, no face grouping, no analysis index, no downloads. Ancient scope decisions are recorded in `PLAN-v1.0.md`.

## Added

**1. In-app video (Gallery and Cloud)** — Media3 player, seek/pause, pause on leaving the app, error card with Retry, cloud videos play from a temporary download of the real original. Device codecs decide formats; no transcoding, no universal iPhone promise.

**2. Gold photo / blue video coverage** — ring and compact bar share one tint (deep gold photos, deep blue videos), labels readable in light and dark (≥ 4.5:1), independent Gallery/Cloud toggles that survive restart.

**3. Selection and viewer polish** — compact numeric badge, aligned targets, one narrow bottom toolbar in the viewer.

**4. Channel-deletion warning + Archive & mute** — the warning explains what losing the channel means; the archive/mute runs only after your confirmation and cannot stop the channel owner deleting media.

**5. Data-friendly cloud previews** — on mobile data nothing is fetched for cloud thumbnails; cells stay empty, and the only transfer is an item you deliberately open. Switch in Settings: *Cloud previews on mobile data* (default off). Phone gallery thumbnails never touch the network.

**6. Bounded caches** — preview cache ceiling 200/500/1000 MB (default 500, oldest out first), **Clear preview cache** action, memory cache capped at 12 % of RAM. Telegram's own media cache stays with TDLib's storage optimizer; this build does not set a TDLib ceiling (that needs one additive TDLib call, which would change the 0.9.2 boundary you asked me to keep intact).

**7. Telegram sign-in redesign (UI only)** — three-step indicator (API access → verification → ready), paste buttons on the API ID/hash fields, inline validation, disabled-until-valid buttons with an in-button spinner, password reveal for 2FA, clearer help text, complete English + Bengali. Authentication, credential storage, channel handling, backup and recovery are untouched.

**8. Lighter source** — the whole AI subsystem, its models registry, licences, unit tests and three dependencies (ONNX Runtime, ML Kit text recognition, ML Kit face detection) are removed.

## Measured size
See `TEST-REPORT.md` for the exact release/debug APK bytes, SHA-256 and the delta against 0.9.4 (25,397,299 bytes).

## Not in this build
Smart delete + Memora Bin, FIFO upload queue and queue screen, Green Nature theme/5 tabs/Home dashboard, cloud tools (verify library, share cloud file, storage stats, notifications, moments), and People/smart search. Those are staged in `PLAN-v1.0.md` and each needs its own decision (storage permission, backup-queue change, model licence) before it is built.

## Publishing on GitHub
1. Push this source to a branch; if the importer drops `.github/`, recreate `.github/workflows/build.yml` in the web editor and paste the bundled file.
2. Keep the same four secrets as 0.9.4: `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. No API secrets are used.
3. **Actions → Build APK → Run workflow**; download artifact `memora-apk-0.9.5` (`app-release.apk` + `.sha256`).
4. **Releases → Draft a new release**, tag `v0.9.5`, attach the release APK and checksum, publish. Keep `v0.9.4` for rollback.
5. Install over 0.9.4 — same key installs, different key means uninstall and data loss. Publish only the release APK, never the debug build.

Optional automation later (one file changes, `build.yml`): a `release:` job with `permissions: { contents: write }` using `softprops/action-gh-release@v2` on `v*` tags.
