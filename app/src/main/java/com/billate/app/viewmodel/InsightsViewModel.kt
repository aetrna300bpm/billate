package com.billate.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billate.app.core.currency.MoneyFormatter
import com.billate.app.core.model.Category
import com.billate.app.core.model.CategoryAmount
import com.billate.app.core.model.Money
import com.billate.app.core.model.PeriodType
import com.billate.app.core.model.Transaction
import com.billate.app.data.local.ApiKeyManager
import com.billate.app.data.repository.TransactionRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SpendingSummary(
    val totalSpent: Money,
    val transactionCount: Int,
    val dailyAverage: Money,
    val categoryBreakdown: List<CategoryAmount>,
)

data class InsightEntry(
    val text: String,
    val generatedAt: Long,
    val periodLabel: String,
)

data class InsightsUiState(
    val summary: SpendingSummary? = null,
    val currentInsight: InsightEntry? = null,
    val isGenerating: Boolean = false,
    val error: String? = null,
    val periodLabel: String = "This Month",
    val periodType: PeriodType = PeriodType.MONTH,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val apiKeyManager: ApiKeyManager,
) : ViewModel() {

    private val _periodType = MutableStateFlow(apiKeyManager.getPeriodType())
    private val _customStartMs = MutableStateFlow(apiKeyManager.getCustomStartMs())
    private val _customEndMs = MutableStateFlow(apiKeyManager.getCustomEndMs())
    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())

    init {
        viewModelScope.launch {
            // Observe period changes and update transactions
            combine(_periodType, _customStartMs, _customEndMs) { period, start, end ->
                Triple(period, start, end)
            }.collect { (period, customStart, customEnd) ->
                val (startMs, endMs) = computeDateRange(period, customStart, customEnd)
                val label = computePeriodLabel(period, startMs, endMs)
                repository.getByDateRange(startMs, endMs).collect { txList ->
                    _transactions.value = txList
                    val currency = apiKeyManager.getDefaultCurrency()
                    val summary = computeSummary(txList, currency, startMs, endMs)
                    val cached = apiKeyManager.getCachedInsight()
                    _uiState.value = _uiState.value.copy(
                        summary = summary,
                        periodLabel = label,
                        periodType = period,
                        currentInsight = cached,
                    )
                }
            }
        }
    }

    fun onPeriodChange(type: PeriodType) {
        _periodType.value = type
        apiKeyManager.savePeriodType(type)
    }

    fun onCustomStartChange(ms: Long) {
        _customStartMs.value = ms
        apiKeyManager.saveCustomStartMs(ms)
    }

    fun onCustomEndChange(ms: Long) {
        _customEndMs.value = ms
        apiKeyManager.saveCustomEndMs(ms)
    }

    fun generateInsight() {
        val summary = _uiState.value.summary ?: return
        val transactions = _transactions.value
        if (transactions.isEmpty()) return

        _uiState.value = _uiState.value.copy(isGenerating = true, error = null)

        viewModelScope.launch {
            try {
                val prompt = buildInsightPrompt(transactions, _uiState.value.periodLabel)
                val model = createModel()
                val response = model.generateContent(
                    content { text(prompt) },
                )
                val text = response.text ?: "No insight generated."
                val entry = InsightEntry(
                    text = text,
                    generatedAt = System.currentTimeMillis(),
                    periodLabel = _uiState.value.periodLabel,
                )
                apiKeyManager.cacheInsight(entry)
                _uiState.value = _uiState.value.copy(
                    currentInsight = entry,
                    isGenerating = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = e.message ?: "Failed to generate insight",
                )
            }
        }
    }

    private fun createModel(): GenerativeModel {
        val apiKey = apiKeyManager.getApiKey()
        require(apiKey.isNotBlank()) { "API key not set." }
        return GenerativeModel(
            modelName = apiKeyManager.getModelName(),
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.7f
                maxOutputTokens = 1024
            },
        )
    }

    private fun buildInsightPrompt(transactions: List<Transaction>, periodLabel: String): String {
        val currency = apiKeyManager.getDefaultCurrency()
        val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
        val lines = transactions.map { tx ->
            val date = dateFormat.format(Date(tx.timestamp))
            val amt = MoneyFormatter.format(tx.amount)
            "- $date | ${tx.category.displayName} | ${tx.name.ifBlank { "Unnamed" }} | $amt"
        }
        return """
You are a personal finance advisor. Analyze this spending data.

PERIOD: $periodLabel

TRANSACTIONS (${transactions.size} total, currency: $currency):
${lines.joinToString("\n")}

Provide:
1. Key spending patterns you observe
2. Biggest categories and whether they seem reasonable
3. 2–3 specific, actionable suggestions
Keep it concise (150–250 words), encouraging.
        """.trimIndent()
    }

    private fun computeSummary(
        transactions: List<Transaction>,
        currency: String,
        startMs: Long,
        endMs: Long,
    ): SpendingSummary {
        val filtered = transactions.filter { it.amount.currency == currency }
        val total = filtered.sumOf { it.amount.amountMinor }
        val days = maxOf(1, ((endMs - startMs) / (24 * 60 * 60 * 1000)).toInt())
        val dailyAvg = if (days > 0) total / days else 0L

        val byCategory = filtered.groupBy { it.category }
        val breakdown = byCategory.map { (cat, txList) ->
            val catTotal = txList.sumOf { it.amount.amountMinor }
            CategoryAmount(
                category = cat,
                totalMinor = catTotal,
                percentage = if (total > 0) catTotal.toFloat() / total else 0f,
            )
        }.sortedByDescending { it.totalMinor }

        return SpendingSummary(
            totalSpent = Money(total, currency),
            transactionCount = filtered.size,
            dailyAverage = Money(dailyAvg, currency),
            categoryBreakdown = breakdown,
        )
    }

    companion object {
        fun computeDateRange(
            periodType: PeriodType,
            customStartMs: Long?,
            customEndMs: Long?,
        ): Pair<Long, Long> {
            val cal = Calendar.getInstance()
            return when (periodType) {
                PeriodType.WEEK -> {
                    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val start = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_WEEK, 7)
                    val end = cal.timeInMillis
                    start to end
                }
                PeriodType.MONTH -> {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    val end = cal.timeInMillis
                    start to end
                }
                PeriodType.CUSTOM -> {
                    val start = customStartMs ?: System.currentTimeMillis()
                    val end = customEndMs ?: System.currentTimeMillis()
                    start to end
                }
            }
        }

        fun computePeriodLabel(
            periodType: PeriodType,
            startMs: Long,
            endMs: Long,
        ): String {
            val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
            return when (periodType) {
                PeriodType.WEEK -> "This Week"
                PeriodType.MONTH -> "This Month"
                PeriodType.CUSTOM -> "${dateFormat.format(Date(startMs))} – ${dateFormat.format(Date(endMs))}"
            }
        }
    }
}
