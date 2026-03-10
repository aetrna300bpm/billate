package com.billate.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.billate.app.core.model.Category

fun categoryColor(category: Category): Color = when (category) {
    Category.Groceries     -> Color(0xFF4CAF50) // green
    Category.Dining        -> Color(0xFFFF9800) // orange
    Category.Shopping      -> Color(0xFF2196F3) // blue
    Category.Transport     -> Color(0xFF9C27B0) // purple
    Category.Utilities     -> Color(0xFF607D8B) // blue-grey
    Category.Health        -> Color(0xFFE91E63) // pink
    Category.Entertainment -> Color(0xFFFFEB3B) // yellow
    Category.Education     -> Color(0xFF00BCD4) // cyan
    Category.Other         -> Color(0xFF795548) // brown
}
