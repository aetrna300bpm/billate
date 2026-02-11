package com.billate.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billate.app.data.BillRepository
import com.billate.app.data.local.ApiKeyManager
import com.billate.app.domain.ProcessBillUseCase
import com.billate.app.model.BillProcessingOutcome
import com.billate.app.model.BillTransaction
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
    data class AutoSaved(val bill: BillTransaction) : HomeUiState()
    data class ReviewNeeded(val bill: BillTransaction, val reason: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: BillRepository,
    private val processBill: ProcessBillUseCase,
    private val apiKeyManager: ApiKeyManager,
) : ViewModel() {

    val bills: StateFlow<List<BillTransaction>> = repository.getBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun hasApiKey(): Boolean = apiKeyManager.hasApiKey()

    fun onImageSelected(uri: Uri) {
        _uiState.value = HomeUiState.Processing
        viewModelScope.launch {
            when (val result = processBill(uri)) {
                is BillProcessingOutcome.AutoSaved -> {
                    _uiState.value = HomeUiState.AutoSaved(result.bill)
                }
                is BillProcessingOutcome.RequiresReview -> {
                    _uiState.value = HomeUiState.ReviewNeeded(result.bill, result.reason)
                }
                is BillProcessingOutcome.Failed -> {
                    _uiState.value = HomeUiState.Error(result.message)
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Initial
    }
}
