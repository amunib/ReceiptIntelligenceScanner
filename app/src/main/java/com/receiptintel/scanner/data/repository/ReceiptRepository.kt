package com.receiptintel.scanner.data.repository

import com.receiptintel.scanner.data.local.dao.ItemDao
import com.receiptintel.scanner.data.local.dao.ReceiptDao
import com.receiptintel.scanner.data.local.entity.DashboardSummary
import com.receiptintel.scanner.data.local.entity.ItemEntity
import com.receiptintel.scanner.data.local.entity.ReceiptEntity
import com.receiptintel.scanner.data.local.entity.ReceiptWithItems
import com.receiptintel.scanner.ocr.ReceiptImageProcessor
import com.receiptintel.scanner.ocr.TextRecognitionEngine
import com.receiptintel.scanner.parser.DuplicateHasher
import com.receiptintel.scanner.parser.ParsedReceipt
import com.receiptintel.scanner.parser.ReceiptParser
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point the ViewModels use to turn raw input (camera bitmap,
 * imported image, TXT content) into a stored [ReceiptEntity], and to query
 * back out for the Dashboard/History/Export screens.
 *
 * Kept OCR-engine and parser as plain constructor dependencies (no DI
 * framework — see [com.receiptintel.scanner.ReceiptScannerApp]) so this
 * class is trivially unit-testable by passing fakes.
 */
class ReceiptRepository(
    private val receiptDao: ReceiptDao,
    private val itemDao: ItemDao,
    private val ocrEngine: TextRecognitionEngine
) {

    fun observeDashboardSummary(): Flow<DashboardSummary> = receiptDao.observeDashboardSummary()

    fun observeRecent(limit: Int = 10): Flow<List<ReceiptEntity>> = receiptDao.observeRecent(limit)

    fun observeAll(): Flow<List<ReceiptWithItems>> = receiptDao.observeAllWithItems()

    fun search(query: String): Flow<List<ReceiptWithItems>> = receiptDao.search(query)

    fun observeFailed(): Flow<List<ReceiptEntity>> = receiptDao.observeFailedReceipts()

    suspend fun delete(receipt: ReceiptEntity) = receiptDao.deleteReceipt(receipt)

    /**
     * Processes a single [ReceiptSource] end-to-end: OCR (if it's an image),
     * regex parsing, duplicate check against existing data, then persistence.
     * Never throws for "bad" receipt content — parse failures are captured
     * as a stored, flagged receipt so the batch can continue; only genuine
     * I/O/OCR engine exceptions surface as [ProcessResult.Failure].
     */
    suspend fun processOne(source: ReceiptSource): ProcessResult {
        return try {
            val rawText = when (source) {
                is ReceiptSource.FromImage -> {
                    val enhanced = ReceiptImageProcessor.enhanceForOcr(
                        ReceiptImageProcessor.autoCrop(source.bitmap)
                    )
                    ocrEngine.recognize(enhanced)
                }
                is ReceiptSource.FromText -> source.text
            }

            val parsed = ReceiptParser.parse(rawText)
            val hash = DuplicateHasher.hash(parsed)
            val existingDuplicates = receiptDao.findByContentHash(hash)
            val isDuplicate = existingDuplicates.isNotEmpty()

            val entity = parsed.toEntity(
                sourceType = source.sourceType,
                sourceFilePath = source.sourceFilePath,
                contentHash = hash,
                duplicateOfId = existingDuplicates.firstOrNull()?.id
            )

            val id = receiptDao.insertReceipt(entity)
            if (parsed.items.isNotEmpty()) {
                itemDao.insertAll(parsed.items.map { it.toEntity(id) })
            }

            ProcessResult.Success(id, isDuplicate)
        } catch (e: Exception) {
            ProcessResult.Failure(e.message ?: e::class.java.simpleName)
        }
    }
}

private fun ParsedReceipt.toEntity(
    sourceType: String,
    sourceFilePath: String?,
    contentHash: String,
    duplicateOfId: Long?
) = ReceiptEntity(
    receiptNumber = receiptNumber,
    date = date,
    time = time,
    sellerTin = sellerTin,
    sellerName = sellerName,
    sellerAddress = sellerAddress,
    sellerPhone = sellerPhone,
    buyerTin = buyerTin,
    taxableAmount = taxableAmount,
    vatPercentage = vatPercentage,
    vatAmount = vatAmount,
    total = total,
    paymentMethod = paymentMethod,
    sourceFilePath = sourceFilePath,
    sourceType = sourceType,
    rawText = rawText,
    parseConfidence = confidence,
    hasErrors = hasErrors,
    errorNotes = missingFields.takeIf { it.isNotEmpty() }?.joinToString(", ") { "missing: $it" },
    contentHash = contentHash,
    isDuplicateOfId = duplicateOfId
)

private fun com.receiptintel.scanner.parser.ParsedReceiptItem.toEntity(receiptId: Long) = ItemEntity(
    receiptId = receiptId,
    itemName = name,
    quantity = quantity,
    unitPrice = unitPrice,
    subtotal = subtotal
)
