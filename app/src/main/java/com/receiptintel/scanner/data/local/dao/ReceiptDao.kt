package com.receiptintel.scanner.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.receiptintel.scanner.data.local.entity.DashboardSummary
import com.receiptintel.scanner.data.local.entity.ReceiptEntity
import com.receiptintel.scanner.data.local.entity.ReceiptWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Update
    suspend fun updateReceipt(receipt: ReceiptEntity)

    @Delete
    suspend fun deleteReceipt(receipt: ReceiptEntity)

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getById(id: Long): ReceiptEntity?

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getWithItems(id: Long): ReceiptWithItems?

    @Transaction
    @Query("SELECT * FROM receipts ORDER BY createdAtEpochMs DESC")
    fun observeAllWithItems(): Flow<List<ReceiptWithItems>>

    @Query("SELECT * FROM receipts ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<ReceiptEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM receipts
        WHERE (:query = '')
           OR receiptNumber LIKE '%' || :query || '%'
           OR sellerName LIKE '%' || :query || '%'
           OR sellerTin LIKE '%' || :query || '%'
           OR buyerTin LIKE '%' || :query || '%'
        ORDER BY createdAtEpochMs DESC
        """
    )
    fun search(query: String): Flow<List<ReceiptWithItems>>

    @Query(
        """
        SELECT COUNT(*) AS totalReceipts,
               SUM(total) AS totalSales,
               SUM(vatAmount) AS totalVat
        FROM receipts
        """
    )
    fun observeDashboardSummary(): Flow<DashboardSummary>

    /** Finds existing receipts sharing the same dedupe hash, used for duplicate detection. */
    @Query("SELECT * FROM receipts WHERE contentHash = :hash AND id != :excludeId")
    suspend fun findByContentHash(hash: String, excludeId: Long = -1): List<ReceiptEntity>

    @Query("SELECT * FROM receipts WHERE hasErrors = 1 ORDER BY createdAtEpochMs DESC")
    fun observeFailedReceipts(): Flow<List<ReceiptEntity>>

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM receipts")
    suspend fun count(): Int
}
