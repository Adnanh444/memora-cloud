# Memora Cloud — 0.9.5 media update

**Photos/videos only. Test build, not final public-launch approval.** Builds on 0.9.4; older source ZIPs and branches are retained. No documents/audio/general-file backup is added, and **this build contains no AI**: no models, no OCR, no image analysis, no cloud AI.

## New in 0.9.5
- **Gold/blue coverage:** photo counters use deep gold, video counters deep blue; each ring and its compact line share one tint and progress. Readable in light and dark (every label pair verified ≥ 4.5:1). Independent Gallery/Cloud visibility buttons persist after restart; backup activity is unaffected.
- **Compact selection, bottom actions:** compact numeric selection badges, aligned 48 dp targets, and one narrow bottom toolbar in the photo viewer.
- **In-app video:** Gallery and Cloud both play through a Media3 player with seek/pause, lifecycle pause, error + retry, and a temporary download of the cloud original. Photos keep native/Coil decoding with an explicit unsupported-image message.
- **Channel warning + Archive & mute:** an explicit warning about what channel deletion means, plus an opt-in confirmed archive/mute convenience. It cannot lock the channel against deletion in Telegram.
- **Data-friendly cloud previews:** on a metered connection no cloud thumbnail or video is fetched; cells stay empty and only an item you deliberately open costs data. Settings → **Cloud previews on mobile data** (default off) re-enables them. Phone gallery thumbnails never use the network.
- **Bounded caches:** preview/thumbnail disk cache is capped at your chosen ceiling (200/500/1000 MB, default 500) with oldest-first eviction, plus a **Clear preview cache** action. Telegram's own media cache is managed by TDLib's storage optimizer.
- **Better sign-in:** the Telegram setup flow got a three-step indicator, paste buttons, inline validation, disabled-until-valid buttons with in-button progress, password reveal, and clearer help text. **The underlying auth, storage and backup code is unchanged from 0.9.2.**

## Size
This build has no AI runtime and no bundled models. Measured release/debug APK sizes and the exact delta versus 0.9.4 are in [TEST-REPORT.md](docs/TEST-REPORT.md).

## Telegram / recovery
The 0.9.2 login, credentials, backup/recovery, storage/key-note formats and original upload safeguards remain. Only the requested archive/mute helper is added to `TelegramClient`; nothing else in that boundary changed. CI requires the **existing signing Secrets**, not API Secrets; API ID/hash are entered in the app. Have credentials ready again on a replacement installation before authentication. Pause backup before logout/recovery and use one account/destination per installation.

HEIC/HEIF, JPEG/PNG/WebP and compatible iPhone MOV/MP4 work where Android/device decoders support them. **No universal HEVC/HDR/Dolby Vision/ProRes/RAW/AVIF support or transcoding is promised.** Backup bytes are unchanged regardless of preview support.

Read [IMPLEMENTATION.md](IMPLEMENTATION.md), [TEST-REPORT.md](docs/TEST-REPORT.md), and [remaining launch gates](docs/LAUNCH-AND-AI.md). [PLAN-v1.0.md](docs/PLAN-v1.0.md) records the deferred roadmap (people/smart search, smart delete/bin, FIFO queue, theme overhaul) and why it is not in this build. Owner-signed phone tests, real Telegram validation, and policy/security review remain required. No main merge, remote push or branch deletion is performed by this package.
