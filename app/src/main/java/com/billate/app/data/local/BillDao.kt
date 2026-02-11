package com.billate.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Transaction
    @Query("SELECT * FROM bill_transactions ORDER BY createdAt DESC")
    fun getAllBillsWithLineItems(): Flow<List<BillWithLineItems>>

    @Transaction
    @Query("SELECT * FROM bill_transactions WHERE id = :billId")
    suspend fun getBillWithLineItems(billId: Long): BillWithLineItems?

    @Insert
    suspend fun insertBill(bill: BillTransactionEntity): Long

    @Insert
    suspend fun insertLineItems(items: List<LineItemEntity>)

    @Update
    suspend fun updateBill(bill: BillTransactionEntity)

    @Query("DELETE FROM line_items WHERE billId = :billId")
    suspend fun deleteLineItemsForBill(billId: Long)

    @Transaction
    suspend fun insertBillWithLineItems(
        bill: BillTransactionEntity,
        lineItems: List<LineItemEntity>,
    ): Long {
        val billId = insertBill(bill)
        val itemsWithBillId = lineItems.map { it.copy(billId = billId) }
        insertLineItems(itemsWithBillId)
        return billId
    }

    @Transaction
    suspend fun updateBillWithLineItems(
        bill: BillTransactionEntity,
        lineItems: List<LineItemEntity>,
    ) {
        updateBill(bill)
        deleteLineItemsForBill(bill.id)
        val itemsWithBillId = lineItems.map { it.copy(billId = bill.id) }
        insertLineItems(itemsWithBillId)
    }

    @Query("DELETE FROM bill_transactions WHERE id = :billId")
    suspend fun deleteBill(billId: Long)
}
