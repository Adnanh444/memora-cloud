# Remaining AI, access-control and launch work

## Decisions selected for the user

The user delegated the best choices: Gallery backup-coverage rings plus separate Cloud totals; small New album header action; four fixed utility tiles; optional private on-device AI; invite-only launch approval; Telegram recovery remains the priority. These choices do not need to be asked again. Only owner-specific deployment credentials/domain and operational decisions must be provided when actually deploying a service.

## Current milestone is not the whole requested app

0.9.4 keeps the 0.9.3 UI/local-lock milestone, fixes dark-mode content-color inheritance and explicitly restores 0.9.2 Telegram login/backup/recovery at the user’s request. The 0.9.3 Telegram hardening/scoping additions are not included; see IMPLEMENTATION.md. It does **not** include face embeddings, semantic image inference, AI OCR indexing, a licensed AI model download, face circles, an invitation server, signed remote access grants or Play Integrity verification. Metadata search is deliberately labeled honestly. No fake AI/approved-user switch is shipped.

## Next: optional on-device AI and people search

- Select auditable, redistribution-permitted visual/text embedding and face-grouping models. Record model origin, license, version and SHA-256. Face detection alone does not establish which pictures show the same person.
- Download models only after explicit opt-in; show actual download size, battery/storage use and indexing progress. Keep the base APK small. Verify downloads cryptographically; no arbitrary model/code URLs.
- Index only user-selected readable local media. Generate embeddings/OCR locally, cache with media identity/modification markers, and remove stale entries when files/access change.
- Face grouping must be opt-in, local and editable: name/merge/split/remove groups; delete all face/search data; respect private/hidden exclusions. No public-person identification or developer media upload is needed.
- Search UI should combine natural-language visual results with existing date/type/folder/album filters and keep scope clear. Person circles should open actual indexed groups, not placeholders or invented matches.
- Verify English/Bengali query handling, accuracy, false grouping, low-memory devices and battery/thermal behavior. Background indexing must be cancelable and compatible with backups.
- Local storage alone does not establish data provenance. Exclude Telegram-downloaded/restored content from AI indexing by default until its permitted use is reviewed.
- **Cloud AI is not automatically allowed.** Telegram API terms section 1.5 places broad restrictions on AI/ML uses of Telegram-obtained data. Review current terms and privacy requirements before any remote-media indexing; do not imply that local inference or user consent automatically overrides platform terms.

References: https://core.telegram.org/api/terms and https://telegram.org/tos/content-licensing . These references inform planning, not a legal approval of the product.

## Next: invite-only authorization, without owning users' cloud

Use a small HTTPS authorization service for owner-approved app accounts/devices. It should store only the minimum approval/revocation and cryptographic device/access records—not photos, Telegram OTPs/passwords, TDLib sessions or recovery keys.

Before implementation/deployment, define:
- Owner-admin authentication and recovery, approval/revocation audit log and protected server signing secrets.
- Server-verifiable app-user identity; do not trust a Telegram ID or an `approved=true` value asserted by a modified client.
- Device-bound, short-lived signed grants; server-side revocation, replay protection and rate limiting. Secure defaults must fail closed when required authorization cannot be established.
- An explicitly limited offline grace period; immediate remote revocation cannot be guaranteed on an offline device.
- Phone-replacement/recovery access that does not make the user's own backup hostage to the developer service. Account owners must retain access to their Telegram data even if Memora access is revoked.
- Proper account-scoped queue, destination, organization/index and session transitions before supporting multiple app profiles/account switching commercially.
- Deployment domain/TLS, data retention/deletion, backups, incident response and operational costs. No production service or owner admin account has been created here.

App authorization protects the official application/service path; it cannot absolutely prevent reverse engineering or someone changing a copied APK. Public source can be copied; even a private source repository does not make APK code secret. Decide the release/license policy deliberately. Local screen lock is a different feature and must not be described as invite-only access.

## Launch gates

1. Preserve the existing signing identity; protect repository/owner accounts with strong authentication and keep recoverable key backups. Review CI permissions and branch protection. Never regenerate a signing key to solve an install mismatch.
2. The current beta uses 0.9.2-style in-app API ID/hash setup; users need credentials again on a replacement installation. If a future public app should provide phone-only login, supply application credentials before authentication using a reviewed build/provisioning design, not a claim to fetch them from Telegram after login. Each user authenticates their own Telegram account and chooses their own destination. Review Telegram third-party-client terms, disclosure, branding and storage/AI limitations before public distribution.
3. Complete optimized owner-signed release builds, dependency/license/security review, account-boundary tests, interrupted-upload recovery and a genuine replacement-phone restore test with disposable originals.
4. Document what is encrypted and where its keys live. Existing metadata key notes in Saved Messages do not provide independent protection from Telegram-account compromise. Any future media encryption requires a recovery key not stored only on the lost phone and a tested recovery procedure.
5. Review the chosen distribution channel's **current** target-SDK, media-permission, privacy/Data Safety, developer verification and applicable face-data obligations. Target SDK 34 is retained in this beta; it is not a claim of current Play submission eligibility.
6. Add genuine server-verified integrity checks if distributing through Play, based on the deployment design. Do not treat a client-only signature check or obfuscation as complete anti-tamper protection.
7. Test access approval/revocation, authorization-service outage, offline grace, a stolen device, lost SIM/Telegram access and a replacement phone. Maintain a documented support/recovery path.
8. Only after acceptance: approved main merge, successful main update, then obsolete repository ZIP/build-file cleanup. Source ZIP size is not APK size; no unsupported size-reduction percentage should be advertised.

Official references: https://core.telegram.org/api/obtaining_api_id , https://core.telegram.org/tdlib/getting-started , https://developer.android.com/studio/publish/app-signing , https://developer.android.com/google/play/integrity .

## Concrete remaining cloud-hardening audit items

This beta retains the baseline's single-account/active-destination ledger assumptions. The current rollback does not retain 0.9.3 backend destination-scoped visibility. Even UI scoping alone would not replace database/profile isolation. Before public multi-account or multi-destination use, scope duplicate lookup and queue ownership to the intended account/destination, migrate/profile the organization state deliberately, and test logout/switch/relogin without replaying a prior account's queue. Do not use account/destination switching with valuable pending uploads as a casual test.

Free up space still relies on the existing fresh remote message/file-size check, not downloading and hashing every remote original. A launch audit should also verify the current local content/hash freshness before deleting an original that may have been edited. Do not exercise destructive bulk cleanup on valuable media to test the beta.

Audit recovery discovery for renamed channels, old/missing Saved Messages notes and very large histories; retain the notes and storage channel while testing. Existing key-note/metadata rotation and fallback-version handling need real-account loss/recovery tests before advertising full recovery guarantees. An additional independent copy of irreplaceable originals remains advisable; no single cloud service can guarantee permanent availability.

The 0.9.2 recovery rollback also removes strict/bounded page scanning, operation serialization, incremental positive batches, explicit metadata-failure warnings and the held-positive-receipt rediscovery correction. Revisit these as a separately approved safety milestone before public launch; current automated source/hash checks establish baseline equivalence, not correctness of every legacy recovery path.
