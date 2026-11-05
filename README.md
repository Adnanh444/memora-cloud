# Memora Cloud
Android photo/video backup using the owner's Telegram storage. Developed by Adnan.

## 0.9.2 — Clear Gallery
**Phases 1–7 are already completed.** This revision retains that baseline and focuses on a gallery-first interface, readable light/dark themes, remembered pinch-grid sizes, Android phone favorites, compact bins, and upload reliability.

- Four tabs: **Gallery · Cloud · Albums · Settings**. No Home dashboard.
- Top sun/moon button; no theme/style page in Settings.
- Android 11+ phone favorites, with Android consent. Legacy Memora favorite records are preserved, not silently converted or deleted.
- Cloud-only photo/video/total statistics; upload progress is separate.
- Duplicate scanner and both bins under **Albums → Utilities**.
- Preparation / sending / confirmation upload stages. Uncertain transfers are held instead of blindly resent.

**Start here: [IMPLEMENTATION.md](IMPLEMENTATION.md)** for safe test-branch import, signing/Secrets, build instructions, phone checks, and the approval-before-main procedure.

Verification and limits: [docs/TEST-REPORT.md](docs/TEST-REPORT.md). Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Font attribution: [docs/licenses/Manrope-OFL.txt](docs/licenses/Manrope-OFL.txt).

Do not commit Telegram sessions, credentials or private signing keys. GitHub builds must reuse the existing signing Secrets. API credentials embedded in an APK are extractable; build-time Secrets keep them out of source control, not out of the installed binary. Telegram availability/limits still apply; this project does not promise unlimited or permanent storage.
