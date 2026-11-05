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

    def test_telegram_engines_and_screens_match_delivered_0_9_2(self):
        baseline = json.loads((ROOT / "docs/verification/telegram-0.9.2-baseline.json").read_text())
        self.assertEqual(len(baseline), 9)
        for path, expected in baseline.items():
            with self.subTest(path=path):
                self.assertEqual(hashlib.sha256((ROOT / path).read_bytes()).hexdigest(), expected)

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


if __name__ == "__main__":
    unittest.main(verbosity=2)
