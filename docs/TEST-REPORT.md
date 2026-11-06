# 0.9.6 — silent delete, Memora bin, filter pill, drag select

**Date:** 5 November 2026. **Version:** 0.9.6 / code 20. Update on top of 0.9.5 (which keeps 0.9.2 Telegram core, 0.9.3 interface, 0.9.4 contrast, 0.9.5 media controls). Photos and videos only. **No AI.**

## Result

**44 JVM unit cases / 7 suites, 29 source contracts, 6 contrast tests, 40 protected-inventory checks, debug Kotlin + resource build, debug APK: PASS** — 0 failures, 0 errors, 0 skipped. `compileReleaseKotlin` and `lintVitalAnalyzeRelease` also completed; **the R8/minified release APK was NOT produced in this sandbox** (details below).

**NOT A PHONE TEST.** No device ran this build. The drag gesture, the All-files-access flow and the floating pill layout are unverified by eye or finger.

## APK built here

| Variant | Bytes | MiB | SHA-256 |
|---|---:|---:|---|
| Debug | 44,853,036 | 42.775 | `5c07aa2034ce3f887ac53f0d0cf745d169619e0805611de3371eb37c986a54ff` |

The release APK is produced by `.github/workflows/build.yml` on the GitHub runner (JDK 17 + Gradle 8.9, R8 on a machine with enough RAM). It is **not** included here.

**Why no release APK in the sandbox:** this container has 2 GB RAM. `minifyReleaseWithR8` failed with *"Compilation failed to complete"* and, on a retry with a 1050 MB heap, the Gradle daemon was killed outright. The Kotlin release compile and the vital lint both pass, so this is a sandbox memory limit, not a source problem — but I am not going to claim a release build I did not produce.

## Checks

| Check | Result |
|---|---|
| JVM unit tests | 44 cases, 7 suites, 0 failures / 0 errors / 0 skips |
| `tools/verify_contracts.py` | 29 PASS — adds "delete goes through Memora bin without a system dialog", "filter pill shared by gallery/cloud/search", "long-press drag selects and deselects serially" |
| `tools/verify_dark_contrast.py` | 6 PASS |
| `tools/check_integrity.py` | 40 PASS |
| `compileDebugKotlin` / `assembleDebug` | PASS |
| `compileReleaseKotlin` / `lintVitalAnalyzeRelease` | PASS |
| `minifyReleaseWithR8` | **FAILED — sandbox RAM** (not a compile error) |

## What is new

1. **Delete without Android's Allow / Deny.** `rememberDeleteAction` no longer calls `createTrashRequest` / `createDeleteRequest` / `RecoverableSecurityException`. It copies the file into Memora's private bin and removes the original through plain file access, which needs `MANAGE_EXTERNAL_STORAGE`. Without the permission the app deletes nothing and explains the setting (its own dialog, not the system one).
2. **Memora bin** (`data/MediaBin.kt`, `ui/screens/MemoraBinScreen.kt`): 30-day retention, restore to the original path, delete forever, empty, purge on start.
3. **All / Photos / Videos pill** (`ui/screens/MediaFilterPill.kt`) floating at the bottom centre of Gallery, Cloud and Search; state shared through `GalleryViewModel.mediaFilter`.
4. **Centred compact nav bar** — four tabs in a floating capsule, small icons.
5. **Long-press + drag select** in `MediaGrid`: `detectDragGesturesAfterLongPress` hit-tests `LazyGridState.layoutInfo`, so day headers and gaps are handled; the first cell decides select-vs-deselect and the drag continues in that direction.

## Not verified / deliberately not done

- No device run: permission grant, silent delete of a real photo, restore to the original path, 30-day purge, drag feel, pill overlap with the nav bar.
- Auto-scroll while dragging near a screen edge is not implemented.
- Deleting from the **Cloud** tab still uses the 0.9.5 cloud path (a cloud delete is not a phone-file delete).
- Cloud albums, video posters, ZIP export/import from the removed 0.9.7 line are **not** in this build and were not reintroduced.
