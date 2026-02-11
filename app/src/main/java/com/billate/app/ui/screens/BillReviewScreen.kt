package com.billate.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.billate.app.model.BillTransaction
import com.billate.app.model.Category
import com.billate.app.model.LineItem
import com.billate.app.viewmodel.BillReviewViewModel
import com.billate.app.viewmodel.ReviewUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillReviewScreen(
    initialBill: BillTransaction?,
    billId: Long?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: BillReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (initialBill != null) {
            viewModel.loadBill(initialBill)
        } else if (billId != null) {
            viewModel.loadBillById(billId)
        }
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is ReviewUiState.Saved -> {
                Toast.makeText(context, "Bill saved!", Toast.LENGTH_SHORT).show()
                onSaved()
            }
            is ReviewUiState.Error -> {
                Toast.makeText(context, (uiState as ReviewUiState.Error).message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            val isEditing = (uiState as? ReviewUiState.Editing)?.isExisting == true
            TopAppBar(
                title = { Text(if (isEditing) "Edit Bill" else "Review Bill") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is ReviewUiState.Editing -> {
                ReviewContent(
                    bill = state.bill,
                    onMerchantChange = viewModel::updateMerchant,
                    onDateChange = viewModel::updateDate,
                    onTotalChange = { viewModel.updateTotal(it) },
                    onCategoryChange = viewModel::updateCategory,
                    onLineItemChange = viewModel::updateLineItem,
                    onAddLineItem = viewModel::addLineItem,
                    onRemoveLineItem = viewModel::removeLineItem,
                    onSave = viewModel::saveBill,
                    modifier = Modifier.padding(padding),
                )
            }
            is ReviewUiState.Saving -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Saving…")
                }
            }
            else -> {
                // Initial / Saved / Error — handled via LaunchedEffect
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewContent(
    bill: BillTransaction,
    onMerchantChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTotalChange: (Long) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onLineItemChange: (Int, LineItem) -> Unit,
    onAddLineItem: () -> Unit,
    onRemoveLineItem: (Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Merchant
        item {
            OutlinedTextField(
                value = bill.merchantName,
                onValueChange = onMerchantChange,
                label = { Text("Merchant Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        // Date
        item {
            OutlinedTextField(
                value = bill.transactionDate,
                onValueChange = onDateChange,
                label = { Text("Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (bill.transactionDateRaw.isNotBlank() && bill.transactionDateRaw != bill.transactionDate) {
                Text(
                    text = "Raw: ${bill.transactionDateRaw}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
        }

        // Total
        item {
            OutlinedTextField(
                value = bill.totalAmountVnd.toString(),
                onValueChange = { text ->
                    text.toLongOrNull()?.let { onTotalChange(it) }
                },
                label = { Text("Total (VND)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (bill.totalAmountRaw.isNotBlank()) {
                Text(
                    text = "Raw: ${bill.totalAmountRaw}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
        }

        // Category dropdown
        item {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = bill.category.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    Category.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.displayName) },
                            onClick = {
                                onCategoryChange(category)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        // Line items header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Line Items", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onAddLineItem) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        // Line items
        itemsIndexed(bill.lineItems) { index, item ->
            LineItemCard(
                item = item,
                index = index,
                onUpdate = { updated -> onLineItemChange(index, updated) },
                onRemove = { onRemoveLineItem(index) },
            )
        }

        // Notes
        item {
            if (bill.notes.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Notes", style = MaterialTheme.typography.labelMedium)
                        Text(bill.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Save button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Bill")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LineItemCard(
    item: LineItem,
    index: Int,
    onUpdate: (LineItem) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${index + 1}", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove item",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            OutlinedTextField(
                value = item.description,
                onValueChange = { onUpdate(item.copy(description = it)) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.qty.toString(),
                    onValueChange = { text ->
                        text.toIntOrNull()?.let { onUpdate(item.copy(qty = it)) }
                    },
                    label = { Text("Qty") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = item.amountVnd.toString(),
                    onValueChange = { text ->
                        text.toLongOrNull()?.let { onUpdate(item.copy(amountVnd = it)) }
                    },
                    label = { Text("Amount (VND)") },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
}
