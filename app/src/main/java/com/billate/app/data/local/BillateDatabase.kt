package com.billate.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BillTransactionEntity::class, LineItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BillateDatabase : RoomDatabase() {
    abstract fun billDao(): BillDao
}
