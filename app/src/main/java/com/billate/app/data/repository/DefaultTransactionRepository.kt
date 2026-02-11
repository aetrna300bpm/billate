package com.billate.app.data.repository

import com.billate.app.core.model.Transaction
import com.billate.app.data.local.TransactionDao
import com.billate.app.data.local.TransactionMappers.lineItemEntities
import com.billate.app.data.local.TransactionMappers.toDomain
import com.billate.app.data.local.TransactionMappers.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val imageStorage: ReceiptImageStorage,
) : TransactionRepository {

    override fun getAll(): Flow<List<Transaction>> =
        dao.getAllWithLineItems().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Transaction? =
        dao.getByIdWithLineItems(id)?.toDomain()

    override fun getByDateRange(startMs: Long, endMs: Long): Flow<List<Transaction>> =
        dao.getByDateRange(startMs, endMs).map { list -> list.map { it.toDomain() } }

    override suspend fun save(transaction: Transaction): Long =
        dao.insertWithLineItems(transaction.toEntity(), transaction.lineItemEntities())

    override suspend fun update(transaction: Transaction) =
        dao.updateWithLineItems(transaction.toEntity(), transaction.lineItemEntities())

    override suspend fun delete(id: Long) {
        // Delete image file first
        val imageUri = dao.getImageUri(id)
        if (imageUri != null) {
            imageStorage.deleteImage(imageUri)
        }
        dao.deleteTransaction(id)
    }
}
