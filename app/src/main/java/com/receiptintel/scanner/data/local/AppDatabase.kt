package com.receiptintel.scanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.receiptintel.scanner.data.local.dao.ItemDao
import com.receiptintel.scanner.data.local.dao.ReceiptDao
import com.receiptintel.scanner.data.local.entity.ItemEntity
import com.receiptintel.scanner.data.local.entity.ReceiptEntity

@Database(
    entities = [ReceiptEntity::class, ItemEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun receiptDao(): ReceiptDao
    abstract fun itemDao(): ItemDao

    companion object {
        private const val DB_NAME = "receipt_scanner.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                // Large-batch inserts (1000+ receipts) benefit from WAL's better
                // concurrent read/write throughput vs the default journal mode.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
