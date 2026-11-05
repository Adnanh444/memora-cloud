# 0.9.5 media update — verification report

**Date:** 5 November 2026. **Version:** 0.9.5 / code 19. Photos/videos only. 0.9.2 Telegram core retained, 0.9.3 interface + 0.9.4 contrast retained. **No AI in this build.**

## Result

**44 JVM unit cases over 7 suites, 26 source contracts, 6 contrast tests, 37 protected-inventory checks, release Kotlin compile + vital lint, debug APK, R8, optimized release APK: PASS** in this sandbox. No failures/errors/skips in the unit run.

**NOT A PUBLIC-LAUNCH APPROVAL.** No real phone, owner signer, live Telegram account, iPhone media sample, codec matrix or security audit was exercised here. Both APKs are sandbox-signed and are not offered as your update.

## APK

| Variant | Bytes | MiB | SHA-256 |
|---|---:|---:|---|
| Debug | 44,799,864 | 42.724 | `18a38626de7b637153442750be388db15dba39a30b9fe4cb1d076d1656a83ff6` |
| Release | 26,127,280 | 24.917 | `3345156c520964e3d30cf8e3e292c7641b32ce92cce0f04b5da9ea2cad0117a9` |

Release APK: minSdk 26, targetSdk 34, arm64-v8a only, not debuggable, sandbox debug signer `cdc7eb192589a363…`. Previous 0.9.4 release: 25,397,299 bytes. Difference: **+729,981 bytes**. Declared permissions: android.permission.INTERNET, android.permission.READ_MEDIA_IMAGES, android.permission.READ_MEDIA_VIDEO, android.permission.READ_MEDIA_VISUAL_USER_SELECTED, android.permission.READ_EXTERNAL_STORAGE (maxSdk 32), android.permission.WRITE_EXTERNAL_STORAGE (maxSdk 28), android.permission.POST_NOTIFICATIONS, android.permission.FOREGROUND_SERVICE, android.permission.FOREGROUND_SERVICE_DATA_SYNC, android.permission.WAKE_LOCK, android.permission.ACCESS_NETWORK_STATE, android.permission.RECEIVE_BOOT_COMPLETED, com.adnan.memora.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION — unchanged by this update. Native libraries: lib/arm64-v8a/libandroidx.graphics.path.so, lib/arm64-v8a/libtdjni.so.

The AI build measured during this cycle was 45,025,992 bytes; removing the ONNX runtime, ML Kit recognition/face modules and the AI code brings the app back to this size.

## Checks

| Check | Result |
|---|---|
| JVM unit tests | **44** cases, 7 suites, 0 failures/errors/skips |
| `tools/verify_contracts.py` | 26 PASS — includes "no AI runtime/models/OCR", "cloud previews gated by connection policy", "caches bounded and clearable", "sign-in redesign keeps the backend untouched" |
| `tools/verify_dark_contrast.py` | 6 PASS — 42 palette pairs ≥ 4.5:1 including the gold/blue counter labels |
| `tools/check_integrity.py` | 37 PASS — protected delivery inventory |
| Android release compile / vital lint | PASS |
| Debug + optimized release packaging | PASS |

## Native alignment (inspection only)

- `lib/arm64-v8a/libandroidx.graphics.path.so`: PT_LOAD [16384, 16384, 16384], 16 KiB minimum: yes
- `lib/arm64-v8a/libtdjni.so`: PT_LOAD [16384, 16384, 16384], 16 KiB minimum: yes

## Still unverified

- No phone, emulator, owner-signed install, real Telegram login/backup/recovery, or iPhone-codec test.
- Metered-preview policy, cache ceiling and eviction behaviour are implemented and compiled, not measured on a device.
- Telegram's own media cache has no explicit ceiling in this build; TDLib's storage optimizer decides.
- The sign-in redesign is presentation-only; real-world error strings from Telegram are shown as-is.
- Retained 0.9.2 core still needs the account/session/isolation audit; no owner-approval service is deployed.
- Deferred on purpose: AI/people/smart search, smart delete + bin, FIFO queue, theme overhaul, cloud tools.
