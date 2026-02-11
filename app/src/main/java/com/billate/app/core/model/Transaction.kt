package com.billate.app.core.model

/**
 * Primary domain entity.
 *
 * Every financial record is a Transaction. If the transaction came from a
 * receipt scan, it will have a non-null [bill]. Manual entries leave [bill] null.
 *
 * @param id        Auto-generated database primary key.
 * @param timestamp Epoch millis when the transaction occurred (from receipt date or user input).
 * @param amount    The final total in the user's currency.
 * @param category  Spending category.
 * @param bill      Optional receipt / bill data.
 * @param note      Free-text note for manual entries.
 * @param createdAt Epoch millis when the record was created in the app.
 */
data class Transaction(
    val id: Long = 0,
    val timestamp: Long,
    val amount: Money,
    val category: Category,
    val bill: Bill? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
