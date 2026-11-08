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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal fun stockNotificationsEnabled(context: Context, ownerId: String): Boolean =
    context.getSharedPreferences("stock_notifications", Context.MODE_PRIVATE).getBoolean("enabled.$ownerId", false)

internal fun setStockNotificationsEnabled(context: Context, ownerId: String, enabled: Boolean) {
    context.getSharedPreferences("stock_notifications", Context.MODE_PRIVATE).edit().putBoolean("enabled.$ownerId", enabled).apply()
}

internal fun newStockAlerts(current: Set<String>, previous: Set<String>): Set<String> = current - previous

/** Generic device alert on transition into low stock. Never exposes product or account details. */
class StockReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return Result.success()
        val prefs = applicationContext.getSharedPreferences("stock_notifications", Context.MODE_PRIVATE)
        val store = CashierStore(applicationContext, "")
        return try {
            val low = mutableSetOf<String>()
            store.readableDatabase.rawQuery("SELECT p.owner_id,p.product_id FROM products p JOIN stock s ON s.owner_id=p.owner_id AND s.product_id=p.product_id WHERE p.active=1 AND s.quantity<=p.minimum_stock", null).use { c ->
                while (c.moveToNext()) {
                    val owner = c.getString(0); val id = c.getString(1)
                    if (prefs.getBoolean("enabled.$owner", false)) {
                        val digest = MessageDigest.getInstance("SHA-256").digest((owner + "\n" + id).toByteArray(Charsets.UTF_8))
                        low.add(digest.joinToString("") { "%02x".format(it.toInt() and 0xff) })
                    }
                }
            }
            val previouslyLow = prefs.getStringSet("low_notified", emptySet())?.toSet() ?: emptySet()
            if (newStockAlerts(low, previouslyLow).isNotEmpty()) {
                val channel = "stock_reminders"
                if (Build.VERSION.SDK_INT >= 26) {
                    val system = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    system.createNotificationChannel(NotificationChannel(channel, "Pengingat stok", NotificationManager.IMPORTANCE_DEFAULT))
                    if (system.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return Result.success()
                }
                val intent = PendingIntent.getActivity(applicationContext, 1, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val alert = NotificationCompat.Builder(applicationContext, channel)
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Peringatan stok")
                    .setContentText("Ada barang yang mencapai batas stok. Buka aplikasi untuk memeriksa.")
                    .setAutoCancel(true).setContentIntent(intent).build()
                manager.notify(41913, alert)
            }
            check(prefs.edit().putStringSet("low_notified", low).commit())
            Result.success()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.retry() } finally { store.close() }
    }
}

internal fun scheduleStockReminders(context: Context) {
    WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
        "stock-reminders", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<StockReminderWorker>(15, TimeUnit.MINUTES).build(),
    )
}
