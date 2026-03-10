package com.billate.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.billate.app.core.currency.MoneyFormatter
import com.billate.app.core.model.DashboardState
import com.billate.app.core.model.PeriodType
import com.billate.app.ui.theme.categoryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardCard(
    state: DashboardState,
    onPeriodChange: (PeriodType) -> Unit,
    onCustomStartPick: () -> Unit,
    onCustomEndPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Period selector
            PeriodDropdown(
                selected = state.periodType,
                label = state.periodLabel,
                onSelected = onPeriodChange,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Summary + Chart row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: stats
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Spent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = MoneyFormatter.format(state.totalSpent),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${state.transactionCount} transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Right: pie chart
                if (state.categoryBreakdown.isNotEmpty()) {
                    CategoryPieChart(
                        breakdown = state.categoryBreakdown,
                    )
                }
            }

            // Category legend
            if (state.categoryBreakdown.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                state.categoryBreakdown.take(5).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height(12.dp),
                            ) {
                                drawCircle(color = categoryColor(item.category))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.category.displayName,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = "${(item.percentage * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Custom date range info
            if (state.periodType == PeriodType.CUSTOM) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.customStartMs?.let {
                            java.text.SimpleDateFormat("MMM dd", java.util.Locale.US).format(java.util.Date(it))
                        } ?: "Start",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("From") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(0.dp),
                        enabled = false,
                    )
                    OutlinedTextField(
                        value = state.customEndMs?.let {
                            java.text.SimpleDateFormat("MMM dd", java.util.Locale.US).format(java.util.Date(it))
                        } ?: "End",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("To") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(0.dp),
                        enabled = false,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodDropdown(
    selected: PeriodType,
    label: String,
    onSelected: (PeriodType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        PeriodType.WEEK to "This Week",
        PeriodType.MONTH to "This Month",
        PeriodType.CUSTOM to "Custom",
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(),
            textStyle = MaterialTheme.typography.labelLarge,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (type, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}
