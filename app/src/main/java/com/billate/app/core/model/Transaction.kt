package com.billate.app.core.model

/**
 * Primary domain entity — sealed class hierarchy.
 *
 * Every financial record is a [Transaction]. The three concrete subtypes
 * represent the origin of the transaction:
 * - [Receipt] — created by scanning a receipt image (has line items, adjustments, image)
 * - [WireTransfer] — created by scanning a bank transfer screenshot (has recipient, image)
 * - [Manual] — created by the user manually (no extras)
 *
 * Common fields live in the sealed base: [id], [timestamp], [amount], [category],
 * [name], [note], [createdAt].
 *
 * [name] is the universal "what did I spend on?" label — always visible in the
 * transaction log and always editable by the user.
 */
sealed class Transaction {
    abstract val id: Long
    abstract val timestamp: Long
    abstract val amount: Money
    abstract val category: Category
    /** User-facing display label. Editable regardless of type. */
    abstract val name: String
    abstract val note: String
    abstract val createdAt: Long

    /**
     * Scanned receipt with line items, adjustments, and the original image.
     *
     * [name] is initialized from the extracted merchant name but can be edited.
     * [merchantNameRaw] preserves the original OCR extraction for reference.
     */
    data class Receipt(
        override val id: Long = 0,
        override val timestamp: Long,
        override val amount: Money,
        override val category: Category,
        override val name: String,
        override val note: String = "",
        override val createdAt: Long = System.currentTimeMillis(),
        val merchantNameRaw: String = "",
        val transactionDateRaw: String = "",
        val totalAmountRaw: String = "",
        val lineItems: List<LineItem> = emptyList(),
        val serviceCharge: Money? = null,
        val discount: Money? = null,
        val tax: Money? = null,
        val imageUri: String? = null,
        val extractionConfidence: Float = 1.0f,
    ) : Transaction()

    /**
     * Wire transfer with recipient info and the original screenshot.
     *
     * [name] is initialized from the extracted recipient name but can be edited.
     * [recipientName] preserves the original extraction for reference.
     */
    data class WireTransfer(
        override val id: Long = 0,
        override val timestamp: Long,
        override val amount: Money,
        override val category: Category,
        override val name: String,
        override val note: String = "",
        override val createdAt: Long = System.currentTimeMillis(),
        val recipientName: String = "",
        val imageUri: String? = null,
        val extractionConfidence: Float = 1.0f,
    ) : Transaction()

    /**
     * Manually entered transaction — no extras beyond the base fields.
     */
    data class Manual(
        override val id: Long = 0,
        override val timestamp: Long,
        override val amount: Money,
        override val category: Category,
        override val name: String,
        override val note: String = "",
        override val createdAt: Long = System.currentTimeMillis(),
    ) : Transaction()
}
