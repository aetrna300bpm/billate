package com.billate.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON shape returned by the Gemini receipt extraction prompt.
 *
 * Currency-agnostic: the model reports which currency it detects.
 * Adjustments (service charge, discount, tax) are **only present when the receipt
 * explicitly lists them**. If the receipt just shows final item prices, these are null/absent.
 */
@Serializable
data class GeminiReceiptResponse(
    @SerialName("merchant_name") val merchantName: String = "",
    @SerialName("transaction_date") val transactionDate: String = "",
    @SerialName("transaction_date_raw") val transactionDateRaw: String = "",
    val currency: String = "",
    @SerialName("final_total") val finalTotal: Long = 0,
    @SerialName("total_amount_raw") val totalAmountRaw: String = "",
    val category: String = "Other",
    @SerialName("line_items") val lineItems: List<GeminiLineItem> = emptyList(),
    val adjustments: GeminiAdjustments? = null,
    val notes: String = "",
    val confidence: Float = 1.0f,
)

@Serializable
data class GeminiLineItem(
    val description: String = "",
    val qty: Int = 1,
    @SerialName("unit_price") val unitPrice: Long? = null,
    val amount: Long = 0,
    @SerialName("amount_raw") val amountRaw: String = "",
)

/**
 * Optional receipt adjustments. Each field is null when the receipt does not list it.
 */
@Serializable
data class GeminiAdjustments(
    @SerialName("service_charge") val serviceCharge: GeminiAdjustmentItem? = null,
    val discount: GeminiAdjustmentItem? = null,
    val tax: GeminiAdjustmentItem? = null,
)

@Serializable
data class GeminiAdjustmentItem(
    val amount: Long = 0,
    @SerialName("amount_raw") val amountRaw: String = "",
)
