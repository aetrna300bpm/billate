package com.billate.app.domain.usecase

import com.billate.app.core.model.Transaction
import com.billate.app.data.repository.TransactionRepository
import javax.inject.Inject

/**
 * Saves a new transaction to the database.
 */
class SaveTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(transaction: Transaction): Long =
        repository.save(transaction)
}
