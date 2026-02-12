package com.billate.app.data.local

import com.billate.app.core.model.Bill
import com.billate.app.core.model.Category
import com.billate.app.core.model.LineItem
import com.billate.app.core.model.Money
import com.billate.app.core.model.Transaction

/**
 * Maps between Room entities and domain models.
 */
object TransactionMappers {

    fun TransactionWithLineItems.toDomain(): Transaction {
        val tx = transaction
        val currency = tx.currency
        val hasBill = tx.merchantName != null

        val bill = if (hasBill) {
            Bill(
                merchantName = tx.merchantName ?: "",
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
                notes = tx.billNotes ?: "",
                imageUri = tx.billImageUri,
                extractionConfidence = tx.extractionConfidence,
            )
        } else {
            null
        }

        return Transaction(
            id = tx.id,
            timestamp = tx.timestamp,
            amount = Money(tx.amountMinor, currency),
            category = Category.fromString(tx.category) ?: Category.Other,
            bill = bill,
            note = tx.note,
            createdAt = tx.createdAt,
        )
    }

    private fun LineItemEntity.toDomain(currency: String) = LineItem(
        id = id,
        description = description,
        qty = qty,
        amount = Money(amountMinor, currency),
        amountRaw = amountRaw,
    )

    fun Transaction.toEntity() = TransactionEntity(
        id = if (id == 0L) 0 else id,
        timestamp = timestamp,
        amountMinor = amount.amountMinor,
        currency = amount.currency,
        category = category.displayName,
        note = note,
        merchantName = bill?.merchantName,
        transactionDateRaw = bill?.transactionDateRaw,
        totalAmountRaw = bill?.totalAmountRaw,
        billNotes = bill?.notes,
        billImageUri = bill?.imageUri,
        serviceChargeMinor = bill?.serviceCharge?.amountMinor,
        serviceChargeCurrency = bill?.serviceCharge?.currency,
        discountMinor = bill?.discount?.amountMinor,
        discountCurrency = bill?.discount?.currency,
        taxMinor = bill?.tax?.amountMinor,
        taxCurrency = bill?.tax?.currency,
        extractionConfidence = bill?.extractionConfidence ?: 1.0f,
        createdAt = createdAt,
    )

    fun Transaction.lineItemEntities(): List<LineItemEntity> {
        val items = bill?.lineItems ?: return emptyList()
        return items.map { item ->
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
