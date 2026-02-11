package com.billate.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billate.app.data.BillRepository
import com.billate.app.domain.SaveBillUseCase
import com.billate.app.model.BillTransaction
import com.billate.app.model.Category
import com.billate.app.model.LineItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ReviewUiState {
    data object Initial : ReviewUiState()
    data class Editing(val bill: BillTransaction, val isExisting: Boolean = false) : ReviewUiState()
    data object Saving : ReviewUiState()
    data object Saved : ReviewUiState()
    data class Error(val message: String) : ReviewUiState()
}

@HiltViewModel
class BillReviewViewModel @Inject constructor(
    private val saveBillUseCase: SaveBillUseCase,
    private val repository: BillRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Initial)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    /** Load a new (unsaved) bill for review. */
    fun loadBill(bill: BillTransaction) {
        _uiState.value = ReviewUiState.Editing(bill, isExisting = false)
    }

    /** Load an existing bill from the database by ID. */
    fun loadBillById(billId: Long) {
        viewModelScope.launch {
            val bill = repository.getBillById(billId)
            if (bill != null) {
                _uiState.value = ReviewUiState.Editing(bill, isExisting = true)
            } else {
                _uiState.value = ReviewUiState.Error("Bill not found")
            }
        }
    }

    fun updateMerchant(name: String) {
        updateBill { it.copy(merchantName = name) }
    }

    fun updateDate(date: String) {
        updateBill { it.copy(transactionDate = date) }
    }

    fun updateTotal(total: Long) {
        updateBill { it.copy(totalAmountVnd = total) }
    }

    fun updateCategory(category: Category) {
        updateBill { it.copy(category = category) }
    }

    fun updateLineItem(index: Int, item: LineItem) {
        updateBill { bill ->
            val items = bill.lineItems.toMutableList()
            if (index in items.indices) {
                items[index] = item
            }
            bill.copy(lineItems = items)
        }
    }

    fun addLineItem() {
        updateBill { bill ->
            bill.copy(
                lineItems = bill.lineItems + LineItem(
                    description = "",
                    qty = 1,
                    amountVnd = 0,
                    amountRaw = "",
                ),
            )
        }
    }

    fun removeLineItem(index: Int) {
        updateBill { bill ->
            val items = bill.lineItems.toMutableList()
            if (index in items.indices) {
                items.removeAt(index)
            }
            bill.copy(lineItems = items)
        }
    }

    fun saveBill() {
        val state = _uiState.value as? ReviewUiState.Editing ?: return
        val current = state.bill
        _uiState.value = ReviewUiState.Saving
        viewModelScope.launch {
            try {
                if (state.isExisting) {
                    repository.updateBill(current)
                } else {
                    saveBillUseCase(current)
                }
                _uiState.value = ReviewUiState.Saved
            } catch (e: Exception) {
                _uiState.value = ReviewUiState.Error(e.message ?: "Failed to save")
            }
        }
    }

    private fun updateBill(transform: (BillTransaction) -> BillTransaction) {
        val current = (_uiState.value as? ReviewUiState.Editing)?.bill ?: return
        _uiState.value = ReviewUiState.Editing(transform(current))
    }
}
