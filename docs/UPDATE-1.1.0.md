# 1.1.0 — what is in this build

Version **1.1.0 (code 21)**, an update on top of 0.9.5. Photos and videos only. **No AI.** The Telegram core (0.9.2), interface (0.9.3), dark contrast (0.9.4) and media controls (0.9.5) are all retained.

## The two upload fixes

**1. The page no longer jumps while uploading.** The grid keeps its scroll position (`rememberSaveable` + `LazyGridState.Saver`, per grid), so a badge or counter changing no longer sends you back to the top of the list.

**2. Pause never leaves a broken file.**
- **Pause is now graceful:** the file being sent at that moment is allowed to **finish**, then the queue stops. Nothing is cut in half, so nothing corrupt exists on the server or in the cache.
- **Immediate stop still exists** (`pauseNow`) for the case where waiting is not acceptable. The cancelled file goes back to the queue, its temporary copy is deleted, and the entry is not marked as uploaded.
- **Leftovers are cleaned:** every run starts by deleting temporary copies from `cacheDir/upload`, so a killed app cannot leave junk behind.
- **Interrupted uploads are repaired, not duplicated:** when the app next checks unconfirmed uploads, a file with no complete copy in the channel has its half-arrived message **deleted**, and is then put back in the queue to upload again from the beginning.

## Already in this line (unchanged here)

3. **Delete with no Allow / Deny sheet** — the file is moved into Memora's own bin through plain file access (needs All files access, granted once in Settings). No Android dialog, ever.
4. **Memora bin** — 30-day retention, restore to the original path, delete forever, empty bin, automatic purge.
5. **Compact floating nav bar** — four tabs in a centred capsule with small icons.
6. **All / Photos / Videos pill** — floating at the bottom centre of Gallery, Cloud and Search.
7. **Long-press + drag to select** — cells are selected in order as the finger crosses them; if the pressed cell was already selected, the drag deselects in order.

## Build / package

Same shape as 0.9.3–0.9.5: the ZIP contains one `memora-cloud/` folder, and `.github/workflows/` holds `build.yml` (builds and signs the APK, artifact `memora-apk-1.1.0`) and `unzip.yml` (optional: unpacks the ZIP inside the repo).

## Not verified
- Nothing was run on a phone here: the pause-during-upload behaviour, the drag feel, the pill position and the bin flow all need your test.
- The minified release APK cannot be produced in this 2 GB sandbox (R8 is killed for memory); the GitHub runner builds it.
- Scrolling the grid by dragging a selection near the screen edge does not auto-scroll yet.
