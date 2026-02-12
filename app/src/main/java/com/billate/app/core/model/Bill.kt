package com.billate.app.core.model

/**
 * Optional bill / receipt data attached to a [Transaction].
 *
 * Contains merchant info, line-item details, adjustments, and the original receipt image.
 *
 * ## Adjustments
 * Service charge, discount, and tax are **only populated when explicitly listed on the receipt**.
 * If the receipt only shows final item prices (tax-inclusive), these fields remain null.
 * - [serviceCharge] — positive amount (gratuity, delivery fee, etc.)
 * - [discount] — **negative** amount (coupon, promotion, etc.)
 * - [tax] — positive amount; may represent multiple tax lines summed together
 *
 * ## Confidence
 * [extractionConfidence] indicates how reliable the OCR extraction was (0.0–1.0).
 * Values below 0.3 trigger a manual-review prompt.
 */
data class Bill(
    val merchantName: String = "",
    val transactionDateRaw: String = "",
    val totalAmountRaw: String = "",
    val lineItems: List<LineItem> = emptyList(),
    // Adjustments — only present when the receipt explicitly lists them
    val serviceCharge: Money? = null,
    val discount: Money? = null,
    val tax: Money? = null,
    val notes: String = "",
    val imageUri: String? = null,
    val extractionConfidence: Float = 1.0f,
)
