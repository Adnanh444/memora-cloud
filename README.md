# Memora Cloud — Your Memories. Your Cloud.
Open-source Android photo/video backup using **your own** Telegram storage. Developed by Adnan. © 2026 Adnan.

**Status: Phase 1 (Foundation).** Navigation, Material 3 theme (light/dark, English/Bengali), Room schema, About screen, CI APK build. Gallery, Telegram, backup etc. are NOT implemented yet.

## Build (phone-friendly)
1. Push this repo to GitHub as `memora-cloud`.
2. Actions tab -> "Build APK" -> run. Download `memora-debug-apk` from the run's artifacts.

## Roadmap
See `docs/ARCHITECTURE.md`.

## Privacy
See `PRIVACY.md`. No developer server; media goes only to the user's own Telegram.

## Secrets
Never commit API credentials, session data or signing keys. Use GitHub Secrets / local `secrets.properties` (git-ignored).
