# 0.9.5 media update — implementation and phone tests

## Scope / provenance
Based on 0.9.4; 0.9.2 Telegram login/backup/recovery retained; 0.9.3 interface and 0.9.4 contrast retained. Photos/videos only. **No AI in this build** — the previously prepared AI subsystem (ONNX runtime, CLIP/SFace models, OCR, index, people) is removed from the source tree, so the APK carries no inference engine and no model payload. The work is preserved in `docs/PLAN-v1.0.md` for a later, separately approved step.

## Interface changes
- Gold photo / blue video counter tints shared by ring and line; two persisted Gallery/Cloud visibility toggles.
- Compact selection badge and aligned targets; single narrow bottom toolbar in the viewer.
- Sign-in redesign (presentation only): three-step indicator, paste buttons, inline validation, disabled-until-valid buttons with in-button progress, password reveal, clearer help text.
- New Settings rows: **Cloud previews on mobile data**, **Preview cache limit** (200/500/1000 MB), **Clear preview cache**.

## Telegram behavior / channel convenience
Unchanged except the requested helper: a warning explaining that channel deletion can remove backups, and an opt-in confirmed **Archive & mute** that archives and mutes the verified backup channel. It never deletes originals, notes, messages or the channel and cannot prevent owner deletion.

## In-app playback / iPhone media
Gallery and Cloud videos play through Media3 with lifecycle pause, error/retry, and a temporary cloud-original download. HEIC/HEIF/MOV/HEVC/HDR support depends on device codecs; no transcoding or universal format claim. Unsupported images show an explicit message.

## Data use and cache
- Metered connections fetch **no** cloud previews by default; cells stay empty until you allow it or open an item deliberately (opening downloads that one original temporarily).
- Preview cache ceiling is user-set (default 500 MB) with oldest-first eviction and a Clear action.
- Telegram's own media cache is left to TDLib's storage optimizer; an explicit TDLib ceiling would need one additive API call and has **not** been made in this build.

## Safe branch import / build
1. Create a branch (e.g. `media-0.9.5`) and upload the ZIP contents. If your importer strips `.github/`, recreate `.github/workflows/build.yml` through the web UI and paste the bundled file.
2. Repository secrets must be the **same four** used by 0.9.4: `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The workflow fails rather than generating a new key. No API Secrets are used.
3. Run **Actions → Build APK** (or build locally with JDK 17 / SDK 34 / Gradle 8.9). The signed release APK installs over 0.9.4 only with the same key — never uninstall or replace the key to force an update.
4. Publish through **Releases**: tag `v0.9.5`, attach `app-release.apk` and its `.sha256`, keep `v0.9.4` available for rollback. Publish the release APK only, never the debug one.

## Required phone tests — disposable originals / spare restore installation
1. Install over 0.9.4: account, albums, favourites, backup settings and history remain.
2. Gold/blue counters match reality; both toggles persist after restart.
3. Local video and cloud video: play, pause, leave app, return, force an error (airplane mode) and Retry.
4. Selection badge compact; viewer actions in the bottom strip; swipe through several photos.
5. Wi-Fi off: cloud grid shows empty cells, no data spike; allow the switch and confirm previews return; open one cloud video and confirm the size prompt/app behaviour.
6. Settings: change the cache ceiling, press Clear preview cache, confirm grids still work and nothing was lost.
7. Backup two new photos on Wi-Fi: progress and completion are truthful; nothing is deleted from the phone.
8. Sign-in on a spare installation: credentials (paste), phone, code, 2FA (reveal), finish. Check wrong code, back navigation, Bengali/English and dark mode.
9. Report results with screenshots; only broken items get a corrective build.

## Local checks / evidence
`tools/verify_contracts.py` (source contracts incl. "no AI payload", "cloud previews gated", "caches bounded", "sign-in keeps backend untouched"), `tools/verify_dark_contrast.py`, `tools/check_integrity.py`, JVM unit tests, release compile + vital lint, debug APK, R8, optimized release APK. Results are in `docs/TEST-REPORT.md` and `docs/verification/`.

## Before public release
Owner-signed build, phone tests above, live Telegram account check, review of Telegram terms for any future cloud analysis, dependency/security review, target-SDK and native-page review, and a privacy policy that matches what the build actually does. What this build does **not** include: AI/OCR/people, smart delete + bin, FIFO queue, Green Nature theme, cloud tools.
