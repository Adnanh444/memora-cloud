# 0.9.5 architecture and scope

Photos/videos only. One Compose app, Room ledger, WorkManager backup, TDLib as the user's own Telegram storage. No AI subsystem in this build.

## UI / playback
- `ui/screens/MediaGrid.kt` renders both grids; `CoveragePanel.kt` + `CounterControls.kt` own the gold photo / blue video tints and the two persisted visibility toggles.
- `SelectionCount.kt` is the compact badge; `ViewerScreen.kt` keeps a single narrow bottom toolbar.
- `MediaVideoPlayer.kt` wraps Media3 (`media3-exoplayer`, `media3-ui`), used from both Gallery and Cloud. Cloud videos first request a temporary original through TDLib; local videos use the content URI.
- `TelegramScreen.kt` is presentation-only around the retained 0.9.2 auth state machine (`TgAuth`): step indicator, paste, inline validation, reveal toggle. It calls the same `saveCredentials/submitPhone/submitCode/submitPassword` entry points and nothing else.

## Telegram boundary (unchanged from 0.9.2)
`telegram/TelegramClient.kt` keeps its parameters, auth flow, key/note format, upload atomics and recovery helpers. The only addition is the opt-in archive/mute helper used by the channel warning. No new credential scheme, no remote destructive call, no cache-limit call into TDLib.

## Network and storage policy
- `data/NetworkPolicy.kt` is the single place that decides whether a cloud preview may be fetched: unmetered always allows, metered follows the user's switch (default off). `cloud/CloudUri.kt`'s `CloudFetcher` returns `null` when blocked, so Coil draws nothing instead of downloading.
- The same file owns the cache ceiling (200/500/1000 MB, default 500) and the clear-cache action.
- `MemoraApplication.newImageLoader()` builds Coil with a bounded memory cache (12 % of RAM) and a disk cache of `NetworkPolicy.imageDiskBytes` (half of the ceiling, leaving the other half to TDLib's own media cache).

## Trust boundaries
- Phone originals are read through MediaStore; backup never deletes them.
- Cloud and phone stay separate libraries; downloading never triggers an upload.
- Backup eligibility remains "type ON and folder ON" for manual, automatic, queued and retried uploads.
- Credentials stay in the existing Keystore-encrypted local store; channel/account identifiers stay masked until a screen-lock confirmation.

## Protected foundation / launch gaps
`tools/check_integrity.py` pins the delivery hashes of 37 protected files (30 unchanged since the retained baseline, 6 authorized changes, 1 new helper). Owner-signed phone tests, live Telegram validation, TDLib cache-ceiling control, target-SDK/16 KiB review and the deferred v1.0 work remain open; see `docs/LAUNCH-AND-AI.md` and `docs/PLAN-v1.0.md`.
