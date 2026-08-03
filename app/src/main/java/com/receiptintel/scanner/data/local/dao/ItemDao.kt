package com.receiptintel.scanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.receiptintel.scanner.data.local.entity.ItemEntity

@Dao
interface ItemDao {

    @Insert
    suspend fun insertAll(items: List<ItemEntity>): List<Long>

    @Query("SELECT * FROM items WHERE receiptId = :receiptId")
    suspend fun getForReceipt(receiptId: Long): List<ItemEntity>

    @Query("DELETE FROM items WHERE receiptId = :receiptId")
    suspend fun deleteForReceipt(receiptId: Long)
}
