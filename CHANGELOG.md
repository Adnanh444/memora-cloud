# Changelog
## 0.5.0 — Phase 4: Backup engine
- Select photos and tap the cloud icon to back them up to your Telegram channel (originals, uncompressed).
- Persistent upload queue with retry (5 tries, growing delay) and resume; background upload with a notification.
- Automatic backup per folder (cloud-sync icon on the folder screen); new photos are picked up about every 30 minutes.
- Wi-Fi only switch (on by default). Backup strip, thumbnail badges and a Retry button show the real state.
- A photo counts as backed up only after Telegram confirms it and the size is read back and matches.
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
