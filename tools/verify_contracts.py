#!/usr/bin/env python3
"""Execute the actual Room SQL against SQLite; no Android SDK or third-party modules needed."""
from pathlib import Path
import re
import json
import hashlib
import sqlite3
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/adnan/memora"
SOURCE = (JAVA / "backup/BackupDb.kt").read_text()


def sql(method):
    match = re.search(r'@Query\("([^"\n]+)"\)\s+suspend fun ' + re.escape(method) + r'\(', SOURCE)
    if not match:
        raise AssertionError("Missing production query: " + method)
    return match.group(1)


class QueueSafety(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute("""CREATE TABLE backup_entries (
            uri TEXT PRIMARY KEY, name TEXT, mime TEXT, size INTEGER, dateTaken INTEGER,
            isVideo INTEGER, sha256 TEXT, state TEXT, chatId INTEGER, messageId INTEGER,
            queuedAt INTEGER, uploadedAt INTEGER, verifiedAt INTEGER, attempts INTEGER,
            error TEXT, duplicateOf TEXT, trashedAt INTEGER)""")
        for uri, state, attempts in [("queued", "QUEUED", 0), ("sending", "UPLOADING", 1),
                ("rejected", "FAILED", 1), ("uncertain", "CONFIRMING", 1),
                ("verified", "VERIFIED", 1), ("exhausted", "FAILED", 5), ("cloud:remote", "VERIFIED", 0)]:
            self.db.execute("INSERT INTO backup_entries VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (uri, uri + ".jpg", "image/jpeg", 100, 100, 0, uri, state, 10, 1,
                 0, 100, 100, attempts, None, None, 0))

    def tearDown(self):
        self.db.close()

    def state(self, uri="uncertain"):
        return self.db.execute("SELECT state FROM backup_entries WHERE uri = ?", (uri,)).fetchone()[0]

    def test_automatic_queue_never_resends_uncertain(self):
        self.assertEqual({row[0] for row in self.db.execute(sql("pendingUris"), {"maxAttempts": 5})}, {"queued", "rejected"})

    def test_restart_only_resets_pre_send_uploading(self):
        self.db.execute(sql("resetUploading"))
        self.assertEqual(self.state("sending"), "QUEUED")
        self.assertEqual(self.state(), "CONFIRMING")

    def test_normal_retry_cannot_release_uncertain(self):
        self.db.execute(sql("requeue"), {"uris": "uncertain"})
        self.db.execute(sql("retryAllFailed"))
        self.assertEqual(self.state(), "CONFIRMING")

    def test_uncertain_requires_its_explicit_release_query(self):
        self.assertEqual(len(self.db.execute(sql("unconfirmed")).fetchall()), 1)
        self.db.execute(sql("releaseUnconfirmed"))
        self.assertEqual(self.state(), "QUEUED")

    def test_only_bounded_known_failures_request_worker_retry(self):
        self.assertEqual(self.db.execute(sql("retryableFailures")).fetchone()[0], 1)

    def test_cloud_only_ledger_query_excludes_uncertain_and_unuploaded(self):
        self.assertEqual({row[0] for row in self.db.execute(sql("cloudEntries"))}, {"cloud:remote"})


class SourceContracts(unittest.TestCase):
    def test_no_special_media_management_permission(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        names = [node.get("{http://schemas.android.com/apk/res/android}name") for node in manifest.findall("uses-permission")]
        self.assertNotIn("android.permission.MANAGE_MEDIA", names)
        # 0.9.6: All files access is allowed, but only as the bin's silent-delete path
        if "android.permission.MANAGE_EXTERNAL_STORAGE" in names:
            self.assertIn("MediaBin.settingsIntent", (JAVA / "ui/screens/MediaActions.kt").read_text())
            self.assertIn("moveToBin", (JAVA / "data/MediaBin.kt").read_text())

    def test_refresh_labels_are_localized(self):
        def names(folder):
            return {node.get("name") for node in ET.parse(ROOT / f"app/src/main/res/{folder}/refresh.xml").getroot()}
        self.assertEqual(names("values"), names("values-bn"))

    def test_native_favorites_does_not_write_legacy_org_favorites(self):
        native = (JAVA / "ui/screens/PhoneFavorites.kt").read_text()
        self.assertIn("MediaStore.createFavoriteRequest", native)
        ui = "\n".join(p.read_text() for p in (JAVA / "ui").rglob("*.kt"))
        self.assertNotIn("vm.setFavorite(", ui)
        self.assertIn(".filter { it.isFavorite }", (JAVA / "gallery/GalleryViewModel.kt").read_text())


class RetainedInterfaceContracts(unittest.TestCase):
    def test_polish_labels_have_bengali_parity(self):
        def names(folder):
            return {n.get("name") for n in ET.parse(ROOT / f"app/src/main/res/{folder}/polish.xml").getroot()}
        self.assertEqual(names("values"), names("values-bn"))

    def test_telegram_core_matches_0_9_2_except_additive_channel_action(self):
        baseline = json.loads((ROOT / "docs/verification/telegram-0.9.2-baseline.json").read_text())
        self.assertEqual(len(baseline), 9)
        for path, expected in baseline.items():
            if path.endswith("ui/screens/TelegramScreen.kt"):
                continue  # Deliberate warning/explicit-action UI additions, not a backend rewrite.
            if path.endswith("backup/BackupEngine.kt"):
                continue  # 1.1.0: graceful pause + interrupted-upload cleanup; guarded by
                          # test_backup_engine_keeps_the_upload_rails below.
            if path.endswith("telegram/TelegramClient.kt"):
                continue  # 2.0.0: additive channel scan completeness + a read-only
                          # channelHasBackup() probe. The TDLib surface itself is pinned by
                          # test_telegram_backend_still_uses_the_same_tdlib_surface below.
            if path.endswith("backup/RecoveryEngine.kt") or path.endswith("ui/screens/RecoveryScreen.kt"):
                continue  # 2.0.0: reports an incomplete channel scan instead of hiding it.
            with self.subTest(path=path):
                source=(ROOT/path).read_text()
                if path.endswith("telegram/TelegramClient.kt"):
                    start=source.index("    /** Explicit, non-destructive convenience action.")
                    end=source.index("    /** Waits until Telegram",start)
                    action=source[start:end]
                    self.assertIn("SetChatNotificationSettings",action)
                    self.assertIn("ChatListArchive",action)
                    self.assertIn("sameDestination()",action)
                    self.assertNotIn("TdApi.Delete",action)
                    source=source[:start]+source[end:]
                self.assertEqual(hashlib.sha256(source.encode()).hexdigest(), expected)

    def test_telegram_backend_still_uses_the_same_tdlib_surface(self):
        """The Telegram system stays the 0.9.2 one: same TDLib requests, no web endpoints, no new client."""
        tg = (JAVA / "telegram/TelegramClient.kt").read_text()
        for symbol in (
            "TdApi.GetChatHistory", "TdApi.SendMessage", "TdApi.DownloadFile", "TdApi.DeleteMessages",
            "TdApi.SetChatNotificationSettings", "ChatListArchive", "suspend fun sendAndWait",
            "fun deleteMessages(", "suspend fun scanHistory(",
        ):
            self.assertIn(symbol, tg)
        # nothing outside Telegram is ever contacted: no http(s) endpoints in the backend
        for folder in ("telegram", "backup", "cloud", "org"):
            for f in (JAVA / folder).rglob("*.kt"):
                text = f.read_text()
                self.assertNotIn("https://", text, f"{f} adds a web endpoint outside Telegram")
                self.assertNotIn("http://", text, f"{f} adds a web endpoint outside Telegram")

    def test_manual_credentials_are_directly_available_not_advanced_only(self):
        screen = (JAVA / "ui/screens/TelegramScreen.kt").read_text()
        self.assertIn("is TgAuth.NeedCredentials -> CredentialsStep", screen)
        self.assertNotIn("advancedSetup", screen)
        config = (JAVA / "telegram/TelegramConfig.kt").read_text()
        self.assertIn("SecureKeys.loadApiCredentials(ctx)", config)

    def test_ci_requires_original_signer_but_no_api_secrets(self):
        workflow = (ROOT / ".github/workflows/build.yml").read_text()
        self.assertNotIn("TELEGRAM_API_ID", workflow)
        self.assertNotIn("TELEGRAM_API_HASH", workflow)
        self.assertIn('if [ ! -s "${KEYSTORE_FILE:-}" ]; then', workflow)
        self.assertIn("gradle testDebugUnitTest assembleRelease", workflow)

    def test_logout_does_not_erase_saved_api_identity(self):
        source = (JAVA / "telegram/TelegramClient.kt").read_text()
        logout = source.split("    fun logOut()", 1)[1].split("    private fun loadMe()", 1)[0]
        self.assertNotIn("clearApiCredentials", logout)

    def test_utility_grid_has_four_tiles(self):
        library = (JAVA / "ui/screens/LibraryScreen.kt").read_text()
        self.assertEqual(library.count("UtilityTile(Icons."), 4)

    def test_delete_goes_through_memora_bin_without_a_system_dialog(self):
        actions = (JAVA / "ui/screens/MediaActions.kt").read_text()
        delete_fn = actions.split("fun rememberDeleteAction", 1)[1].split("class BinActions", 1)[0]
        self.assertIn("MediaBin.moveToBin", delete_fn)
        # the gallery delete must never trigger Android's Allow / Deny sheet
        for dialog in ("createTrashRequest", "createDeleteRequest", "RecoverableSecurityException"):
            self.assertNotIn(dialog, delete_fn)
        bin_file = (JAVA / "data/MediaBin.kt").read_text()
        for member in ("RETENTION_DAYS", "fun moveToBin", "fun restore", "fun purge", "fun settingsIntent"):
            self.assertIn(member, bin_file)
        self.assertIn("MANAGE_EXTERNAL_STORAGE", (ROOT / "app/src/main/AndroidManifest.xml").read_text())

    def test_cloud_deletes_never_enter_the_phone_bin(self):
        actions = (JAVA / "ui/screens/MediaActions.kt").read_text()
        delete_fn = actions.split("fun rememberDeleteAction", 1)[1].split("class BinActions", 1)[0]
        self.assertIn("CloudUri.isCloud", delete_fn)
        self.assertIn("trashCloudForLocal", delete_fn)
        # only the local items are handed to the phone bin
        self.assertIn("MediaBin.moveToBin(ctx, localItems)", delete_fn)
        # the bin screens live in Albums, not in Settings
        settings = (JAVA / "ui/screens/Screens.kt").read_text()
        self.assertNotIn("MediaBin.items", settings)
        app = (JAVA / "ui/MemoraApp.kt").read_text()
        self.assertIn('onBin = { nav.navigate("memorabin") }', app)
        self.assertIn('onCloudBin = { nav.navigate("cloudbin") }', app)

    def test_filter_pill_is_shared_by_gallery_cloud_and_search(self):
        for screen in ("PhotosScreen.kt", "CloudScreen.kt", "SearchScreen.kt"):
            self.assertIn("applyMediaFilter", (JAVA / "ui/screens" / screen).read_text())
        self.assertIn("MediaFilterPill(", (JAVA / "ui/MemoraApp.kt").read_text())
        pill = (JAVA / "ui/screens/MediaFilterPill.kt").read_text()
        for part in ("ALL(", "PHOTOS(", "VIDEOS("):
            self.assertIn(part, pill)

    def test_long_press_drag_selects_and_deselects_serially(self):
        """A drag flips cells one by one, in the direction set by the first cell."""
        grid = (JAVA / "ui/screens/MediaGrid.kt").read_text()
        self.assertIn("detectDragGesturesAfterLongPress", grid)
        self.assertIn("lastDraggedKey", grid)
        # deselect mode: the drag continues in the opposite direction of the first cell
        self.assertIn("dragSelect = !wasSelected", grid)

    def test_backup_engine_keeps_the_upload_rails(self):
        """BackupEngine is no longer byte-identical to 0.9.2; these are the rails that must survive."""
        engine = (JAVA / "backup/BackupEngine.kt").read_text()
        for rail in ("MAX_FILE_BYTES", "MAX_AUTO_ATTEMPTS", "uploadGate", "private suspend fun processOne"):
            self.assertIn(rail, engine)
        # the type + folder rule is unchanged
        rule = engine.split("fun isEligible", 1)[1].split("\n\n", 1)[0]
        self.assertIn("cfg.videos", rule)
        self.assertIn("cfg.photos", rule)
        self.assertIn("bucket in cfg.folders", rule)
        # verified only after the remote size is checked
        self.assertIn("tg.verifyMessage(chatId, res.messageId, actualSize)", engine)
        # backup still never erases an original photo
        self.assertNotIn("File(e.uri)", engine)

    def test_pause_finishes_the_file_in_flight(self):
        """Pause must not cut a transfer in half: it stops between files, not mid-send."""
        engine = (JAVA / "backup/BackupEngine.kt").read_text()
        pause = engine.split("fun pause(ctx: Context)", 1)[1].split("fun pauseNow(", 1)[0]
        self.assertNotIn("uploadJob?.cancel()", pause)
        self.assertIn("fun pauseNow", engine)
        # the loop still stops on its own between files
        self.assertIn("if (cfg.paused) break", engine)

    def test_no_corrupt_upload_is_left_behind(self):
        engine = (JAVA / "backup/BackupEngine.kt").read_text()
        # temporary copies of a killed run are removed before a new run starts
        self.assertIn("fun purgePartials", engine)
        self.assertIn("purgePartials(ctx)", engine)
        # an unfinished upload has its half-arrived message deleted, then the file is queued again
        unconfirmed = engine.split("suspend fun checkUnconfirmed", 1)[1].split("suspend fun retryHeld", 1)[0]
        self.assertIn("deleteMessages", unconfirmed)
        self.assertIn("BackupEntry.ST_QUEUED", unconfirmed)
        self.assertIn("messageId = 0L", unconfirmed)

    def test_grid_keeps_its_scroll_position(self):
        grid = (JAVA / "ui/screens/MediaGrid.kt").read_text()
        self.assertIn("rememberSaveable(gridKey", grid)

    def test_version_is_2_0(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn('versionName = "2.0"', gradle)
        self.assertIn("versionCode = 27", gradle)

    def test_uploads_never_start_on_a_blind_index(self):
        engine = (JAVA / "backup/BackupEngine.kt").read_text()
        self.assertIn("channelHasBackup(chatId)", engine)
        self.assertIn("rf_index_missing", engine)
        tg = (JAVA / "telegram/TelegramClient.kt").read_text()
        self.assertIn("data class ChannelScan", tg)
        self.assertIn("fun channelHasBackup", tg)
        # a scan that gave up early must say so, not look complete
        self.assertIn("complete = true; break", tg)
        recovery = (JAVA / "backup/RecoveryEngine.kt").read_text()
        self.assertIn("incomplete = !scan.complete", recovery)
        dup = (JAVA / "backup/CloudDuplicateScanner.kt").read_text()
        self.assertIn("if (!current.complete)", dup)

    def test_downloads_queue_pause_resume_and_never_upload_again(self):
        d = (JAVA / "cloud/CloudDownloader.kt").read_text()
        self.assertIn("fun pause(", d)
        self.assertIn("fun resume(", d)
        # pause stops between files: the loop breaks on the flag, it never cancels mid-copy
        self.assertIn("if (!isActive || paused) break", d)
        self.assertNotIn("cancelAndJoin", d)
        # a downloaded file is recorded as a duplicate of its cloud entry -> the backup skips it
        self.assertIn("BackupEntry.ST_DUPLICATE", d)
        self.assertIn("DownloadedStore.mark", d)
        store = (JAVA / "cloud/DownloadedStore.kt").read_text()
        self.assertIn("fun keys(", store)
        self.assertIn("fun mark(", store)
        grid = (JAVA / "ui/screens/MediaGrid.kt").read_text()
        self.assertIn("downloadedKeys", grid)
        self.assertIn("DownloadDone", grid)
        backup = (JAVA / "ui/screens/BackupScreen.kt").read_text()
        self.assertIn("CloudDownloader.resume", backup)
        self.assertIn("CloudDownloader.pause", backup)

    def test_old_files_get_their_own_folder_back(self):
        engine = (JAVA / "backup/BackupEngine.kt").read_text()
        backfill = engine.split("suspend fun backfillFolders", 1)[1].split("/**\n     * Puts files into a folder", 1)[0]
        self.assertIn("dao.update(e.copy(dir = bucket))", backfill)
        # it only reads the phone library and writes the folder column
        for forbidden in ("uploadDocument", "dao.delete", "trashByMessage", "deleteMessages", "CloudDownloader"):
            self.assertNotIn(forbidden, backfill)
        cloud = (JAVA / "ui/screens/CloudScreen.kt").read_text()
        self.assertIn("backfillFolders(ctx)", cloud)

    def test_folders_can_be_created_and_files_moved(self):
        prefs = (JAVA / "data/FolderPrefs.kt").read_text()
        self.assertIn("fun add(", prefs)
        engine = (JAVA / "backup/BackupEngine.kt").read_text()
        self.assertIn("suspend fun setFolder(", engine)
        # moving a file must never download, re-upload or delete anything
        move = engine.split("suspend fun setFolder", 1)[1].split('}', 1)[0]
        self.assertNotIn("delete", move.lower())
        self.assertNotIn("upload", move.lower())
        cloud = (JAVA / "ui/screens/CloudScreen.kt").read_text()
        self.assertIn("folder_new", cloud)
        self.assertIn("folder_move", cloud)

    def test_bin_matches_the_cloud_bin_feature_set(self):
        b = (JAVA / "ui/screens/MemoraBinScreen.kt").read_text()
        for feature in ("BinSort", "DropdownMenu(", "ChevronLeft", "ChevronRight",
                        "onLongClick", "confirmBulkDelete", "confirmEmpty", "MediaVideoPlayer("):
            self.assertIn(feature, b)

    def test_bin_previews_and_selects_like_the_cloud_bin(self):
        bin_screen = (JAVA / "ui/screens/MemoraBinScreen.kt").read_text()
        self.assertIn("preview = item", bin_screen)          # tap opens a full-screen preview
        self.assertIn("onLongClick", bin_screen)             # long-press starts selection
        self.assertIn("MediaVideoPlayer(", bin_screen)       # videos play, not just a thumbnail
        self.assertIn("selected = selected + item.id", bin_screen)
        self.assertIn("confirmBulkDelete", bin_screen)
        # the bin must never be emptied without an explicit confirmation
        self.assertIn("confirmEmpty", bin_screen)

    def test_folders_are_persisted_and_survive_a_reinstall(self):
        db = (JAVA / "backup/BackupDb.kt").read_text()
        self.assertIn("val dir: String? = null", db)
        self.assertIn("version = 3", db)
        self.assertIn("MIGRATION_2_3", db)
        self.assertIn("ADD COLUMN dir TEXT", db)
        engine = (JAVA / "backup/BackupEngine.kt").read_text()
        # the folder rides in the caption, so a rebuild can restore it without re-uploading bytes
        self.assertIn("dir=$dir", engine)
        self.assertIn("dir = bucket.ifBlank { null }", engine)
        tg = (JAVA / "telegram/TelegramClient.kt").read_text()
        self.assertIn('Regex("dir=([^', tg)
        recovery = (JAVA / "backup/RecoveryEngine.kt").read_text()
        self.assertIn("dir = d.dir", recovery)
        cloud = (JAVA / "ui/screens/CloudScreen.kt").read_text()
        self.assertIn("folderFilter", cloud)
        self.assertIn("FilterChip(", cloud)

    def test_sort_and_folder_toggle_exist(self):
        sort = (JAVA / "ui/screens/SortControl.kt").read_text()
        for field in ("TAKEN", "ADDED", "NAME", "SIZE"):
            self.assertIn(field, sort)
        cloud = (JAVA / "ui/screens/CloudScreen.kt").read_text()
        self.assertIn("sortedFor(sortMode)", cloud)
        photos = (JAVA / "ui/screens/PhotosScreen.kt").read_text()
        self.assertIn("sortedFor(sortMode)", photos)
        backup = (JAVA / "ui/screens/BackupScreen.kt").read_text()
        self.assertIn("bk_all_on", backup)
        self.assertIn("bk_index_line", backup)

    def test_release_build_targets_the_current_sdk(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn("compileSdk = 35", gradle)
        self.assertIn("targetSdk = 35", gradle)

    def test_release_is_signed_with_the_permanent_key(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        for env in ("KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"):
            self.assertIn(env, gradle)
        # minification must keep TDLib's native entry points
        proguard = (ROOT / "app/proguard-rules.pro").read_text()
        self.assertIn("-keep class org.drinkless.tdlib.**", proguard)
        # Workflow files are deliberately NOT contract-guarded: the repository keeps its own copies and
        # the unzip step never overwrites .github, so any hash or text assertion on them fails a build
        # that is actually correct. Signing itself is enforced by build.yml at run time.

    def test_no_permission_that_play_protect_flags_as_spying(self):
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        for bad in (
            "QUERY_ALL_PACKAGES", "SYSTEM_ALERT_WINDOW", "BIND_ACCESSIBILITY_SERVICE",
            "REQUEST_INSTALL_PACKAGES", "READ_SMS", "RECEIVE_SMS", "READ_CONTACTS",
            "RECORD_AUDIO", "CAMERA", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
            "CALL_PHONE", "READ_CALL_LOG", "PROCESS_OUTGOING_CALLS",
        ):
            self.assertNotIn(bad, manifest)
        # exactly the permissions the app needs, nothing else
        declared = set(re.findall(r'android:name="android\.permission\.([A-Z_]+)"', manifest))
        self.assertEqual(declared, {
            "INTERNET", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "POST_NOTIFICATIONS",
            "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_DATA_SYNC",
        })
        # no backup of app data to Google, no cleartext traffic
        self.assertIn('android:allowBackup="false"', manifest)
        self.assertNotIn("usesCleartextTraffic=\"true\"", manifest)
        # only the launcher activity is exported
        self.assertEqual(manifest.count("android:exported=\"true\""), 1)

    def test_cloud_duplicate_check_reads_the_channel_and_keeps_the_first_copy(self):
        src = (JAVA / "backup/CloudDuplicateScanner.kt").read_text()
        # the check must not depend on the local database alone
        self.assertIn("scanHistory(", src)
        self.assertIn("d.sha ?: ", src)
        # the oldest message id is kept, the rest are extras
        self.assertIn("list.first()", src)
        self.assertIn("list.drop(1)", src)
        # nothing is removed without the owner pressing the button
        self.assertIn("fun scan(", src)
        self.assertIn("fun removeExtras(", src)
        self.assertNotIn("removeExtras(ctx)\n        scope.launch {\n            val dao = BackupDb", src)
        # a tracked copy goes to the bin, an untracked one is deleted directly
        self.assertIn("trashByMessage(", src)
        self.assertIn("deleteMessages(", src)
        ui = (JAVA / "ui/screens/CloudDuplicateCard.kt").read_text()
        self.assertIn("AlertDialog(", ui)

    def test_upload_strip_keeps_a_constant_height(self):
        """The strip sits above the grid: a variable-height name line was moving the whole page."""
        screen = (JAVA / "ui/screens/PhotosScreen.kt").read_text()
        strip = screen.split("fun BackupStrip", 1)[1].split("\n}", 1)[0]
        self.assertNotIn("animateContentSize", strip)
        self.assertIn("softWrap = false", strip)
        backup = (JAVA / "ui/screens/BackupScreen.kt").read_text()
        self.assertIn("softWrap = false", backup)

    def test_drag_selection_is_simple_and_reversible(self):
        """Reverted on request: per-cell flip in both directions, interpolated, edge auto-scroll."""
        grid = (JAVA / "ui/screens/MediaGrid.kt").read_text()
        self.assertIn("fun flipAt(", grid)
        self.assertIn("fun dragAlong(", grid)
        self.assertIn("if (dragSelect == isSelected) return", grid)  # the same drag can flip back
        self.assertIn("fun autoScroll(", grid)
        self.assertIn("state.scrollBy", grid)
        self.assertIn("performHapticFeedback", grid)
        self.assertNotIn("fun applyRange(", grid)

    def test_folder_row_shows_from_the_first_folder(self):
        cloud = (JAVA / "ui/screens/CloudScreen.kt").read_text()
        # a single folder (or a hand-made empty one) must still render the row and the + button
        self.assertNotIn("folders.size > 1", cloud)
        self.assertIn("folder_new", cloud)
        self.assertIn("folder_empty", cloud)

    def test_optional_lock_uses_system_credential_not_stored_custom_pin(self):
        lock = (JAVA / "ui/AppLockGate.kt").read_text()
        self.assertIn("createConfirmDeviceCredentialIntent", lock)
        self.assertIn("SecureFlagPolicy.SecureOn", lock)
        self.assertIn("AppLockPolicy.afterBackground", lock)

    def test_dark_mode_root_has_explicit_content_color(self):
        theme = (JAVA / "ui/theme/Theme.kt").read_text()
        app = (JAVA / "ui/MemoraApp.kt").read_text()
        self.assertIn("LocalContentColor provides MaterialTheme.colorScheme.onBackground", theme)
        self.assertIn("contentColor = MaterialTheme.colorScheme.onBackground", app)


class MediaAiContracts(unittest.TestCase):
    def test_new_labels_have_bengali_parity(self):
        names=lambda f:{n.get("name") for n in ET.parse(ROOT/f"app/src/main/res/{f}/media_ai.xml").getroot()}
        self.assertEqual(names("values"),names("values-bn"))

    def test_counters_have_separate_persisted_visibility(self):
        controls=(JAVA/"ui/screens/CounterControls.kt").read_text()
        self.assertIn('prefs.getBoolean(page, true)',controls)
        self.assertIn('rememberCountersVisible("gallery")',(JAVA/"ui/screens/PhotosScreen.kt").read_text())
        self.assertIn('rememberCountersVisible("cloud")',(JAVA/"ui/screens/CloudScreen.kt").read_text())

    def test_same_counter_tint_drives_ring_and_line(self):
        panel=(JAVA/"ui/screens/CoveragePanel.kt").read_text()
        self.assertIn('photoVerified, photoTint, small',panel)
        self.assertIn('videoVerified, videoTint, small',panel)
        self.assertGreaterEqual(panel.count('color = tint'),3)

    def test_both_gallery_and_cloud_use_in_app_player(self):
        viewer=(JAVA/"ui/screens/ViewerScreen.kt").read_text()
        player=(JAVA/"ui/screens/MediaVideoPlayer.kt").read_text()
        self.assertIn('playingVideo = m',viewer)
        self.assertIn('MediaVideoPlayer(video)',viewer)
        self.assertNotIn('Intent(Intent.ACTION_VIEW)',viewer)
        for required in ['ExoPlayer.Builder','cloudFullPath','Lifecycle.Event.ON_STOP','player.release()','mx_video_error']:
            self.assertIn(required,player)

    def test_build_contains_no_ai_runtime_models_or_ocr(self):
        gradle=(ROOT/'app/build.gradle.kts').read_text()
        self.assertNotIn('onnxruntime',gradle)
        self.assertNotIn('mlkit-text-recognition',gradle)
        self.assertNotIn('mlkit-face-detection',gradle)
        self.assertFalse((JAVA/'ai').exists())
        self.assertFalse((ROOT/'app/src/main/assets/ai').exists())
        self.assertFalse((JAVA/'ui/screens/AiSettingsScreen.kt').exists())
        for locale in ('values','values-bn'):
            strings=(ROOT/f'app/src/main/res/{locale}/media_ai.xml').read_text()
            self.assertNotIn('mx_ai',strings)
            self.assertNotIn('mx_ocr',strings)
        search=(JAVA/'ui/screens/SearchScreen.kt').read_text()
        self.assertNotIn('AiManager',search)

    def test_cloud_previews_are_gated_by_connection_policy(self):
        policy=(JAVA/'data/NetworkPolicy.kt').read_text()
        self.assertIn('cloud_on_metered',policy)
        self.assertIn('NET_CAPABILITY_NOT_METERED',policy)
        self.assertIn('fun allowsCloudFetch',policy)
        fetcher=(JAVA/'cloud/CloudUri.kt').read_text()
        self.assertIn('NetworkPolicy.allowsCloudFetch(app)',fetcher)
        # Default must be data-saving: nothing fetched on a metered connection until opted in.
        self.assertIn('getBoolean("cloud_on_metered", false)',policy)

    def test_caches_are_bounded_and_clearable(self):
        app=(JAVA/'MemoraApplication.kt').read_text()
        self.assertIn('maxSizePercent(0.12)',app)
        self.assertIn('NetworkPolicy.imageDiskBytes',app)
        self.assertIn('maxSizeBytes',app)
        policy=(JAVA/'data/NetworkPolicy.kt').read_text()
        self.assertIn('fun clearImageCache',policy)
        settings=(JAVA/'ui/screens/Screens.kt').read_text()
        self.assertIn('R.string.set_clear_cache',settings)
        self.assertIn('R.string.set_cache_limit',settings)
        self.assertIn('R.string.set_cloud_mobile',settings)

    def test_sign_in_redesign_keeps_the_backend_untouched(self):
        screen=(JAVA/'ui/screens/TelegramScreen.kt').read_text()
        for marker in ('StepDots','StepShell','PasteButton','tg_show_password','tg_paste'):
            self.assertIn(marker,screen)
        # UI only: the auth entry points are called exactly as the retained 0.9.2 flow requires.
        for call in ('tg.saveCredentials(id, hash)','tg.submitPhone(','tg.submitCode(','tg.submitPassword('):
            self.assertIn(call,screen)
        client=(JAVA/'telegram/TelegramClient.kt').read_text()
        self.assertNotIn('OptimizeStorage',client)

    def test_selection_count_is_compact_and_viewer_controls_bottom_only(self):
        badge=(JAVA/'ui/screens/SelectionCount.kt').read_text()
        self.assertIn('typography.labelLarge',badge)
        self.assertIn('maxLines=1',badge)
        viewer=(JAVA/'ui/screens/ViewerScreen.kt').read_text()
        self.assertNotIn('// top bar',viewer)
        self.assertIn('// One narrow bottom toolbar',viewer)



if __name__ == "__main__":
    unittest.main(verbosity=2)
