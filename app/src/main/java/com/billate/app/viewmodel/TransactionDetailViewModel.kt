package com.billate.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billate.app.core.model.Bill
import com.billate.app.core.model.Category
import com.billate.app.core.model.LineItem
import com.billate.app.core.model.Money
import com.billate.app.core.model.Transaction
import com.billate.app.data.repository.TransactionRepository
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
    data class Error(val message: String) : TransactionDetailUiState()
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val saveTransaction: SaveTransactionUseCase,
    private val repository: TransactionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TransactionDetailUiState>(TransactionDetailUiState.Initial)
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

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

    fun updateMerchant(name: String) {
        updateTransaction { tx ->
            tx.copy(bill = (tx.bill ?: Bill()).copy(merchantName = name))
        }
    }

    fun updateNote(note: String) {
        updateTransaction { it.copy(note = note) }
    }

    fun updateTotal(amountMinor: Long) {
        updateTransaction { tx ->
            tx.copy(amount = tx.amount.copy(amountMinor = amountMinor))
        }
    }

    fun updateCategory(category: Category) {
        updateTransaction { it.copy(category = category) }
    }

    fun updateTimestamp(timestamp: Long) {
        updateTransaction { it.copy(timestamp = timestamp) }
    }

    fun updateLineItem(index: Int, item: LineItem) {
        updateTransaction { tx ->
            val bill = tx.bill ?: return@updateTransaction tx
            val items = bill.lineItems.toMutableList()
            if (index in items.indices) {
                items[index] = item
            }
            tx.copy(bill = bill.copy(lineItems = items))
        }
    }

    fun addLineItem() {
        updateTransaction { tx ->
            val bill = tx.bill ?: return@updateTransaction tx
            val currency = tx.amount.currency
            tx.copy(
                bill = bill.copy(
                    lineItems = bill.lineItems + LineItem(
                        description = "",
                        qty = 1,
                        amount = Money.zero(currency),
                    ),
                ),
            )
        }
    }

    fun removeLineItem(index: Int) {
        updateTransaction { tx ->
            val bill = tx.bill ?: return@updateTransaction tx
            val items = bill.lineItems.toMutableList()
            if (index in items.indices) {
                items.removeAt(index)
            }
            tx.copy(bill = bill.copy(lineItems = items))
        }
    }

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

    private fun updateTransaction(transform: (Transaction) -> Transaction) {
        val state = _uiState.value as? TransactionDetailUiState.Editing ?: return
        _uiState.value = state.copy(transaction = transform(state.transaction))
    }
}
