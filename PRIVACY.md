# Privacy Policy (draft, Phase 1)
Memora Cloud has no developer-operated server and no analytics. Media is uploaded only to a private Telegram channel owned by the user (from Phase 3). Telegram credentials are handled by Telegram's official login flow; Memora never stores your password or OTP. Face data (Phase 8) is processed on-device, encrypted, and can be deleted at any time. This policy will be finalized as features ship.

## Telegram (Phase 3)
- Sign-in uses Telegram's official TDLib library: phone number, login code and (if you set one) your two-step password go directly to Telegram. Memora never stores your password or the code.
- The local Telegram session database is encrypted with a random key that is itself protected by the Android Keystore.
- The app creates one private channel in YOUR account. "Forget this storage" only removes it from the app; it never deletes anything in Telegram.
- Each user enters their own Telegram API key in the app. It is stored encrypted on that phone only and is never sent to the developer or committed to the repository.
