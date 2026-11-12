package id.warungmatriks.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReportPdfCleanupTest {
    @Test fun removesOnlyExpiredReportPdfsFromPrivateShareCache() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "pdf-cleanup-test-${UUID.randomUUID()}").apply { mkdirs() }
        val folder = File(root, "report_share").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val old = File(folder, "Laporan-old.pdf").apply { writeText("test"); assertTrue(setLastModified(now - 25L * 60 * 60 * 1000)) }
        val recent = File(folder, "Laporan-recent.pdf").apply { writeText("test") }
        val other = File(folder, "other.pdf").apply { writeText("test"); assertTrue(setLastModified(now - 25L * 60 * 60 * 1000)) }
        try {
            assertTrue(cleanOldReportPdfsIn(folder, now))
            assertFalse(old.exists()); assertTrue(recent.exists()); assertTrue(other.exists())
        } finally { root.deleteRecursively() }
    }
}
