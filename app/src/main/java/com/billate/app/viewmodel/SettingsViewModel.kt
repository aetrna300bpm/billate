package com.billate.app.viewmodel

import androidx.lifecycle.ViewModel
import com.billate.app.data.local.ApiKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val saved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyManager: ApiKeyManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = SettingsUiState(apiKey = apiKeyManager.getApiKey())
    }

    fun onApiKeyChange(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key, saved = false)
    }

    fun saveApiKey() {
        apiKeyManager.saveApiKey(_uiState.value.apiKey)
        _uiState.value = _uiState.value.copy(saved = true)
    }

    fun hasApiKey(): Boolean = apiKeyManager.hasApiKey()
}
