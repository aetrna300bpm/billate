package com.billate.app.core.model

data class CategoryAmount(
    val category: Category,
    val totalMinor: Long,
    val percentage: Float, // 0.0–1.0
)
