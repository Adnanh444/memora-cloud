# Remaining launch work (0.9.5)

0.9.5 is a **photo/video test build**, not launch approval. It contains no AI. What is left before this can be published broadly:

## 1. Owner-device verification (first)
- Install the owner-signed release over 0.9.4 on the real phone and confirm data survives.
- Media reality check: local and cloud video, HEIC/HEIF/MOV from an iPhone, an unsupported file, error + retry.
- Data reality check with mobile data only: cloud grid shows no downloads; the switch behaves; cache limit and Clear work.
- Backup and recovery: a real upload, a real restore path, and the documented pause-before-logout rule.

## 2. Distribution prerequisites
- Same signing key for updates; publish only the release APK; keep old releases for rollback.
- Decide direct APK vs a store. Target SDK 34 is retained; modern store requirements (target SDK, 16 KiB native page size, media-permission policy) are unresolved. The bundled TDLib and androidx native libraries were inspected earlier; any new native dependency needs the same check.
- Release notes must say what the build does **not** do: no AI, no bin, no FIFO queue, device-dependent codecs, channel owner can still delete media.

## 3. Privacy / terms / security review
- Privacy policy that matches this build exactly (no AI claims, no telemetry claims beyond the dependency stack's own behaviour).
- Telegram API terms review before any feature that analyses Telegram-derived data (deferred; see `docs/PLAN-v1.0.md`).
- Retained 0.9.2 core still needs the account/session/queue/destination isolation audit, recovery completeness check, and a decision on an owner-approval/revocation service.

## 4. Known gaps deliberately left in this build
- Smart delete + app-managed Memora Bin (needs the storage-permission decision).
- FIFO upload order + queue screen (touches the proven backup queue).
- Lighter-core pass beyond this build (dead-style removal, deeper cache tuning, paging).
- Green Nature theme, 5-tab navigation and the redesigned Home/Cloud/Albums/Search/Settings screens.
- Cloud tools: verify library, share cloud file, storage stats, notifications, moments, app lock extras, GitHub-Releases updater beyond the manual Release flow.
- People / smart search (would need an approved model stack, downloads and the terms review above).

## 5. What must never be claimed
No universal codec support, no protection against Telegram channel deletion, no AI capability, no zero-telemetry guarantee, no unlimited storage guarantee, no owner-approval enforcement.
