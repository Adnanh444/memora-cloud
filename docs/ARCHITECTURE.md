# Architecture & Key Decisions
Single Gradle module with one package per feature (`gallery`, `scanner`, `backup`, `telegram`, `dedupe`, `download`, `recovery`, `faces`, `albums`, `settings`, `update`); split into Gradle modules when each stabilizes.

## Telegram decisions (to settle in Phase 3)
- TDLib requires an `api_id`/`api_hash`. These cannot be truly secret inside an APK. Options: (a) user enters their own from my.telegram.org (most compliant, recommended), (b) developer-registered pair injected via CI secret (extractable; shared rate-limit/ban risk).
- Telegram file limit: 2 GB (4 GB Premium). Large videos need a documented policy.
- Recovery: mark the channel with a Memora ID in its description + pinned encrypted index message; optional copy in Saved Messages.
- Telegram's ToS on bulk storage is a gray area; the README must not promise unlimited/permanent storage.

## Phases
1 Foundation (this) · 2 Local gallery · 3 Telegram · 4 Backup engine · 5 Dedupe · 6 Cloud library · 7 Recovery · 8 Advanced · 9 Updates · 10 Testing/release
