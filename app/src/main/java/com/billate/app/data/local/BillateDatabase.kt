package com.billate.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TransactionEntity::class, LineItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class BillateDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
