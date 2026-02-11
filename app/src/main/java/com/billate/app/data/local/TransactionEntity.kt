package com.billate.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val amountMinor: Long,
    val currency: String,
    val category: String,
    val note: String = "",
    // Embedded bill fields (null when manual entry)
    val merchantName: String? = null,
    val transactionDateRaw: String? = null,
    val totalAmountRaw: String? = null,
    val billNotes: String? = null,
    val billImageUri: String? = null,
    val createdAt: Long,
)
