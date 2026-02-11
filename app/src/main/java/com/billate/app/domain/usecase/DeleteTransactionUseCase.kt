package com.billate.app.domain.usecase

import com.billate.app.core.model.Transaction
import com.billate.app.data.repository.TransactionRepository
import javax.inject.Inject

/**
 * Deletes a transaction and its associated receipt image.
 */
class DeleteTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
