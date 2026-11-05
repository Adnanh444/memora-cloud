# Privacy notes — 0.9.5

Memora has no developer-operated media server. Cloud backup/view/download use TDLib and the user's own Telegram account and destination; Telegram's terms, availability and privacy practices apply. No unlimited/permanent storage guarantee is made. Existing originals and organization metadata are not independently end-to-end encrypted against compromise of the Telegram account.

**This build has no AI.** There is no image-recognition model, no text recognition, no face grouping, no embedding index and no model download. Nothing in the app analyses your photos, and no photo, vector or query is sent to any AI service. `docs/PLAN-v1.0.md` describes the deferred optional work and its approval requirements.

Local photos/videos use Android media permissions and system consent. MediaStore-native favourites do not grant access to Google Photos account favourites or another gallery's private data. Historical organization records remain. This build does not add document/audio/general-file backup, all-files access or MANAGE_MEDIA. Original backup bytes are not transcoded to make a preview playable.

## Network use and caching
- Phone gallery thumbnails are read from the device: no network, no data cost.
- Cloud previews are **not fetched on a metered connection** unless you allow it in Settings (default off). When blocked, cloud cells show an empty tile, so scrolling the cloud grid on mobile data costs nothing.
- Opening a cloud photo or video yourself downloads that single original, temporarily, into app-private storage for display/playback and is released by the existing cleanup. That tap is your consent for the transfer.
- Backup uploads continue to follow your backup settings and connection rules; this policy only governs previews and playback.
- The preview/thumbnail disk cache is bounded by your chosen ceiling (200/500/1000 MB, default 500) with oldest-first eviction; **Clear preview cache** empties it. Telegram's own media file cache is managed by TDLib's `use_storage_optimizer` behaviour; this build does not set an explicit TDLib cache ceiling.
- Clearing caches never deletes phone originals, cloud copies, favourites, albums or settings.

API credentials are entered in the app and encrypted with the existing 0.9.2 local Keystore implementation. A replacement installation needs credentials again before authentication. Optional build-bundled credentials remain extractable; never commit them. Existing signing keys, passwords and Telegram sessions must not be published or replaced to bypass an upgrade mismatch.

The explicit archive/mute action changes only the selected verified backup channel's notification mute and chat-list placement. It never deletes originals, notes, messages or the channel. It cannot stop users deleting the channel in Telegram or guarantee it stays archived. Keep the backup channel and Saved Messages recovery notes. Pause backup manually before logout/recovery; the retained legacy system is single-account/destination, not full profile isolation.

Optional App lock uses Android screen credentials and secure-window flags. No custom Memora PIN is collected. Background backup can continue while the UI is locked. App lock does not lock Telegram or other galleries and is not an owner-approval service. Production invite/revocation, complete profile isolation, public privacy/policy compliance and phone validation remain pending.

## Future optional analysis (not in this build)
Any later analysis would have to be on-device, opt-in, default-off, deletable, and approved model-by-model (name, size, licence, accuracy) before download — and a cloud-sourced analysis would additionally need a Telegram-terms review. None of that exists in 0.9.5.
