package com.billate.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("billate_settings", Context.MODE_PRIVATE)

    fun getApiKey(): String =
        prefs.getString(KEY_API_KEY, "") ?: ""

    fun saveApiKey(key: String) {
        prefs.edit { putString(KEY_API_KEY, key.trim()) }
    }

    fun getModelName(): String =
        prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun saveModelName(name: String) {
        prefs.edit { putString(KEY_MODEL_NAME, name.trim()) }
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_MODEL_NAME = "gemini_model_name"
        const val DEFAULT_MODEL = "gemini-3.0-flash"
    }
}
