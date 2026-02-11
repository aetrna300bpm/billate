package com.billate.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllWithLineItems(): Flow<List<TransactionWithLineItems>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getByIdWithLineItems(id: Long): TransactionWithLineItems?

    @Transaction
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp DESC")
    fun getByDateRange(startMs: Long, endMs: Long): Flow<List<TransactionWithLineItems>>

    @Insert
    suspend fun insertTransaction(entity: TransactionEntity): Long

    @Insert
    suspend fun insertLineItems(items: List<LineItemEntity>)

    @Update
    suspend fun updateTransaction(entity: TransactionEntity)

    @Query("DELETE FROM line_items WHERE transactionId = :transactionId")
    suspend fun deleteLineItemsForTransaction(transactionId: Long)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query("SELECT billImageUri FROM transactions WHERE id = :id")
    suspend fun getImageUri(id: Long): String?

    // --- Composite operations ---

    @Transaction
    suspend fun insertWithLineItems(
        entity: TransactionEntity,
        lineItems: List<LineItemEntity>,
    ): Long {
        val txId = insertTransaction(entity)
        val items = lineItems.map { it.copy(transactionId = txId) }
        insertLineItems(items)
        return txId
    }

    @Transaction
    suspend fun updateWithLineItems(
        entity: TransactionEntity,
        lineItems: List<LineItemEntity>,
    ) {
        updateTransaction(entity)
        deleteLineItemsForTransaction(entity.id)
        val items = lineItems.map { it.copy(transactionId = entity.id) }
        insertLineItems(items)
    }
}
