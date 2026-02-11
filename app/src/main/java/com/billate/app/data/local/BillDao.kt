package com.billate.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Transaction
    @Query("SELECT * FROM bill_transactions ORDER BY createdAt DESC")
    fun getAllBillsWithLineItems(): Flow<List<BillWithLineItems>>

    @Insert
    suspend fun insertBill(bill: BillTransactionEntity): Long

    @Insert
    suspend fun insertLineItems(items: List<LineItemEntity>)

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

    @Query("DELETE FROM bill_transactions WHERE id = :billId")
    suspend fun deleteBill(billId: Long)
}
