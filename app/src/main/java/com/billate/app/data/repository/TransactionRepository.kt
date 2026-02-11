package com.billate.app.data.repository

import com.billate.app.core.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAll(): Flow<List<Transaction>>
    suspend fun getById(id: Long): Transaction?
    fun getByDateRange(startMs: Long, endMs: Long): Flow<List<Transaction>>
    suspend fun save(transaction: Transaction): Long
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: Long)
}
