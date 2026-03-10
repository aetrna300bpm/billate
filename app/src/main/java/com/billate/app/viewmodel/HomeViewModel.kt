package com.billate.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billate.app.core.model.Category
import com.billate.app.core.model.CategoryAmount
import com.billate.app.core.model.DashboardState
import com.billate.app.core.model.GroupedTransactions
import com.billate.app.core.model.Money
import com.billate.app.core.model.PeriodType
import com.billate.app.core.model.Transaction
import com.billate.app.data.local.ApiKeyManager
import com.billate.app.data.repository.TransactionRepository
import com.billate.app.domain.usecase.ProcessReceiptUseCase
import com.billate.app.domain.usecase.ReceiptProcessingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class HomeUiState {
    data object Initial : HomeUiState()
    data object Processing : HomeUiState()
    data class AutoSaved(val transaction: Transaction) : HomeUiState()
    data class ReviewNeeded(val transaction: Transaction, val reason: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val processReceipt: ProcessReceiptUseCase,
    private val apiKeyManager: ApiKeyManager,
) : ViewModel() {

    // ── Period state (shared with Insights via ApiKeyManager) ──
    private val _periodType = MutableStateFlow(apiKeyManager.getPeriodType())
    private val _customStartMs = MutableStateFlow(apiKeyManager.getCustomStartMs())
    private val _customEndMs = MutableStateFlow(apiKeyManager.getCustomEndMs())

    // ── Search & filter state ──
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _selectedCategories = MutableStateFlow<Set<Category>>(emptySet())
    val selectedCategories: StateFlow<Set<Category>> = _selectedCategories.asStateFlow()

    // ── All transactions (for the full list, sorted desc) ──
    val transactions: StateFlow<List<Transaction>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Dashboard date-range transactions ──
    private val dateRange = combine(_periodType, _customStartMs, _customEndMs) { period, start, end ->
        InsightsViewModel.computeDateRange(period, start, end)
    }

    private val dashboardTransactions = dateRange.flatMapLatest { (startMs, endMs) ->
        repository.getByDateRange(startMs, endMs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Dashboard state ──
    val dashboardState: StateFlow<DashboardState> = combine(
        dashboardTransactions,
        _periodType,
        _customStartMs,
        _customEndMs,
    ) { txList, period, customStart, customEnd ->
        val currency = apiKeyManager.getDefaultCurrency()
        val (startMs, endMs) = InsightsViewModel.computeDateRange(period, customStart, customEnd)
        val label = InsightsViewModel.computePeriodLabel(period, startMs, endMs)
        computeDashboard(txList, currency, label, period, customStart, customEnd)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardState(),
    )

    // ── Grouped & filtered transaction list ──
    val groupedTransactions: StateFlow<List<GroupedTransactions>> = combine(
        transactions,
        _searchQuery,
        _selectedCategories,
    ) { txList, query, categories ->
        val filtered = txList.filter { tx ->
            val matchesQuery = if (query.isBlank()) true else {
                val q = query.lowercase()
                tx.name.lowercase().contains(q) ||
                    tx.note.lowercase().contains(q) ||
                    tx.amount.amountMinor.toString().contains(q)
            }
            val matchesCategory = categories.isEmpty() || tx.category in categories
            matchesQuery && matchesCategory
        }
        groupByDay(filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // ── Period ──
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

    // ── Search & filter ──
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategoryToggle(category: Category) {
        val current = _selectedCategories.value.toMutableSet()
        if (category in current) current.remove(category) else current.add(category)
        _selectedCategories.value = current
    }

    // ── Helpers ──

    private fun computeDashboard(
        transactions: List<Transaction>,
        currency: String,
        periodLabel: String,
        periodType: PeriodType,
        customStartMs: Long?,
        customEndMs: Long?,
    ): DashboardState {
        val filtered = transactions.filter { it.amount.currency == currency }
        val total = filtered.sumOf { it.amount.amountMinor }

        val byCategory = filtered.groupBy { it.category }
        val breakdown = byCategory.map { (cat, txList) ->
            val catTotal = txList.sumOf { it.amount.amountMinor }
            CategoryAmount(
                category = cat,
                totalMinor = catTotal,
                percentage = if (total > 0) catTotal.toFloat() / total else 0f,
            )
        }.sortedByDescending { it.totalMinor }

        return DashboardState(
            periodLabel = periodLabel,
            periodType = periodType,
            totalSpent = Money(total, currency),
            transactionCount = filtered.size,
            categoryBreakdown = breakdown,
            customStartMs = customStartMs,
            customEndMs = customEndMs,
        )
    }

    private fun groupByDay(transactions: List<Transaction>): List<GroupedTransactions> {
        if (transactions.isEmpty()) return emptyList()
        val currency = apiKeyManager.getDefaultCurrency()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val currentYear = today.year

        val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM dd", Locale.US)
        val dayYearFormat = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy", Locale.US)

        return transactions
            .groupBy { tx ->
                Instant.ofEpochMilli(tx.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            .toSortedMap(compareByDescending { it })
            .map { (date, txList) ->
                val label = when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> if (date.year == currentYear) date.format(dayFormat) else date.format(dayYearFormat)
                }
                val dailyTotal = Money(
                    txList.filter { it.amount.currency == currency }.sumOf { it.amount.amountMinor },
                    currency,
                )
                GroupedTransactions(
                    date = date,
                    label = label,
                    dailyTotal = dailyTotal,
                    transactions = txList.sortedByDescending { it.timestamp },
                )
            }
    }
}
