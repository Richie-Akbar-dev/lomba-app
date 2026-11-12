package id.warungmatriks.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private const val REPORT_CACHE_MAX_AGE = 24L * 60 * 60 * 1000

/** Keep recent files available for apps that read a shared URI asynchronously. */
internal fun cleanOldReportPdfs(context: Context, now: Long = System.currentTimeMillis()): Boolean =
    cleanOldReportPdfsIn(File(context.cacheDir, "report_share"), now)

internal fun cleanOldReportPdfsIn(folder: File, now: Long): Boolean {
    if (!folder.exists()) return true
    val files = folder.listFiles() ?: return false
    var ok = true
    files.filter { it.isFile && it.name.startsWith("Laporan-") && it.name.endsWith(".pdf") }
        .filter { it.lastModified() > 0 && it.lastModified() <= now - REPORT_CACHE_MAX_AGE }
        .forEach { if (!it.delete() && it.exists()) ok = false }
    return ok
}

class ReportPdfCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (cleanOldReportPdfs(applicationContext)) Result.success() else Result.retry()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.retry() }
    }
}

internal fun scheduleReportPdfCleanup(context: Context) {
    val manager = WorkManager.getInstance(context.applicationContext)
    manager.enqueueUniqueWork("report-pdf-cleanup-start", ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<ReportPdfCleanupWorker>().build())
    manager.enqueueUniquePeriodicWork("report-pdf-cleanup-periodic", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<ReportPdfCleanupWorker>(12, TimeUnit.HOURS).build())
}
