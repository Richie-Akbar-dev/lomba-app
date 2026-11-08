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
import java.util.concurrent.TimeUnit

class HistoryCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = CashierStore(applicationContext, "")
        try {
            HistoryRetention.clean(store.writableDatabase, System.currentTimeMillis())
            Result.success()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.retry() } finally { store.close() }
    }
}

internal fun scheduleHistoryCleanup(context: Context) {
    val manager = WorkManager.getInstance(context.applicationContext)
    manager.enqueueUniquePeriodicWork("sales-history-retention-periodic", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<HistoryCleanupWorker>(12, TimeUnit.HOURS).build())
    manager.enqueueUniqueWork("sales-history-retention-start", ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<HistoryCleanupWorker>().build())
}
