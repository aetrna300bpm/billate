package com.billate.app.core.model

/**
 * Optional bill / receipt data attached to a [Transaction].
 *
 * Contains merchant info, line-item details, and the original receipt image.
 */
data class Bill(
    val merchantName: String = "",
    val transactionDateRaw: String = "",
    val totalAmountRaw: String = "",
    val lineItems: List<LineItem> = emptyList(),
    val notes: String = "",
    val imageUri: String? = null,
)
