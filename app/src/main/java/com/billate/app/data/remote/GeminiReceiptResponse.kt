package com.billate.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON shape returned by the Gemini receipt extraction prompt.
 * Currency-agnostic: the model reports which currency it detects.
 */
@Serializable
data class GeminiReceiptResponse(
    @SerialName("merchant_name") val merchantName: String = "",
    @SerialName("transaction_date") val transactionDate: String = "",
    @SerialName("transaction_date_raw") val transactionDateRaw: String = "",
    val currency: String = "",
    @SerialName("total_amount") val totalAmount: Long = 0,
    @SerialName("total_amount_raw") val totalAmountRaw: String = "",
    val category: String = "Other",
    @SerialName("line_items") val lineItems: List<GeminiLineItem> = emptyList(),
    val notes: String = "",
)

@Serializable
data class GeminiLineItem(
    val description: String = "",
    val qty: Int = 1,
    val amount: Long = 0,
    @SerialName("amount_raw") val amountRaw: String = "",
)
