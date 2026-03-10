package com.billate.app.data.local

import com.billate.app.core.model.Category
import com.billate.app.core.model.LineItem
import com.billate.app.core.model.Money
import com.billate.app.core.model.Transaction

/**
 * Maps between Room entities and the sealed [Transaction] hierarchy.
 */
object TransactionMappers {

    fun TransactionWithLineItems.toDomain(): Transaction {
        val tx = transaction
        val currency = tx.currency
        val amount = Money(tx.amountMinor, currency)
        val category = Category.fromString(tx.category) ?: Category.Other

        return when (tx.type) {
            "receipt" -> Transaction.Receipt(
                id = tx.id,
                timestamp = tx.timestamp,
                amount = amount,
                category = category,
                name = tx.name.ifBlank { tx.merchantName ?: "" },
                note = tx.note.ifBlank { tx.billNotes ?: "" },
                createdAt = tx.createdAt,
                merchantNameRaw = tx.merchantName ?: "",
                transactionDateRaw = tx.transactionDateRaw ?: "",
                totalAmountRaw = tx.totalAmountRaw ?: "",
                lineItems = lineItems.map { it.toDomain(currency) },
                serviceCharge = tx.serviceChargeMinor?.let {
                    Money(it, tx.serviceChargeCurrency ?: currency)
                },
                discount = tx.discountMinor?.let {
                    Money(it, tx.discountCurrency ?: currency)
                },
                tax = tx.taxMinor?.let {
                    Money(it, tx.taxCurrency ?: currency)
                },
                imageUri = tx.billImageUri,
                extractionConfidence = tx.extractionConfidence,
            )
            "wire_transfer" -> Transaction.WireTransfer(
                id = tx.id,
                timestamp = tx.timestamp,
                amount = amount,
                category = category,
                name = tx.name.ifBlank { tx.recipientName ?: "" },
                note = tx.note,
                createdAt = tx.createdAt,
                recipientName = tx.recipientName ?: "",
                imageUri = tx.billImageUri,
                extractionConfidence = tx.extractionConfidence,
            )
            else -> Transaction.Manual(
                id = tx.id,
                timestamp = tx.timestamp,
                amount = amount,
                category = category,
                name = tx.name,
                note = tx.note,
                createdAt = tx.createdAt,
            )
        }
    }

    private fun LineItemEntity.toDomain(currency: String) = LineItem(
        id = id,
        description = description,
        qty = qty,
        amount = Money(amountMinor, currency),
        amountRaw = amountRaw,
    )

    fun Transaction.toEntity(): TransactionEntity {
        val typeStr = when (this) {
            is Transaction.Receipt -> "receipt"
            is Transaction.WireTransfer -> "wire_transfer"
            is Transaction.Manual -> "manual"
        }
        val base = TransactionEntity(
            id = if (id == 0L) 0 else id,
            type = typeStr,
            timestamp = timestamp,
            amountMinor = amount.amountMinor,
            currency = amount.currency,
            category = category.displayName,
            name = name,
            note = note,
            createdAt = createdAt,
        )
        return when (this) {
            is Transaction.Receipt -> base.copy(
                merchantName = merchantNameRaw,
                transactionDateRaw = transactionDateRaw,
                totalAmountRaw = totalAmountRaw,
                billImageUri = imageUri,
                serviceChargeMinor = serviceCharge?.amountMinor,
                serviceChargeCurrency = serviceCharge?.currency,
                discountMinor = discount?.amountMinor,
                discountCurrency = discount?.currency,
                taxMinor = tax?.amountMinor,
                taxCurrency = tax?.currency,
                extractionConfidence = extractionConfidence,
            )
            is Transaction.WireTransfer -> base.copy(
                recipientName = recipientName,
                billImageUri = imageUri,
                extractionConfidence = extractionConfidence,
            )
            is Transaction.Manual -> base
        }
    }

    fun Transaction.lineItemEntities(): List<LineItemEntity> {
        if (this !is Transaction.Receipt) return emptyList()
        return lineItems.map { item ->
            LineItemEntity(
                id = 0,
                transactionId = 0, // Will be set by DAO
                description = item.description,
                qty = item.qty,
                amountMinor = item.amount.amountMinor,
                currency = item.amount.currency,
                amountRaw = item.amountRaw,
            )
        }
    }
}
