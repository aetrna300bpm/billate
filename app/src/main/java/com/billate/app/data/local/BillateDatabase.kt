package com.billate.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, LineItemEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class BillateDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        /**
         * Migration 3 → 4: Add sealed-class columns (`type`, `name`, `recipientName`).
         *
         * Existing rows:
         *  - merchantName IS NOT NULL  → type = "receipt", name = merchantName
         *  - merchantName IS NULL      → type = "manual",  name = note (or empty)
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN type TEXT NOT NULL DEFAULT 'receipt'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN name TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN recipientName TEXT")
                // Populate name from best available source
                db.execSQL("UPDATE transactions SET name = COALESCE(merchantName, note, '')")
                // Rows without a merchant were manually created
                db.execSQL("UPDATE transactions SET type = 'manual' WHERE merchantName IS NULL")
            }
        }
    }
}
