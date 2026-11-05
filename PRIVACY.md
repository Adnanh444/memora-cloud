# Privacy notes

Memora Cloud has no developer-operated media server or analytics integration. Its existing cloud features use TDLib and the user's Telegram account/destination; Telegram's terms, privacy practices and availability apply. This app does not promise unlimited or permanent storage.

Local photos/videos are read through Android media permissions. Android 11+ phone favorites use the platform MediaStore flag and Android-owned consent. This does not grant access to Google Photos account favorites or another gallery's private database. Historical Memora organization records remain in the existing database/metadata system rather than being silently erased during this transition.

The app keeps its backup index, preferences, organization records and Telegram session data in app-managed storage, and uses temporary files for uploads/previews/downloads. Do not publish session data, private signing keys or build credentials. Build-time Secrets keep API credentials out of source control; credentials embedded in an APK remain extractable.

Android owns the system permission, favorite, trash/restore and deletion consent screens. No all-files access or optional MANAGE_MEDIA privilege is added by this revision. Choosing Delete forever is destructive; canceled or uncertain uploads are not proof of a verified remote copy.

No new face-analysis/biometric feature or private-gallery integration is introduced in 0.9.2. Consult the implementation guide for exact support boundaries and the phone checks still required.
