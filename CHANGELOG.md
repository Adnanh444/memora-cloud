# Changelog
## 0.9.5 — Photo/video controls, in-app playback and data-friendly cloud previews
- Matching theme-aware gold photo / blue video rings and compact lines; independent persistent Gallery/Cloud visibility controls.
- Compact numeric selection badges and a narrow bottom-only photo toolbar; readable system bars in the black viewer.
- Shared Media3 Gallery/Cloud video player with lifecycle pause, retry/error handling and temporary cloud-original playback. Platform-dependent HEIC/HEIF/MOV/HEVC; no universal codec/transcoding guarantee.
- Channel-deletion warning and explicit confirmed archive/mute convenience; no deletion lock or remote media mutation. 0.9.2 credential/backup/recovery core retained.
- Cloud previews are not downloaded on mobile data unless enabled (default off); opening an item is the only consented transfer. User-set preview cache ceiling (200/500/1000 MB, default 500) with oldest-first eviction and a Clear action.
- Telegram sign-in redesigned (UI only): three-step indicator, paste buttons, inline validation, in-button progress, password reveal. The authentication, storage, backup and recovery logic is untouched.
- No AI in this build: the prepared on-device CLIP/SFace/OCR subsystem, its models, licences and runtime dependency were removed. Photos/videos only; no general-file/audio/document backup, no bin, no FIFO queue, no remote merge.

## 0.9.4 — Dark contrast / 0.9.2 Telegram rollback
- Fixed inherited default-black text in dark mode by supplying theme-aware root content colors; kept light-mode and component/status colors.
- Kept the 0.9.3 interface, coverage/collapse, four-tab highlight, utility layout, metadata search and optional local lock.
- At the user’s explicit request, restored the entire 0.9.2 Telegram package, BackupEngine, RecoveryEngine and their two screens byte-for-byte. This removes the 0.9.3 Telegram hardening/scoping additions; see IMPLEMENTATION.md for limits.
- Removed mandatory CI API credentials/injection; API ID/hash can be entered directly in the app. Original release-signing Secrets are still required.
- Removed three unused rollback-only helpers/tests; preserved previous source ZIPs/branch. No schema/key/data reset, remote merge or old-branch deletion.

## 0.9.3 — Recovery & Polish (test-branch milestone)
- Gallery coverage rings/compact bars and a collapsing backup strip; Cloud totals remain separately scoped.
- Four-tab glass-like selected indicator, small New album header icon, top 2×2 Albums utilities, framed selection/delete actions.
- Scoped metadata-search entry points; visual AI and face search are not implemented.
- Validated configured-app/manual Telegram credentials; bounded recovery, persisted positive batches and explicit metadata warnings.
- Confirmation-held remote copies remain discoverable without blindly resending the held queue entry.
- Optional Android-credential app lock/screenshot protection; verified active-destination display scoping and paused backup on logout.
- Final sandbox checks: 59 JVM tests, 17 SQL/source checks, debug and fully optimized release assembly pass. Owner-key/live-phone/Telegram validation remains pending.
- Full profile isolation, deployed owner approval, AI models and launch compliance remain open; see docs/LAUNCH-AND-AI.md.

## 0.9.0 — Phase 8 (part 1): Organization
- Favorites, Archive and Albums (identified by SHA-256, so they follow your photos to a new phone).
- Free Up Space: removes phone copies only after each one is re-verified with the cloud.
- Favorites/archive/albums are saved in the cloud as one encrypted file and restored with the library.
## 0.8.0 — Phase 7: Recovery
- Restore existing backup: finds your private storage in the signed-in Telegram account and rebuilds the Cloud library from the channel.
- Photos already on the new phone are recognised by SHA-256 and never uploaded again.
- New uploads record their date taken so restored items keep their timeline.
- Rebuild-from-cloud button; limitations explained on screen.
## 0.7.0 — Phase 6: Cloud + Google Photos style
- Cloud tab: browse backed-up photos/videos with previews from the cloud; originals stay off the phone unless you tap Download.
- In-app cloud photo zoom and video playback from a temporary private cache (auto-removed).
- Download saves originals to Pictures/Memora or Movies/Memora; delete from cloud uses a 30-day cloud bin.
- Backup rule fix: photo/video type and folder switches now block manual, automatic, background, queued and retried uploads.
- Clean (Google Photos-like) style is the default; neon style is optional. Cloud connection details are hidden behind the screen lock.
## 0.6.0 — Phase 5: Duplicates + Pause + better visibility
- Pause/Resume button on the Photos backup strip, Backup screen and notification; Pause stops the current upload immediately.
- Duplicate scanner (SHA-256, size pre-filter, cached hashes); you choose what to delete; at least one copy is always kept.
- Details show 'Already backed up' for duplicates of cloud files.
- Higher-contrast dark and light themes, solid panels, larger text.
## 0.5.1 — Fix: upload for current TDLib
## 0.5.0 — Phase 4: Backup engine
- Manual and automatic backup to the private Telegram channel (originals uploaded as documents, no compression).
- Persistent queue, retry, pause/resume, Wi-Fi/charging rules, new-media detection, progress notification.
- SHA-256 hashing while copying; identical files already backed up are skipped.
- Upload verified with Telegram before a file counts as backed up; live strip, thumbnail badges and progress.
## 0.4.2 — Permanent signing key support (updates install over the old app)
## 0.4.1 — Each user enters their own Telegram API key (stored encrypted on the phone)
## 0.4.0 — Phase 3: Telegram connection
- Telegram login (phone, code, two-step password) via TDLib; encrypted session; private channel creation and ownership/ID verification; Telegram storage screen.
## 0.3.0 — Google Photos-style experience
- Fast-scroll handle with month bubble; pull-down to refresh.
- Viewer: pinch + double-tap zoom, tap to hide controls, bottom action bar, swipe up for details
  (photo shrinks up, panel slides in), swipe down to close; EXIF camera info.
- Modern bottom sheets (delete shows From phone / From cloud / From both; settings choices).
- Library tab: device folders with cover photos, folder screens, Bin (phone trash, 30 days, restore / delete forever).
- Search (offline keyword search over name, folder, type, date).
- Backup strip + thumbnail status badges + upload-progress UI (shows real state; backup engine arrives in Phase 4).
## 0.2.2 — Share and Delete
## 0.2.1 — Futuristic UI + smaller APK
## 0.2.0 — Local Gallery
## 0.1.0 — Foundation
