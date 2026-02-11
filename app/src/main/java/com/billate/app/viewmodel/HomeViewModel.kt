package com.billate.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billate.app.core.model.Transaction
import com.billate.app.data.local.ApiKeyManager
import com.billate.app.data.repository.TransactionRepository
import com.billate.app.domain.usecase.ProcessReceiptUseCase
import com.billate.app.domain.usecase.ReceiptProcessingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HomeUiState {
    data object Initial : HomeUiState()
    data object Processing : HomeUiState()
    data class AutoSaved(val transaction: Transaction) : HomeUiState()
    data class ReviewNeeded(val transaction: Transaction, val reason: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: TransactionRepository,
    private val processReceipt: ProcessReceiptUseCase,
    private val apiKeyManager: ApiKeyManager,
) : ViewModel() {

    val transactions: StateFlow<List<Transaction>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun hasApiKey(): Boolean = apiKeyManager.hasApiKey()

    fun onImageSelected(uri: Uri) {
        _uiState.value = HomeUiState.Processing
        viewModelScope.launch {
            when (val result = processReceipt(uri)) {
                is ReceiptProcessingResult.AutoSaved -> {
                    _uiState.value = HomeUiState.AutoSaved(result.transaction)
                }
                is ReceiptProcessingResult.ReviewNeeded -> {
                    _uiState.value = HomeUiState.ReviewNeeded(result.transaction, result.reason)
                }
                is ReceiptProcessingResult.Failed -> {
                    _uiState.value = HomeUiState.Error(result.message)
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Initial
    }
}
