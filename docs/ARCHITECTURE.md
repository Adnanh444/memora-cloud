# 0.9.4 architecture and scope

Phases 1–7 remain the existing foundation. The user selected 0.9.3 UI plus dark-mode text fixes, but **0.9.2 Telegram login/backup/recovery**.

## UI and contrast
- `gallery/CoverageMath` / `ui/screens/CoveragePanel`: readable-local denominator, VERIFIED ledger keys, animated rings/compact bars and non-consuming nested scroll.
- `ui/MemoraApp`: four translucent selected tabs, scoped metadata search and explicit theme foreground on the transparent Scaffold.
- `ui/theme/Theme`: LocalContentColor provided inside MaterialTheme, preventing plain text/icons from inheriting default black in dark mode. Existing component foreground roles and Day/Night palettes retained.
- `ui/AppLockGate` / `data/AppLockPolicy`: optional Android credential modal, 30-second background grace and secure-window flags. Separate from launch authorization; no custom app PIN.

## Legacy Telegram boundary
The entire Telegram package, BackupEngine, RecoveryEngine, TelegramScreen and RecoveryScreen are byte-identical to delivered 0.9.2. Direct in-app API setup uses existing encrypted local storage and saved sessions. Local/custom build credentials remain an optional fallback; normal CI does not inject them or require their Secrets. Original signing Secrets/selection are preserved.

0.9.3 CredentialPolicy, HistoryScanRules and DestinationScope and their tests are no longer wired and have been removed from this branch. The original 0.9.3 branch/ZIP retains them. Strict scans, shared recovery operation lock, positive-page persistence, metadata warning state, held-ID rediscovery correction, automatic logout pause and backend destination scoping are therefore absent. This deliberate rollback is not a claim of improved recovery security.

## Unchanged foundation
Room schema 2, SHA cache, upload run gate/workspace/acknowledgment rules, CONFIRMING queue behavior, WorkManager scheduling, storage/key notes, metadata/album/archive formats, native favorites, bins, Cloud download addressing, permissions, signing configuration and existing importer/key-generation workflows remain. No new credential/media server, recovery-key scheme, destructive remote operation or data reset.

## Boundaries
Coverage reflects the legacy installation ledger and is not proof of account/destination isolation or download-and-rehash integrity. Use one account/destination and pause manually before logout/recovery. See IMPLEMENTATION.md for recovery caveats. AI/face models, deployed approval and production security/profile isolation remain open in LAUNCH-AND-AI.md. Tests/static contrast math do not replace device/Telegram tests.
