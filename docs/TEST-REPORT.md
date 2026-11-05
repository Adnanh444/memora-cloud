# 0.9.4 Dark Contrast / 0.9.2 Telegram — verification report

**Date:** 5 November 2026. **Version:** 0.9.4 / code 18. **Local branch:** `work/dark-contrast`, based on preserved local 0.9.3 snapshot `223fdcf`. The user explicitly selected 0.9.3 interface + dark readability fixes + **0.9.2 Telegram login/backup/recovery**.

## Final result

**44 JVM tests, 17 SQL/source contracts, 5 static contrast tests, debug assembly, release compilation/vital lint, R8 and full optimized release packaging PASS.** The exact latest source was built without changes during verification. No live phone, Android rendering/instrumentation, owner-key GitHub run or Telegram account was tested. This is not launch approval.

## Executed checks

| Check | Actual result |
|---|---|
| `testDebugUnitTest` | **44 tests; 0 failures/errors/skips; 7 suites** |
| Grid density / local-favorite URI rules | 10 passed |
| Bin selection rules | 7 passed |
| Upload serialization/cancellation | 4 passed |
| Upload workspace/safe filenames/cleanup | 4 passed |
| Upload acknowledgment/progress/deadline/cancellation | 5 passed |
| Coverage/count/collapse rules | 10 passed |
| Local app-lock timing policy | 4 passed |
| `python3 tools/verify_contracts.py` | **17 checks passed** |
| `python3 tools/verify_dark_contrast.py` | **5 checks passed**; includes 38 explicit palette pairs >=4.5:1 |
| `compileReleaseKotlin`, `lintVitalRelease` | **PASS** |
| `assembleDebug` | **PASS** |
| `minifyReleaseWithR8` | **PASS** |
| Fresh `assembleRelease` / optimized APK packaging | **PASS** |
| Both APK ZIP CRC, signature, identity/SDK/ABI, font license | **PASS**, sandbox certificate only |
| Manual setup BuildConfig | **PASS:** API ID 0 / hash empty in both variants, intentionally |
| 0.9.2 Telegram package + engines/screens | **9 files byte-identical** to the delivered baseline |
| Protected-file inventory | **36 files:** 33 unchanged / 1 authorized MainActivity change / 2 retained UI-policy helpers |
| Original signingConfigs / release signing selection | **BYTE-IDENTICAL** to 0.9.2 |
| Local CI guard simulations | **3 passed**, Gradle command stubbed; real owner key not tested |
| Artifact name/compression/CRC/checksum configuration | Checked locally; owner download not tested |
| `git diff --check` | **PASS** |

The 0.9.3-only credential/history/destination helper tests are deliberately removed with those unused helpers. The count drops from 59 to 44 because the user requested the rollback; the removed 0.9.3 behavior is not claimed present or tested. Original 0.9.2 tests (30), coverage (10), and app-lock timing (4) remain.

SQL tests run production DAO query text against in-memory SQLite; source contracts include exact baseline file hashes, manual setup routing, API-free CI/signing guards and UI wiring. They are not Android Room/network/consent instrumentation. Static contrast tests verify source wiring and explicit RGB palette pairs, not every actual rendered screen, overlay/alpha, disabled control or OEM accessibility setting. Vital lint is not the full lint suite.

## Build method

JDK 17, SDK/build-tools 34, Gradle 8.9; four sequential successful invocations to bound sandbox memory:

1. `testDebugUnitTest compileReleaseKotlin lintVitalRelease`: heap 700 MiB / metaspace 600 MiB / code cache 64 MiB.
2. `assembleDebug`: heap 650 MiB / metaspace 256 MiB / code cache 48 MiB.
3. `minifyReleaseWithR8`: heap 1100 MiB / metaspace 220 MiB / code cache 32 MiB / ActiveProcessorCount=1.
4. `assembleRelease`: heap 650 MiB / metaspace 256 MiB / code cache 48 MiB / ActiveProcessorCount=1.

All use SerialGC, UTF-8, `--no-daemon --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process`; JVM settings are supplied through `-Dorg.gradle.jvmargs`. All four stages passed for this revision. Compiler output has deprecated Android credential API warnings; no specific phone/OEM compatibility guarantee follows from compilation.

## APK measurements — evidence, not owner update APKs

Package `com.adnan.memora`, minimum SDK 26 / target SDK 34 / arm64-v8a only / version 0.9.4 code 18.

| Variant | Bytes | MiB | SHA-256 |
|---|---:|---:|---|
| Debug | 41,775,436 | 39.840 | `0475ad6fe55fcd4c3ad981e37c32b8766966bf1cc6937926151092a6e6adc62a` |
| Optimized release | 25,397,299 | 24.221 | `ea07fbd28157f8145a99027b3ee664ae9036cac84ba7694879637d6ad46d4296` |

Both APKs pass ZIP/signature verification and retain the font license, without MANAGE_MEDIA or MANAGE_EXTERNAL_STORAGE. Release is not marked debuggable but **uses the sandbox debug signing certificate**. The source package does not offer these as installable updates to the owner-signed app. Owner-key builds have different hashes and may differ in size. No material production APK-size reduction is claimed; source ZIP size is unrelated to APK size.

## Preserved / deliberately rolled back

The entire Telegram package, BackupEngine, RecoveryEngine, TelegramScreen and RecoveryScreen match delivered 0.9.2 ZIP SHA-256 `60e6e52d42249fcb66d762ced9c42510ba9da396f0eb45d03b2f1ba05a8d6398`. Hashes are in `verification/telegram-0.9.2-baseline.json`; every contract run checks all 9. The scoped inventory also retains Room schema 2, MediaActions, original permissions/ProGuard/importer/key-generation workflows, metadata/organization and download interfaces. MainActivity retains 0.9.3 local app-lock integration; CoverageMath and AppLockPolicy remain the two new inventory helpers.

The user-requested rollback removes 0.9.3 strict/bounded history/recovery operations, shared recovery gate, positive-page persistence, metadata-warning state, held-positive-ID rediscovery correction, automatic logout pause, auth-close thumbnail cleanup and active-destination backend scoping. **These are not silently claimed retained.** Legacy recovery can misreport incomplete scans/metadata and skip a held-positive remote item; exact baseline equivalence is not a correctness proof. Pause uploads before recovery/logout, keep independent original copies, and avoid account/destination switching with valuable pending queues.

The workflow change is deliberate: API Secrets are neither required nor injected; direct in-app credentials are used. Original signer-file guard and signing Secrets remain. Existing saved manual credentials/session data are not cleared. Installations relying solely on previously bundled API credentials will need one-time in-app API setup in this empty-API build. A replacement installation always needs API credentials before Telegram authentication; no Telegram credential-fetching bootstrap is promised.

## Required owner/device checks

Follow [IMPLEMENTATION.md](../IMPLEMENTATION.md): install as an update with the original signer; check dark/light text across all screens/sheets/forms, large text/rotation/gestures, retained preferences/data, native favorites across OEM galleries, bin preview/consent, app-lock authentication/grace/screenshots, real in-app login, upload/network loss/held confirmations, downloaded originals and recovery on a spare installation. Inspect album/archive metadata separately; do not trust legacy Done as a complete integrity audit. Do not bulk Free up space valuable originals; local hash freshness and full remote hash verification remain audit items.

AI/face search, deployed owner approval, complete profile isolation and launch compliance are still pending. See [LAUNCH-AND-AI.md](LAUNCH-AND-AI.md). No remote push, main merge, old-branch deletion, replacement signing key or destructive cleanup of previous deliveries was performed.

## Evidence

`verification/TESTS-AND-RELEASE-SOURCE.txt`, `DEBUG-BUILD.txt`, `RELEASE-R8.txt`, `RELEASE-BUILD.txt`; 7 JUnit XML suites / `unit-summary.json`; `contract-tests.txt`; `dark-contrast-checks.txt`; exact `APK-VERIFICATION.txt` / `apk-summary.json`; `telegram-0.9.2-baseline.json`; `backend-inventory.csv`, `integrity-summary.txt`, and local-only `ci-guard-checks.txt`.
