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
    // Bill adjustments — only when explicitly listed on the receipt
    val serviceChargeMinor: Long? = null,
    val serviceChargeCurrency: String? = null,
    val discountMinor: Long? = null,
    val discountCurrency: String? = null,
    val taxMinor: Long? = null,
    val taxCurrency: String? = null,
    val extractionConfidence: Float = 1.0f,
    val createdAt: Long,
)
