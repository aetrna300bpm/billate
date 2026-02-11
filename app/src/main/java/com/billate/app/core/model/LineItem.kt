package com.billate.app.core.model

/**
 * A single line item extracted from a receipt / bill.
 */
data class LineItem(
    val id: Long = 0,
    val description: String,
    val qty: Int = 1,
    val amount: Money,
    val amountRaw: String = "",
)
