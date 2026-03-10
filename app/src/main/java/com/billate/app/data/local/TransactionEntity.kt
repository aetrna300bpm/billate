package com.billate.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = "receipt",           // "receipt", "wire_transfer", "manual"
    val timestamp: Long,
    val amountMinor: Long,
    val currency: String,
    val category: String,
    val name: String = "",                  // Universal display label
    val note: String = "",
    // Receipt fields (null when not a receipt)
    val merchantName: String? = null,       // kept as column name for migration compat; maps to merchantNameRaw
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
    // Wire transfer fields (null when not a wire transfer)
    val recipientName: String? = null,
    val createdAt: Long,
)
