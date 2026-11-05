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
        self.assertNotIn("android.permission.MANAGE_EXTERNAL_STORAGE", names)

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
