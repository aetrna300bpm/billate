package com.billate.app.model

data class BillTransaction(
    val id: Long = 0,
    val merchantName: String,
    val transactionDate: String,       // YYYY-MM-DD
    val transactionDateRaw: String,
    val currency: String = "VND",
    val totalAmountVnd: Long,
    val totalAmountRaw: String,
    val category: Category,
    val lineItems: List<LineItem>,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class LineItem(
    val id: Long = 0,
    val description: String,
    val qty: Int = 1,
    val amountVnd: Long,
    val amountRaw: String,
)
