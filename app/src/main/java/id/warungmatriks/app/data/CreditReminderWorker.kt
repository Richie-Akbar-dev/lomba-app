package id.warungmatriks.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
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
import id.warungmatriks.app.auth.AuthRepository
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Best-effort device notification at or after the selected deadline, never a WhatsApp message. */
class CreditReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return Result.success()
        val auth = AuthRepository(applicationContext)
        val owner = try { auth.currentUser()?.uid } catch (_: Exception) { return Result.retry() }
        if (owner.isNullOrBlank()) return Result.success()
        val store = CashierStore(applicationContext, owner)
        return try {
            val db = store.writableDatabase
            val now = System.currentTimeMillis()
            val due = dueCreditReminderIds(db, owner, now)
            if (due.isNotEmpty()) {
                val channel = "credit_reminders"
                if (Build.VERSION.SDK_INT >= 26) {
                    val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.createNotificationChannel(NotificationChannel(channel, "Pengingat kasbon", NotificationManager.IMPORTANCE_DEFAULT))
                    if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return Result.success()
                }
                // Don't notify or consume reminders for a session that has since changed.
                if (auth.currentUser()?.uid != owner) return Result.success()
                val intent = Intent(applicationContext, MainActivity::class.java)
                val pending = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(applicationContext, channel)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Pengingat kasbon")
                    .setContentText("Ada kasbon yang telah jatuh tempo. Buka aplikasi untuk memeriksa.")
                    .setAutoCancel(true).setContentIntent(pending).build()
                NotificationManagerCompat.from(applicationContext).notify(CREDIT_REMINDER_NOTIFICATION_ID, notification)
                if (auth.currentUser()?.uid != owner) {
                    NotificationManagerCompat.from(applicationContext).cancel(CREDIT_REMINDER_NOTIFICATION_ID)
                    return Result.success()
                }
                markCreditRemindersNotified(db, owner, due, now)
            }
            Result.success()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.retry() } finally { store.close() }
    }
}

internal const val CREDIT_REMINDER_NOTIFICATION_ID = 41912

internal fun dueCreditReminderIds(db: SQLiteDatabase, ownerId: String, now: Long): List<String> {
    require(ownerId.isNotBlank())
    val due = mutableListOf<String>()
    db.rawQuery("SELECT sale_id FROM receivables WHERE owner_id=? AND amount_remaining>0 AND remind=1 AND notified=0 AND due_at IS NOT NULL AND due_at<=?", arrayOf(ownerId, now.toString())).use { c ->
        while (c.moveToNext()) due.add(c.getString(0))
    }
    return due
}

internal fun markCreditRemindersNotified(db: SQLiteDatabase, ownerId: String, saleIds: List<String>, now: Long) {
    require(ownerId.isNotBlank())
    db.beginTransaction()
    try {
        saleIds.forEach { id ->
            db.execSQL("UPDATE receivables SET notified=1 WHERE owner_id=? AND sale_id=? AND amount_remaining>0 AND remind=1 AND notified=0 AND due_at IS NOT NULL AND due_at<=?", arrayOf(ownerId, id, now))
        }
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}

internal fun scheduleCreditReminders(context: Context) {
    WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
        "credit-reminders", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<CreditReminderWorker>(15, TimeUnit.MINUTES).build(),
    )
}
