package com.billate.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billate.app.core.model.Category
import com.billate.app.core.model.LineItem
import com.billate.app.core.model.Money
import com.billate.app.core.model.Transaction
import com.billate.app.data.repository.TransactionRepository
import com.billate.app.domain.usecase.DeleteTransactionUseCase
import com.billate.app.domain.usecase.SaveTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TransactionDetailUiState {
    data object Initial : TransactionDetailUiState()
    data class Editing(val transaction: Transaction, val isExisting: Boolean = false) : TransactionDetailUiState()
    data object Saving : TransactionDetailUiState()
    data object Saved : TransactionDetailUiState()
    data object Deleted : TransactionDetailUiState()
    data class Error(val message: String) : TransactionDetailUiState()
}

/**
 * Controls what happens to the transaction total when a line item is edited.
 */
enum class LineItemEditMode {
    /** Recalculate total from line items + adjustments (user correcting OCR mistakes). */
    RECALCULATE_TOTAL,
    /** Keep total as-is (user splitting the bill / removing their share). */
    KEEP_TOTAL,
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val saveTransaction: SaveTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val repository: TransactionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TransactionDetailUiState>(TransactionDetailUiState.Initial)
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    /** How line item edits affect the total. Default: recalculate. */
    var lineItemEditMode: LineItemEditMode = LineItemEditMode.RECALCULATE_TOTAL

    /** Load a new (unsaved) transaction for review. */
    fun loadTransaction(transaction: Transaction) {
        _uiState.value = TransactionDetailUiState.Editing(transaction, isExisting = false)
    }

    /** Load an existing transaction from the database by ID. */
    fun loadTransactionById(id: Long) {
        viewModelScope.launch {
            val tx = repository.getById(id)
            if (tx != null) {
                _uiState.value = TransactionDetailUiState.Editing(tx, isExisting = true)
            } else {
                _uiState.value = TransactionDetailUiState.Error("Transaction not found")
            }
        }
    }

    fun updateName(name: String) {
        updateTransaction { tx ->
            when (tx) {
                is Transaction.Receipt -> tx.copy(name = name)
                is Transaction.WireTransfer -> tx.copy(name = name)
                is Transaction.Manual -> tx.copy(name = name)
            }
        }
    }

    fun updateNote(note: String) {
        updateTransaction { tx ->
            when (tx) {
                is Transaction.Receipt -> tx.copy(note = note)
                is Transaction.WireTransfer -> tx.copy(note = note)
                is Transaction.Manual -> tx.copy(note = note)
            }
        }
    }

    fun updateTotal(amountMinor: Long) {
        updateTransaction { tx ->
            val newAmount = tx.amount.copy(amountMinor = amountMinor)
            when (tx) {
                is Transaction.Receipt -> tx.copy(amount = newAmount)
                is Transaction.WireTransfer -> tx.copy(amount = newAmount)
                is Transaction.Manual -> tx.copy(amount = newAmount)
            }
        }
    }

    fun updateCategory(category: Category) {
        updateTransaction { tx ->
            when (tx) {
                is Transaction.Receipt -> tx.copy(category = category)
                is Transaction.WireTransfer -> tx.copy(category = category)
                is Transaction.Manual -> tx.copy(category = category)
            }
        }
    }

    fun updateTimestamp(timestamp: Long) {
        updateTransaction { tx ->
            when (tx) {
                is Transaction.Receipt -> tx.copy(timestamp = timestamp)
                is Transaction.WireTransfer -> tx.copy(timestamp = timestamp)
                is Transaction.Manual -> tx.copy(timestamp = timestamp)
            }
        }
    }

    // --- Adjustment updates (Receipt only) ---

    fun updateServiceCharge(amountMinor: Long?) {
        updateTransaction { tx ->
            if (tx !is Transaction.Receipt) return@updateTransaction tx
            val newCharge = amountMinor?.let { Money(it, tx.amount.currency) }
            tx.copy(serviceCharge = newCharge)
        }
    }

    fun updateDiscount(amountMinor: Long?) {
        updateTransaction { tx ->
            if (tx !is Transaction.Receipt) return@updateTransaction tx
            val newDiscount = amountMinor?.let { Money(it, tx.amount.currency) }
            tx.copy(discount = newDiscount)
        }
    }

    fun updateTax(amountMinor: Long?) {
        updateTransaction { tx ->
            if (tx !is Transaction.Receipt) return@updateTransaction tx
            val newTax = amountMinor?.let { Money(it, tx.amount.currency) }
            tx.copy(tax = newTax)
        }
    }

    // --- Line item operations (Receipt only) ---

    fun updateLineItem(index: Int, item: LineItem) {
        updateTransaction { tx ->
            if (tx !is Transaction.Receipt) return@updateTransaction tx
            val items = tx.lineItems.toMutableList()
            if (index in items.indices) {
                items[index] = item
            }
            val updated = tx.copy(lineItems = items)
            maybeRecalculateTotal(updated)
        }
    }

    fun addLineItem() {
        updateTransaction { tx ->
            if (tx !is Transaction.Receipt) return@updateTransaction tx
            val currency = tx.amount.currency
            tx.copy(
                lineItems = tx.lineItems + LineItem(
                    description = "",
                    qty = 1,
                    amount = Money.zero(currency),
                ),
            )
        }
    }

    fun removeLineItem(index: Int) {
        updateTransaction { tx ->
            if (tx !is Transaction.Receipt) return@updateTransaction tx
            val items = tx.lineItems.toMutableList()
            if (index in items.indices) {
                items.removeAt(index)
            }
            val updated = tx.copy(lineItems = items)
            maybeRecalculateTotal(updated)
        }
    }

    // --- Delete ---

    fun delete() {
        val state = _uiState.value as? TransactionDetailUiState.Editing ?: return
        viewModelScope.launch {
            try {
                if (state.isExisting) {
                    deleteTransaction(state.transaction.id)
                }
                _uiState.value = TransactionDetailUiState.Deleted
            } catch (e: Exception) {
                _uiState.value = TransactionDetailUiState.Error(e.message ?: "Failed to delete")
            }
        }
    }

    // --- Save ---

    fun save() {
        val state = _uiState.value as? TransactionDetailUiState.Editing ?: return
        val current = state.transaction
        _uiState.value = TransactionDetailUiState.Saving
        viewModelScope.launch {
            try {
                if (state.isExisting) {
                    repository.update(current)
                } else {
                    saveTransaction(current)
                }
                _uiState.value = TransactionDetailUiState.Saved
            } catch (e: Exception) {
                _uiState.value = TransactionDetailUiState.Error(e.message ?: "Failed to save")
            }
        }
    }

    // --- Helpers ---

    /**
     * Recalculate the transaction total from line items + adjustments,
     * but only when [lineItemEditMode] is [LineItemEditMode.RECALCULATE_TOTAL].
     */
    private fun maybeRecalculateTotal(tx: Transaction.Receipt): Transaction.Receipt {
        if (lineItemEditMode != LineItemEditMode.RECALCULATE_TOTAL) return tx
        val itemsTotal = tx.lineItems.sumOf { it.amount.amountMinor * it.qty }
        val svcCharge = tx.serviceCharge?.amountMinor ?: 0L
        val disc = tx.discount?.amountMinor ?: 0L   // already negative
        val taxAmt = tx.tax?.amountMinor ?: 0L
        val newTotal = itemsTotal + svcCharge + disc + taxAmt
        return tx.copy(amount = tx.amount.copy(amountMinor = newTotal))
    }

    private fun updateTransaction(transform: (Transaction) -> Transaction) {
        val state = _uiState.value as? TransactionDetailUiState.Editing ?: return
        _uiState.value = state.copy(transaction = transform(state.transaction))
    }
}
