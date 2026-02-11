package com.billate.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bill_transactions")
data class BillTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantName: String,
    val transactionDate: String,
    val transactionDateRaw: String,
    val currency: String,
    val totalAmountVnd: Long,
    val totalAmountRaw: String,
    val category: String,
    val notes: String,
    val createdAt: Long,
)
