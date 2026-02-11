package com.billate.app.data.local

import com.billate.app.model.BillTransaction
import com.billate.app.model.Category
import com.billate.app.model.LineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillLocalDataSource @Inject constructor(
    private val billDao: BillDao,
) {
    fun getAllBills(): Flow<List<BillTransaction>> {
        return billDao.getAllBillsWithLineItems().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun saveBill(bill: BillTransaction): Long {
        val entity = bill.toEntity()
        val lineItemEntities = bill.lineItems.map { it.toEntity(billId = 0) }
        return billDao.insertBillWithLineItems(entity, lineItemEntities)
    }

    suspend fun deleteBill(billId: Long) {
        billDao.deleteBill(billId)
    }
}

// --- Mappers ---

private fun BillWithLineItems.toDomain() = BillTransaction(
    id = bill.id,
    merchantName = bill.merchantName,
    transactionDate = bill.transactionDate,
    transactionDateRaw = bill.transactionDateRaw,
    currency = bill.currency,
    totalAmountVnd = bill.totalAmountVnd,
    totalAmountRaw = bill.totalAmountRaw,
    category = Category.fromString(bill.category) ?: Category.Other,
    lineItems = lineItems.map { it.toDomain() },
    notes = bill.notes,
    createdAt = bill.createdAt,
)

private fun LineItemEntity.toDomain() = LineItem(
    id = id,
    description = description,
    qty = qty,
    amountVnd = amountVnd,
    amountRaw = amountRaw,
)

private fun BillTransaction.toEntity() = BillTransactionEntity(
    id = if (id == 0L) 0 else id,
    merchantName = merchantName,
    transactionDate = transactionDate,
    transactionDateRaw = transactionDateRaw,
    currency = currency,
    totalAmountVnd = totalAmountVnd,
    totalAmountRaw = totalAmountRaw,
    category = category.displayName,
    notes = notes,
    createdAt = createdAt,
)

private fun LineItem.toEntity(billId: Long) = LineItemEntity(
    id = 0,
    billId = billId,
    description = description,
    qty = qty,
    amountVnd = amountVnd,
    amountRaw = amountRaw,
)
