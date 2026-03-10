package com.billate.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.billate.app.core.model.CategoryAmount
import com.billate.app.ui.theme.categoryColor

@Composable
fun CategoryPieChart(
    breakdown: List<CategoryAmount>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(120.dp)) {
        if (breakdown.isEmpty()) return@Canvas
        var startAngle = -90f
        breakdown.forEach { item ->
            val sweep = item.percentage * 360f
            drawArc(
                color = categoryColor(item.category),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
            )
            startAngle += sweep
        }
    }
}
