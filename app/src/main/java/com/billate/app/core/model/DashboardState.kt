package com.billate.app.core.model

data class DashboardState(
    val periodLabel: String = "This Month",
    val periodType: PeriodType = PeriodType.MONTH,
    val totalSpent: Money = Money.zero("VND"),
    val transactionCount: Int = 0,
    val categoryBreakdown: List<CategoryAmount> = emptyList(),
    val customStartMs: Long? = null,
    val customEndMs: Long? = null,
)
