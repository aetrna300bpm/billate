package com.billate.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiReceiptResponse(
    @SerialName("merchant_name") val merchantName: String = "",
    @SerialName("transaction_date") val transactionDate: String = "",
    @SerialName("transaction_date_raw") val transactionDateRaw: String = "",
    val currency: String = "VND",
    @SerialName("total_amount_vnd") val totalAmountVnd: Long = 0,
    @SerialName("total_amount_raw") val totalAmountRaw: String = "",
    val category: String = "Other",
    @SerialName("line_items") val lineItems: List<GeminiLineItem> = emptyList(),
    val notes: String = "",
)

@Serializable
data class GeminiLineItem(
    val description: String = "",
    val qty: Int = 1,
    @SerialName("amount_vnd") val amountVnd: Long = 0,
    @SerialName("amount_raw") val amountRaw: String = "",
)
