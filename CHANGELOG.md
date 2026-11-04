# Changelog
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
