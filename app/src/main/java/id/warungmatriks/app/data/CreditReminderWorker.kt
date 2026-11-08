package id.warungmatriks.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.warungmatriks.app.MainActivity
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Best-effort device notification at or after the selected deadline, never a WhatsApp message. */
class CreditReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return Result.success()
        val store = CashierStore(applicationContext, "")
        return try {
            val db = store.writableDatabase
            val due = mutableListOf<Pair<String, String>>()
            db.rawQuery("SELECT owner_id,sale_id FROM receivables WHERE amount_remaining>0 AND remind=1 AND notified=0 AND due_at IS NOT NULL AND due_at<=?", arrayOf(System.currentTimeMillis().toString())).use { c ->
                while (c.moveToNext()) due.add(c.getString(0) to c.getString(1))
            }
            if (due.isNotEmpty()) {
                val channel = "credit_reminders"
                if (Build.VERSION.SDK_INT >= 26) {
                    val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.createNotificationChannel(NotificationChannel(channel, "Pengingat kasbon", NotificationManager.IMPORTANCE_DEFAULT))
                }
                val intent = Intent(applicationContext, MainActivity::class.java)
                val pending = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(applicationContext, channel)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Pengingat kasbon")
                    .setContentText("Ada kasbon yang telah jatuh tempo. Buka aplikasi untuk memeriksa.")
                    .setAutoCancel(true).setContentIntent(pending).build()
                NotificationManagerCompat.from(applicationContext).notify(41912, notification)
                db.beginTransaction()
                try {
                    due.forEach { (owner, id) -> db.execSQL("UPDATE receivables SET notified=1 WHERE owner_id=? AND sale_id=? AND amount_remaining>0", arrayOf(owner, id)) }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            Result.success()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.retry() } finally { store.close() }
    }
}

internal fun scheduleCreditReminders(context: Context) {
    WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
        "credit-reminders", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<CreditReminderWorker>(15, TimeUnit.MINUTES).build(),
    )
}
