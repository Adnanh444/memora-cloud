# 0.9.2 Clear Gallery — verification report

**Date:** 5 November 2026. **Baseline:** `596acfbfc2a0b8f2b2f27272373d89849b19279b`. **Version:** 0.9.2 / code 16. **Local branch:** `work/clear-gallery`.

## Result at delivery

**Source/unit checks and the debug build pass. The full optimized release is NOT yet verified.** Release Kotlin compilation and release vital lint passed, but two full-release attempts lost the Gradle daemon during R8 optimization. Resource pressure in this roughly 2 GB sandbox is suspected; the logs do not prove a specific compiler defect or memory cause. Do not interpret this report as a passing `assembleRelease` or as a phone test.

The owner-signed GitHub release build and phone acceptance checklist in `IMPLEMENTATION.md` are required before main integration. No owner signing key, Telegram API credentials, Telegram session or connected phone was supplied for sandbox testing.

## Executed automated checks

| Check | Actual result |
|---|---|
| Latest-source `testDebugUnitTest` | **PASS: 30 tests, 0 failures/errors/skips** |
| Grid density and local-favorite URI rule | 10 tests passed |
| Bin selection rules | 7 tests passed |
| Upload run serialization/cancellation | 4 tests passed |
| Isolated upload workspace/safe filenames/cleanup | 4 tests passed |
| Bounded upload acknowledgment wait/progress/deadline/cancellation | 5 tests passed |
| Production Room SQL + source contracts, `python3 tools/verify_contracts.py` | **PASS: 9 checks** |
| Latest-source `assembleDebug` | **PASS** |
| APK ZIP CRC, identity/SDK/ABI, v2 signature | **PASS**, sandbox debug signer only |
| `compileReleaseKotlin` | **PASS** |
| `lintVitalRelease` | **PASS** during release attempts |
| Full `assembleRelease` / R8 / release package | **NOT COMPLETED: daemon terminated** |
| Backend/protected-file inventory | **PASS: 34 files** — 23 unchanged, 8 authorized changes, 3 new upload helpers |
| Gradle signing block/selection comparison | **PASS: existing signing configuration retained** |
| CI guard shell simulations | **PASS: 3 cases**, build command stubbed; not a real GitHub run |
| `git diff --check` | **PASS** |

The 9 Python checks execute the production DAO query strings against an in-memory SQLite database and inspect a few source contracts. They are **not** Android Room instrumentation, Android consent tests, actual Telegram transfers, or proof of cross-gallery interoperability.

## APK measurement

The locally verified **debug** APK is **41,759,697 bytes (39.825 MiB)**, package `com.adnan.memora`, version 0.9.2/code 16, minimum SDK 26, target SDK 34, `arm64-v8a` only.

SHA-256: `24d37a753a16e7c2616625269880251fe43058b071a6f2f8cee9974847bfe980`.

This APK has the sandbox debug key and unset owner Telegram API credentials. It is not the user's installable production update and is intentionally not included as an update APK. A release APK size is **not available** because release assembly did not finish; no baseline-to-release size reduction is claimed. Source ZIP size is not APK size.

## Integrity boundaries

`verification/backend-inventory.csv` records baseline/current SHA-256 hashes:

- **23 byte-identical protected files**, including unaffected backend modules, organization metadata, Cloud downloader/addressing, MediaActions, manifest, ProGuard and original import/key-generation workflows.
- **8 explicitly authorized changed existing files:** BackupDb, BackupEngine, BackupWorker, BackupUi, GalleryViewModel, LocalMedia, MediaRepository and TelegramClient.
- **3 new upload helpers:** UploadRunGate, UploadWorkspace and UploadAwaiter.

The Gradle signing configuration block and release signing selection were compared to the baseline and remain unchanged. The build workflow now supplies API Secrets and refuses missing existing release signing material. Organization database/schema, archive/album metadata behavior and the unrelated storage/recovery/download implementation were not rewritten.

The new CONFIRMING state is excluded from ordinary pending/retry/reset behavior by the tested SQL. No Room table schema/version change is introduced. Remote SHA-caption/file-size matching only marks positive confirmations; it does not prove absence after a failed/partial query or download-and-rehash every cloud file.

## Not executed — must be checked on the phone/CI

- Google Photos/Infinix AI Gallery publishing and reading the same MediaStore favorite flag; account-cloud/private-gallery favorites remain outside the supported API.
- Android favorite consent acceptance/cancellation, Android 8–10 unsupported behavior, limited-media grants and OEM provider quirks.
- Real multi-touch, scroll/long-press coexistence, restart persistence, rotation, large fonts, accessibility and light/dark visual clarity on a device.
- Trashed-media preview access, video playback, native restore/delete consent and destructive bin actions.
- Actual Telegram send/verification, interrupted/offline transfer, late acknowledgment, worker/process death, destination constraints, pause/resume and recovery/reindex against a real account.
- Main-branch integration, production-key signature compatibility, the owner-signed optimized release and release-size comparison.

No emulator/device or `/dev/kvm` was available. Existing unit results from the prior Ocean delivery are **not** substituted for these revision results.

## Evidence and repeatability

- `verification/DEBUG-BUILD.txt`: successful latest-source debug tests/build log.
- `verification/unit-tests/TEST-*.xml` and `unit-summary.json`: all 30 JUnit cases.
- `verification/contract-tests.txt`: 9 executed SQL/source checks.
- `verification/APK-VERIFICATION.txt`: exact debug identity, size, hash, ABI and signature result.
- `verification/RELEASE-ATTEMPTS.txt`: release limitations and actual failure tails.
- `verification/backend-inventory.csv`, `integrity-summary.txt`: scoped baseline audit.
- `verification/ci-guard-checks.txt`: local guard simulations only.

Run `./gradlew testDebugUnitTest assembleDebug`, `python3 tools/verify_contracts.py`, and `python3 tools/check_integrity.py` to repeat the checks. See the implementation guide for release credentials and the mandatory phone checklist. No remote writes, main merge or destructive cleanup were performed.
