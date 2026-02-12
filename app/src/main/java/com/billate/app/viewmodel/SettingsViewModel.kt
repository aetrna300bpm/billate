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
    val modelName: String = ApiKeyManager.DEFAULT_MODEL,
    val defaultCurrency: String = ApiKeyManager.DEFAULT_CURRENCY,
    val saved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyManager: ApiKeyManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = SettingsUiState(
            apiKey = apiKeyManager.getApiKey(),
            modelName = apiKeyManager.getModelName(),
            defaultCurrency = apiKeyManager.getDefaultCurrency(),
        )
    }

    fun onApiKeyChange(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key, saved = false)
    }

    fun saveApiKey() {
        apiKeyManager.saveApiKey(_uiState.value.apiKey)
        _uiState.value = _uiState.value.copy(saved = true)
    }

    fun onModelChange(name: String) {
        apiKeyManager.saveModelName(name)
        _uiState.value = _uiState.value.copy(modelName = name)
    }

    fun onDefaultCurrencyChange(code: String) {
        apiKeyManager.setDefaultCurrency(code)
        _uiState.value = _uiState.value.copy(defaultCurrency = code)
    }

    fun hasApiKey(): Boolean = apiKeyManager.hasApiKey()

    companion object {
        val modelOptions = listOf(
            "gemini-3-flash-preview",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
        )
    }
}
