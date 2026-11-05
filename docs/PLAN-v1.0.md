# Memora Cloud — v1.0 brief (light & simple)

## 0. Status (updated)
0.9.5 has shipped **without** the AI block: no models, no OCR, no people, no smart search. The prepared subsystem was removed rather than shipped. Everything in Part F below stays deferred until you approve a model stack and the Telegram-terms review for any cloud-sourced analysis.

## 1. 0.9.5 — what it shipped

Your phone has **0.9.4**. 0.9.5 keeps exactly your 7 points:

1. Telegram system (0.9.2 login / backup / recovery) untouched — no other backend changes.
2. 0.9.3 interface + 0.9.4 dark-mode contrast; readable text, status colours kept, sun/moon toggle, no theme card in Settings.
3. Photo ring/bar = deep gold, video = deep blue, matching line, readable in both themes; small Gallery/Cloud toggles hide/show counters.
4. Compact, aligned selection badge; narrow bottom-only viewer toolbar.
5. In-app Gallery and Cloud video viewing, device decoders, iPhone-origin formats where the phone supports them — no all-codec promise.
6. Channel-deletion warning **plus** optional confirmed Archive & mute — not deletion protection.
7. Nothing unrelated broken; every user owns their own Telegram account and cloud.

**Everything else AI-shaped comes out of 0.9.5**: no OCR, no auto-tagging, no analysis queue, no ONNX runtime, no model downloads.

- Release APK goes back to about **25.4 MB** (the AI build measured 42.9 MB — the runtime alone was 18.5 MB).
- Runtime/UI additions only: Media3 player. No new permission.
- Removals: 5 AI source files, the AI settings screen, search hooks, AI tests and notices.

## 2. AI later = exactly two features, one optional module (Part F)

Nothing about AI ships until you approve models.

**A. People** — faces detected on the phone, grouped into people albums, rename / merge / split / hide / remove a wrong face / delete all face data. Probabilistic, always correctable.

**B. Smart search** — "dog on the beach", "under a tree", "paper", "document", "receipt". Appearance-based (colours, subject, layout), Latin/none needed. **No OCR**, so it does not read text inside a document — it finds pages that *look* like documents. Say the word and OCR is gone for good.

Size choices for you to approve at that point:

| Piece | Current verified option | Lighter option |
|---|---|---|
| Runtime | ONNX Runtime **+18.5 MB** (already built and tested) | TFLite/MediaPipe **≈ +4–6 MB** — must be rebuilt and re-verified from scratch |
| Smart search model | CLIP pair **≈ 153.6 MB** download | int8-quantised build, target **≈ 40–50 MB** — accuracy must be measured first |
| People model | SFace **38.7 MB**, Apache-2.0, verified | smaller face embedder **≈ 5–10 MB** — license + quality must be reviewed |

Downloads are optional, SHA-pinned, private, deletable, and never leave the phone. No model is chosen before you see name, size, license and accuracy notes.

## 3. Your v1.0 parts (unchanged order, one ZIP each)

| Part | Steps | Content |
|---|---|---|
| A | 1 | Smart delete + Memora Bin (permission decision pending) |
| B | 2 | FIFO upload order + queue screen (touches 0.9.2 queue only — needs your OK) |
| C | 3 | Lighter core: dead animation removed, cache caps + Clear cache, windowed lists, measured before/after |
| D | 4–7 | Green Nature theme, 5 tabs, splash, Home, Cloud hub + progress cards, Gallery chips, Albums, Search, Settings, viewer, dialogs, bn/en |
| E | 8 | Cloud tools, storage stats, notifications, moments, app lock |
| F | 9–12 | Analysis engine → **People** + **smart search** only (similar-image search included as one of its modes) |

## 4. Light & simple rules applied everywhere

- No neon, no animated background, no decorative motion beyond tiny counter ticks.
- Windowed/paged lists; capped Coil + TDLib cache with a user-set limit (default 500 MB).
- arm64 only; R8 + resource shrinking kept; no new permission unless Part A needs it.
- No developer server, no secrets in the repo, no hidden collection.

## 5. Open decisions (blocking)

1. **0.9.5 = light media/UI build, AI deferred to Part F?** (my recommendation) — or keep People + smart search inside 0.9.5 at +18.5 MB base.
2. **Runtime for Part F:** ONNX now (verified, heavier) or TFLite (lighter, needs full rebuild + re-verify)?
3. **Confirm OCR is dropped** ("paper/document" = appearance only).
4. **Part A:** direct APK (silent bin move, all-files access) or Play-safe (system sheet once per batch)?
5. **Part B:** may I change only upload ordering inside the 0.9.2 backup engine?
6. **Cache cap:** 500 MB default OK?
