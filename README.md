# Memora Cloud — 0.9.4 Dark Contrast / 0.9.2 Telegram

**Test-branch update, not public-launch approval.** Keeps the 0.9.3 interface and optional local app lock, fixes inherited dark-mode text, and restores the delivered **0.9.2 Telegram login/backup/recovery implementation** at the user's explicit request. Completed Phases 1–7 are not being reimplemented.

## This update
- Theme-aware default text/icon content color at the theme root and transparent main scaffold. Plain labels no longer inherit default black in dark mode. Component button/error/disabled colors remain theme-controlled.
- Entire Telegram package plus BackupEngine, RecoveryEngine, TelegramScreen and RecoveryScreen match the delivered 0.9.2 source byte-for-byte.
- CI builds the in-app-credential variant without TELEGRAM_API_ID / TELEGRAM_API_HASH Secrets. Existing **signing-key Secrets remain required**. No API credentials, session or signing keys are committed.
- Version 0.9.4 / code 18; build artifact `memora-apk-0.9.4`.

## Kept from the 0.9.3 interface
Gallery photo/video coverage rings and compact bars, collapsing backup strip, four glass-like selected tabs, small New album header icon, top 2×2 Albums utilities, selection/delete glyphs, scoped metadata-search entry points and optional Android-credential app lock. Cloud totals are separate from readable phone totals. Coverage uses the legacy installation's verified backup ledger, not proof of full account/destination isolation.

## Telegram setup / restore
Open Cloud → Connect (or Settings → Telegram storage). On an unconfigured installation, enter API ID/hash directly in the app, then your own Telegram phone/code/two-step password. Credentials are encrypted locally, as in 0.9.2. On a new phone/reinstall, supply API credentials again before Telegram authentication; they cannot be fetched from Telegram before login. Keep a secure independent record of them.

Pause backup before logging out. Use one account/destination per installation; do not switch valuable pending queues between accounts. The requested rollback removes 0.9.3 recovery hardening and backend display scoping, not the original uncertainty-held upload safeguards. Read the guide for exact limits.

## Still pending
Visual AI/face search, deployed owner approval/revocation, full profile isolation, live Telegram/phone validation and launch compliance. Metadata search is not visual AI; local app lock is not launch authorization.

Read [IMPLEMENTATION.md](IMPLEMENTATION.md), [test evidence](docs/TEST-REPORT.md) and [remaining launch work](docs/LAUNCH-AND-AI.md). Keep the original signing identity; never uninstall to bypass a mismatch. No remote push, main merge, branch deletion or old-delivery deletion is performed by this package.
