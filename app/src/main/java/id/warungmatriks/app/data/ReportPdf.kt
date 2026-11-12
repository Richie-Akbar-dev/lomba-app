package id.warungmatriks.app.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal data class ExportCredit(val credit: CreditDetail, val items: List<SaleItemSnapshot>)

/** Produces a private temporary PDF. Sharing grants URI read access only to the receiving app. */
internal suspend fun makeReportPdf(context: Context, report: ReportSnapshot, shopName: String, credits: List<ExportCredit>): File = withContext(Dispatchers.IO) {
    val folder = File(context.cacheDir, "report_share").apply { mkdirs() }
    cleanOldReportPdfs(context)
    val file = File(folder, "Laporan-${UUID.randomUUID()}.pdf")
    val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
    val lines = mutableListOf(
        "LAPORAN WARUNG MATRIKS", "Toko: $shopName", "Periode: ${fmt.format(Date(report.start))} - ${fmt.format(Date(report.end - 1))}",
        "Data lokal; data lama yang sudah terhapus sebelum pembaruan tidak dapat dipulihkan.", "",
        "Uang masuk (nilai penjualan): ${rupiah(report.sales)}", "Jumlah transaksi: ${report.count}",
        "QRIS dicatat: ${rupiah(report.qris)}", "Kas masuk: ${rupiah(report.cashIn)}", "Kas keluar: ${rupiah(report.cashOut)}",
        "Tunai penjualan: ${rupiah(report.salesCash)}", "Angsuran kasbon diterima: ${rupiah(report.installmentCash)}", "Modal awal / setoran: ${rupiah(report.deposits)}",
        "Modal barang terjual: ${report.modal?.let(::rupiah) ?: "Tidak lengkap"}", "Perkiraan laba kotor: ${report.profit?.let(::rupiah) ?: "Tidak lengkap"}",
        "Potensi penjualan stok aktif: ${rupiah(report.activeStockValue)}", "",
        "PENJUALAN VS SELURUH KAS KELUAR (7 hari terakhir pada bulan ini)",
    )
    report.days.forEach { lines.add("${it.label}: Penjualan ${rupiah(it.sales)} | Kas keluar ${rupiah(it.cashOut)}") }
    lines.add(""); lines.add("KATEGORI BERDASARKAN PERKIRAAN LABA KOTOR")
    if (report.categories.isEmpty()) lines.add("Belum ada data kategori lengkap.")
    report.categories.forEach { lines.add("${it.name}: ${it.profit?.let(::rupiah) ?: "Modal belum lengkap"}") }
    if (report.unknownCategory > 0) lines.add("${report.unknownCategory} pcs data lama tidak memiliki kategori saat transaksi.")
    lines.add(""); lines.add("PRODUK PALING LARIS")
    report.bestProducts.forEachIndexed { index, item -> lines.add("${index + 1}. ${item.name}: ${item.quantity} pcs") }
    lines.add("Pola transaksi: 06.00-17.59 ${report.morning}; 18.00-05.59 ${report.evening} transaksi")
    lines.add(""); lines.add("STOK BARANG")
    report.inventory.forEach { lines.add("${it.name}: ${it.stock} pcs | ${rupiah(it.price)} | ${if (it.active) "aktif" else "nonaktif"}") }
    lines.add(""); lines.add("KASBON BELUM LUNAS - DATA PELANGGAN")
    lines.add("Total sisa kasbon: ${rupiah(credits.sumOf { it.credit.remaining.toLong() })}")
    credits.forEach { entry ->
        val c = entry.credit
        lines.add("Pelanggan: ${c.customer} | WhatsApp: ${c.phone?.let { "+$it" } ?: "tidak tersedia"}")
        lines.add("Transaksi: ${fmt.format(Date(c.createdAt))} | Total: ${rupiah(c.total)} | Bayar awal: ${rupiah(c.received)} | Sisa: ${rupiah(c.remaining)}")
        c.dueAt?.let { lines.add("Batas pelunasan: ${fmt.format(Date(it))}") }
        if (entry.items.isEmpty()) lines.add("Rincian barang tidak tersedia.")
        entry.items.forEach { lines.add("- ${it.productName}: ${it.quantity} pcs x ${rupiah(it.price)}") }
        lines.add("")
    }
    if (report.missingCost > 0 || report.missingItems > 0) lines.add("Modal atau rincian barang tidak lengkap. Laba dan modal total tidak dapat dihitung penuh.")
    lines.add("Penjualan dan kas masuk berbeda. QRIS dicatat sebagai penjualan; periksa pembayaran di penyedia.")
    val pdf = PdfDocument()
    try {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f }
        var pageNo = 0
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNo).create())
        var y = 48f
        fun nextPage() { pdf.finishPage(page); page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNo).create()); y = 48f }
        fun draw(line: String) {
            if (line.isEmpty()) { y += 10f; return }
            var remaining = line.replace('\n', ' ').replace('\r', ' ')
            while (remaining.isNotEmpty()) {
                if (y > 800f) nextPage()
                var len = paint.breakText(remaining, true, 505f, null).coerceAtLeast(1)
                if (len < remaining.length) { val space = remaining.lastIndexOf(' ', len - 1); if (space > 0) len = space }
                page.canvas.drawText(remaining.substring(0, len), 45f, y, paint)
                remaining = remaining.substring(len).trimStart()
                y += 15f
            }
        }
        lines.forEach(::draw)
        pdf.finishPage(page)
        file.outputStream().use { pdf.writeTo(it) }
    } catch (e: Exception) { file.delete(); throw e } finally { pdf.close() }
    file
}
