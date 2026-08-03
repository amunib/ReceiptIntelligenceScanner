package com.receiptintel.scanner.csv

import com.receiptintel.scanner.data.local.entity.ReceiptWithItems
import java.io.File

/**
 * Excel (.xlsx) export.
 *
 * NOT wired to Apache POI by default. Apache POI is a desktop-JVM library:
 * on Android it pulls in java.awt/java.beans transitively, needs manual
 * exclusions, and roughly doubles method count / APK size for a feature most
 * users won't touch daily (CSV already opens fine in Excel). Given this is a
 * mobile app, we recommend a purpose-built lightweight writer instead:
 *
 *   implementation("org.dhatim:fastexcel:0.17.0")
 *
 * fastexcel has no AWT dependency and streams rows, so it scales to your
 * 1000+ receipt requirement without the memory spikes POI's usermodel API is
 * known for on constrained devices.
 *
 * To wire it up:
 *   1. Add the dependency above to app/build.gradle.kts.
 *   2. Replace the body of [export] below with:
 *
 *      Workbook(FileOutputStream(outFile), "Receipt Intelligence Scanner", "1.0").use { wb ->
 *          val ws = wb.newWorksheet("Receipts")
 *          CsvExporter.HEADER.forEachIndexed { col, title -> ws.value(0, col, title) }
 *          var row = 1
 *          receipts.forEach { rwi -> /* same flattening logic as CsvExporter */ }
 *      }
 *
 * Left as a clear extension point rather than silently faked so the export
 * screen can show an honest "coming soon" state (see ExportScreen) instead of
 * claiming to produce a real .xlsx.
 */
object ExcelExporter {

    class NotConfiguredException : Exception(
        "Excel export requires the fastexcel dependency — see ExcelExporter.kt for setup."
    )

    @Suppress("UNUSED_PARAMETER")
    fun export(receipts: List<ReceiptWithItems>, outFile: File): File {
        throw NotConfiguredException()
    }
}
