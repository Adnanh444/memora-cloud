package com.adnan.memora.backup

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UploadWorkspaceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun identicalNamesHaveIndependentFilesAndCleanup() {
        val root = temp.newFolder()
        val first = UploadWorkspace.create(root, "IMG_0001.jpg")
        val second = UploadWorkspace.create(root, "IMG_0001.jpg")
        assertNotEquals(first.original.canonicalPath, second.original.canonicalPath)
        assertEquals("IMG_0001.jpg", first.original.name)
        first.original.writeText("first media")
        second.original.writeText("second media")
        first.close()
        assertFalse(first.directory.exists())
        assertEquals("second media", second.original.readText())
        second.close()
        assertEquals(0, root.listFiles()!!.size)
    }

    @Test fun originalCalledPreviewNeverCollidesWithThumbnail() {
        UploadWorkspace.create(temp.newFolder(), "preview.jpg").use { attempt ->
            attempt.original.writeText("original")
            attempt.thumbnail.writeText("thumbnail")
            assertEquals("original", attempt.original.readText())
            assertNotEquals(attempt.original.canonicalPath, attempt.thumbnail.canonicalPath)
        }
    }

    @Test fun unsafeAndEmptyNamesStayInsideAttempt() {
        val root = temp.newFolder()
        for (name in listOf("../../outside.jpg", "", ".", "..", "a\\b:bad?.jpg", "\u0000")) {
            UploadWorkspace.create(root, name).use { attempt ->
                assertTrue(attempt.original.canonicalPath.startsWith(attempt.directory.canonicalPath + File.separator))
                assertTrue(attempt.original.name.isNotBlank())
                assertFalse(attempt.original.name.contains('/'))
            }
        }
    }

    @Test fun longNamesAreLimitedAndCleanupIsIdempotent() {
        val attempt = UploadWorkspace.create(temp.newFolder(), "x".repeat(300))
        assertEquals(120, attempt.original.name.length)
        attempt.close()
        attempt.close()
        assertFalse(attempt.directory.exists())
    }
}
