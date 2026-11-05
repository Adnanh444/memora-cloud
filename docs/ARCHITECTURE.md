# Architecture and revision boundaries

## Completed baseline

Phases **1–7 are completed** in the 0.9.0 baseline used here: foundation, local gallery, Telegram integration, backup, deduplication, cloud library and recovery. The previous Phase-1-only README/architecture wording was obsolete. The 0.9.2 work is a focused revision of that functioning app, not a rebuild of those phases.

One Gradle app module, Kotlin, Jetpack Compose/Material 3, Room, WorkManager and TDLib. Main source packages:

- `gallery`: MediaStore reads, media models, gallery state and native-favorite flags.
- `backup`: persistent backup ledger/hash cache, queue, foreground workers, recovery and temporary upload workspaces.
- `telegram`: TDLib client, login/destination handling and Telegram-backed file operations.
- `cloud`: thumbnail/full-image addressing, fetch and download support.
- `org`: existing SHA-based archive/album metadata; historical favorite records remain preserved here.
- `ui`: navigation, screens, previews, density gestures and theme.
- `data`: existing preferences/application models.

## 0.9.2 state and persistence

- Appearance: `memora_clear_appearance` SharedPreferences, `dark` boolean, default light.
- Grid density: `memora_grid_density` SharedPreferences, separate `gallery`, `cloud`, `albums`, `device-bin`, `cloud-bin` keys, default 2. UI clamps by available width without persisting a rotation-only clamp.
- Favorites: Android 11+ MediaStore flag plus Android consent; not Google account/private gallery API access. Organization schema/metadata remains intact for archives/albums and old favorite records.
- Upload safety: one backup-run gate, isolated attempt directories, bounded send/idle/overall/verification waits. `CONFIRMING` extends the existing text state without a Room schema change. It is excluded from automatic pending/retry queries. Positive confirmation or an explicit owner-approved resend releases a held transfer.

## Scope protections

The delivery's backend inventory records the eight intentionally changed existing non-UI files and the three new upload helpers. Other backend files, MediaActions, the manifest, ProGuard rules and original import/signing-key workflows are compared byte-for-byte to the baseline. It is **not** a claim that every backend file is unchanged: native favorites and upload fixes were explicitly authorized.

No storage/recovery/album rewrite, Telegram protocol replacement, new developer server, special media-management permission, or main-branch cleanup is part of this revision. Telegram storage availability and account/file limits still apply.

See `IMPLEMENTATION.md` for build, consent, uncertainty recovery and phone-acceptance details; see `TEST-REPORT.md` for what has actually been tested.
