package id.warungmatriks.app.data

import android.database.sqlite.SQLiteDatabase
import java.util.Calendar
import java.util.TimeZone

/** UI hides ordinary sales after 7 x 24h. Monthly cleanup uses the first day's cutoff. */
internal object HistoryRetention {
    const val RETENTION_MS: Long = 7L * 24 * 60 * 60 * 1000
    fun monthStart(now: Long, zone: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(zone).apply {
        timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // A fixed first-of-month cutoff means records that become 7 days old later wait until next month.
    fun clean(db: SQLiteDatabase, now: Long, ownerId: String? = null) {
        val monthStart = monthStart(now)
        val ownerClause = if (ownerId == null) "" else " AND s.owner_id = ?"
        val args = mutableListOf<Any>(monthStart, monthStart - RETENTION_MS, monthStart)
        if (ownerId != null) args.add(ownerId)
        // Unpaid credits are protected. A credit paid since this month's reset waits for next reset.
        val eligible = "SELECT s.id FROM sales s WHERE s.created_at < ? AND s.created_at <= ? AND NOT EXISTS (SELECT 1 FROM credit_payments p WHERE p.sale_id=s.id AND p.owner_id=s.owner_id AND p.created_at >= ?) $ownerClause AND NOT EXISTS (SELECT 1 FROM receivables r WHERE r.sale_id=s.id AND r.owner_id=s.owner_id AND r.amount_remaining>0)"
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM sale_items WHERE sale_id IN ($eligible)", args.toTypedArray())
            db.execSQL("DELETE FROM sales WHERE id IN ($eligible)", args.toTypedArray())
            // Payment detail tied to a removed sale is no longer needed; cash entries remain.
            val orphanOwner = if (ownerId == null) "" else " AND owner_id = ?"
            val orphanArgs = if (ownerId == null) emptyArray<Any>() else arrayOf<Any>(ownerId)
            db.execSQL("DELETE FROM credit_payments WHERE NOT EXISTS (SELECT 1 FROM sales s WHERE s.id=credit_payments.sale_id AND s.owner_id=credit_payments.owner_id)$orphanOwner", orphanArgs)
            if (ownerId == null) db.execSQL("DELETE FROM receivables WHERE amount_remaining <= 0")
            else db.execSQL("DELETE FROM receivables WHERE owner_id = ? AND amount_remaining <= 0", arrayOf(ownerId))
            // Cash ledger and QRIS archive remain separate and are not deleted here.
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
