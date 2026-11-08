package id.warungmatriks.app.data

import android.database.sqlite.SQLiteDatabase

/** Seven 24-hour periods on the device clock. Unpaid debts keep their sale and snapshots. */
internal object HistoryRetention {
    const val RETENTION_MS: Long = 7L * 24 * 60 * 60 * 1000

    // ownerId == null is used by the device-wide background worker.
    fun clean(db: SQLiteDatabase, now: Long, ownerId: String? = null) {
        val ownerClause = if (ownerId == null) "" else " AND s.owner_id = ?"
        val cutoff = now - RETENTION_MS
        val args = if (ownerId == null) arrayOf<Any>(cutoff) else arrayOf<Any>(cutoff, ownerId)
        val eligible = "SELECT s.id FROM sales s WHERE s.created_at <= ?$ownerClause AND NOT EXISTS (SELECT 1 FROM receivables r WHERE r.sale_id=s.id AND r.owner_id=s.owner_id AND r.amount_remaining>0)"
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM sale_items WHERE sale_id IN ($eligible)", args)
            db.execSQL("DELETE FROM sales WHERE id IN ($eligible)", args)
            // Zero-balance credit entries can go immediately; payment features must set it to zero first.
            if (ownerId == null) db.execSQL("DELETE FROM receivables WHERE amount_remaining <= 0")
            else db.execSQL("DELETE FROM receivables WHERE owner_id = ? AND amount_remaining <= 0", arrayOf(ownerId))
            // Cash movements are separate cash-ledger records, not disposable sales-history details.
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
