# 0.9.6 — what is in this build

Version **0.9.6 (code 20)**, an update on top of your 0.9.5 install. Photos and videos only. **No AI.** Everything that was in 0.9.5 — Telegram core (0.9.2), interface (0.9.3), dark contrast (0.9.4), media controls and data-friendly cloud previews (0.9.5) — is untouched.

## Added (three things you asked for)

**1. Delete no longer shows Allow / Deny.** Android's confirmation belongs to its own delete API, so the app stopped using it. A delete now moves the file into **Memora's own bin** and the grid updates instantly. This needs **All files access**, granted once from Settings → *Silent delete (All files access)*; the app explains the permission there and in a one-time dialog in plain words. Without it nothing is deleted and no Android dialog appears — the app just offers the setting. Nothing else in the app reads files through that permission.

**2. Memora bin (new).** Settings → *Memora bin* shows what was deleted: thumbnails, size, days left. Restore puts a file back at its exact original path; *Delete forever* removes one item; *Empty bin* clears all. Anything older than 30 days is purged automatically (on app start and when the bin is opened).

**3. All / Photos / Videos pill.** A small three-icon pill floats at the bottom centre of the Gallery, the Cloud grid and Search results. It is tinted with the same gold (photos) and blue (videos) accents as the counters, hides while selecting, and does not fetch anything by itself.

**4. Compact floating nav bar.** The four tabs sit in a centred floating capsule with small icons instead of a full-width bar.

**5. Long-press and drag to select.** Press and hold a photo, keep the finger down and drag: every cell the finger crosses is selected in order. If the cell you pressed was already selected, the same drag deselects them in order. Releasing ends the run. Scrolling, tapping to open, and the selection toolbar behave as before.

## Not in this build
- Cloud bin / cloud delete behaviour is unchanged from 0.9.5.
- Auto-scroll while dragging near the top or bottom edge is **not** implemented; on a long list, start the drag where you want it to run.
- No cloud albums, no ZIP tools — those are not part of 0.9.5 and are not being re-introduced without your word.

## Installing it

Push the files to a branch, then **Actions → Build APK**. The run produces the artifact **`memora-apk-0.9.6`** (`app-release.apk` + `app-release.apk.sha256`). Install it over 0.9.5 — the version code is 20, so Android accepts it as an update and your data stays.
