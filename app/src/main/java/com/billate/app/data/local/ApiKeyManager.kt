package com.billate.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.billate.app.core.model.PeriodType
import com.billate.app.viewmodel.InsightEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("billate_settings", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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

    fun getDefaultCurrency(): String =
        prefs.getString(KEY_DEFAULT_CURRENCY, DEFAULT_CURRENCY) ?: DEFAULT_CURRENCY

    fun setDefaultCurrency(code: String) {
        prefs.edit { putString(KEY_DEFAULT_CURRENCY, code.trim().uppercase()) }
    }

    // ── Period selection (shared between Dashboard & Insights) ──

    fun getPeriodType(): PeriodType {
        val ordinal = prefs.getInt(KEY_PERIOD_TYPE, PeriodType.MONTH.ordinal)
        return PeriodType.entries.getOrElse(ordinal) { PeriodType.MONTH }
    }

    fun savePeriodType(type: PeriodType) {
        prefs.edit { putInt(KEY_PERIOD_TYPE, type.ordinal) }
    }

    fun getCustomStartMs(): Long? {
        val v = prefs.getLong(KEY_CUSTOM_START, -1L)
        return if (v == -1L) null else v
    }

    fun saveCustomStartMs(ms: Long) {
        prefs.edit { putLong(KEY_CUSTOM_START, ms) }
    }

    fun getCustomEndMs(): Long? {
        val v = prefs.getLong(KEY_CUSTOM_END, -1L)
        return if (v == -1L) null else v
    }

    fun saveCustomEndMs(ms: Long) {
        prefs.edit { putLong(KEY_CUSTOM_END, ms) }
    }

    // ── Insight cache ──

    fun cacheInsight(entry: InsightEntry) {
        val data = json.encodeToString(
            InsightCacheData.serializer(),
            InsightCacheData(entry.text, entry.generatedAt, entry.periodLabel),
        )
        prefs.edit { putString(KEY_INSIGHT_CACHE, data) }
    }

    fun getCachedInsight(): InsightEntry? {
        val raw = prefs.getString(KEY_INSIGHT_CACHE, null) ?: return null
        return try {
            val data = json.decodeFromString(InsightCacheData.serializer(), raw)
            InsightEntry(data.text, data.timestamp, data.periodLabel)
        } catch (_: Exception) {
            null
        }
    }

    @Serializable
    private data class InsightCacheData(
        val text: String,
        val timestamp: Long,
        val periodLabel: String,
    )

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_MODEL_NAME = "gemini_model_name"
        private const val KEY_DEFAULT_CURRENCY = "default_currency"
        private const val KEY_PERIOD_TYPE = "period_type"
        private const val KEY_CUSTOM_START = "custom_start_ms"
        private const val KEY_CUSTOM_END = "custom_end_ms"
        private const val KEY_INSIGHT_CACHE = "insight_cache"
        const val DEFAULT_MODEL = "gemini-3-flash-preview"
        const val DEFAULT_CURRENCY = "VND"
    }
}
