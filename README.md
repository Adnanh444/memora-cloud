# Memora Cloud 📱☁️

**Your phone gallery, backed up into your own private Telegram channel.**
No account on our side, no server, no ads. Your photos go from your phone to your Telegram — nowhere else.

Version **2.0** · Android 8.0+ · Works with your own Telegram account

---

## What it does

| | |
|---|---|
| 📸 **Backup** | Photos and videos upload to a private channel you own |
| 🗂 **Folders** | Your phone folders appear as they are — Camera, WhatsApp, Screenshots… |
| ☁️ **Cloud tab** | See everything that is backed up, even if you deleted it from the phone |
| 🧹 **Bin** | Deleted files wait 30 days before they are really gone |
| ⬇️ **Download** | Bring anything back from the cloud, file by file |
| 🔍 **Sort** | Newest, oldest, by name, by size |
| 🌙 **Dark mode** | Readable in both themes |

---

## Install

1. Download `Memora.apk` from the **Actions → Build APK** page of this repository.
2. Tap it on your phone.
3. If Android says *"For your security…"*, tap **Settings → Allow from this source**, then install.
4. If Play Protect says *"Unsafe app blocked"*, tap **More details → Install anyway**. This appears because the app is not distributed through Play, not because anything is wrong.
5. Open the app.

**Updating:** install the new APK over the old one. Your data stays. Never uninstall to update — uninstalling deletes the app's index and bin.

---

## First run — 4 steps

**1. Connect Telegram**
Enter your `api_id` and `api_hash` from [my.telegram.org](https://my.telegram.org) → *API development tools*. Free, takes a minute.
Then your phone number → the code Telegram sends you → your two-step password if you have one.

**2. Create the storage**
The app makes a private channel in your account. Only you can see it. Do not delete it, rename it, or add members.

**3. Choose what to back up**
Backup → pick the folders you want (or press **All on**), and whether to include photos, videos, or both.

**4. Start**
Tap back up. Uploads run in the background with a notification. You can pause — the file in flight finishes first, so nothing is ever left half-uploaded.

---

## Using it

**Gallery** — your phone library, by date. Tap a photo to open it. Long-press, then drag, to select many.

**Cloud** — everything backed up. Folder chips at the top filter by folder. Tap **+ New folder** to make your own.

**Bin** — deleted files, 30 days. Tap to preview, restore, or delete for good.

**Sort** — the icon in the top bar. Your choice is remembered.

**Backup screen** — shows how many files are in the cloud and how many phone photos already match, so you always know your library is in sync.

---

## Screenshots

| Gallery | Cloud | Folders | Bin |
|---|---|---|---|
| ![Gallery](docs/screenshots/01-gallery.png) | ![Cloud](docs/screenshots/02-cloud.png) | ![Folders](docs/screenshots/03-folders.png) | ![Bin](docs/screenshots/04-bin.png) |

*Capture these four screens on your phone and drop them into `docs/screenshots/` with those names.*

---

## Rules the app follows

- **Never deletes your originals.** Backup only reads.
- **Never uploads twice.** Every file is fingerprinted before upload.
- **Type AND folder must be ON** for anything to be uploaded.
- **Cloud and phone are separate libraries.** Downloading never triggers an upload.
- **No secrets in the app.** Credentials stay encrypted on your phone; there is no developer server.

## If something looks wrong

| Symptom | What to do |
|---|---|
| "Cloud library not loaded" | Telegram → **Restore existing backup**. Uploads wait until the library is read. |
| Counts don't match | Gallery is the phone, Cloud is the channel. They are meant to differ. |
| Files show under **Unfiled** | Their folder is unknown yet — open the Cloud tab, it fills in automatically. |
| New phone / reinstalled | Sign in, then **Restore existing backup** → wait for "Restore complete". |

## Privacy

The app talks to Telegram and to nothing else. No analytics, no ads, no crash reporting, no developer server.

## Licence

Private project. Not for redistribution.
