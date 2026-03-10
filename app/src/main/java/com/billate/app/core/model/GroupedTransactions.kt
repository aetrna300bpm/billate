package com.billate.app.core.model

import java.time.LocalDate

data class GroupedTransactions(
    val date: LocalDate,
    val label: String,
    val dailyTotal: Money,
    val transactions: List<Transaction>,
)
