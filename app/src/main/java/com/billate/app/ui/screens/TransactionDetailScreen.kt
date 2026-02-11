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
import com.billate.app.core.currency.MoneyFormatter
import com.billate.app.core.model.Category
import com.billate.app.core.model.LineItem
import com.billate.app.core.model.Transaction
import com.billate.app.viewmodel.TransactionDetailUiState
import com.billate.app.viewmodel.TransactionDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    initialTransaction: Transaction?,
    transactionId: Long?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (initialTransaction != null) {
            viewModel.loadTransaction(initialTransaction)
        } else if (transactionId != null) {
            viewModel.loadTransactionById(transactionId)
        }
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is TransactionDetailUiState.Saved -> {
                Toast.makeText(context, "Transaction saved!", Toast.LENGTH_SHORT).show()
                onSaved()
            }
            is TransactionDetailUiState.Error -> {
                Toast.makeText(context, (uiState as TransactionDetailUiState.Error).message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            val isEditing = (uiState as? TransactionDetailUiState.Editing)?.isExisting == true
            TopAppBar(
                title = { Text(if (isEditing) "Edit Transaction" else "Review Transaction") },
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
            is TransactionDetailUiState.Editing -> {
                TransactionDetailContent(
                    transaction = state.transaction,
                    onMerchantChange = viewModel::updateMerchant,
                    onNoteChange = viewModel::updateNote,
                    onTotalChange = { viewModel.updateTotal(it) },
                    onCategoryChange = viewModel::updateCategory,
                    onLineItemChange = viewModel::updateLineItem,
                    onAddLineItem = viewModel::addLineItem,
                    onRemoveLineItem = viewModel::removeLineItem,
                    onSave = viewModel::save,
                    modifier = Modifier.padding(padding),
                )
            }
            is TransactionDetailUiState.Saving -> {
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
private fun TransactionDetailContent(
    transaction: Transaction,
    onMerchantChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onTotalChange: (Long) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onLineItemChange: (Int, LineItem) -> Unit,
    onAddLineItem: () -> Unit,
    onRemoveLineItem: (Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currencyCode = transaction.amount.currency

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Note (for all transactions)
        item {
            OutlinedTextField(
                value = transaction.note,
                onValueChange = onNoteChange,
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        // Total amount
        item {
            OutlinedTextField(
                value = transaction.amount.amountMinor.toString(),
                onValueChange = { text ->
                    text.toLongOrNull()?.let { onTotalChange(it) }
                },
                label = { Text("Total (minor units, $currencyCode)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Text(
                text = "Display: ${MoneyFormatter.format(transaction.amount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            if (transaction.bill?.totalAmountRaw?.isNotBlank() == true) {
                Text(
                    text = "Raw from receipt: ${transaction.bill.totalAmountRaw}",
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
                    value = transaction.category.displayName,
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

        // Bill section (only if bill is present, i.e., scanned receipt)
        if (transaction.bill != null) {
            // Merchant
            item {
                OutlinedTextField(
                    value = transaction.bill.merchantName,
                    onValueChange = onMerchantChange,
                    label = { Text("Merchant Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // Receipt date raw
            if (transaction.bill.transactionDateRaw.isNotBlank()) {
                item {
                    OutlinedTextField(
                        value = transaction.bill.transactionDateRaw,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date from receipt") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
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
            itemsIndexed(transaction.bill.lineItems) { index, item ->
                DetailLineItemCard(
                    item = item,
                    index = index,
                    currencyCode = currencyCode,
                    onUpdate = { updated -> onLineItemChange(index, updated) },
                    onRemove = { onRemoveLineItem(index) },
                )
            }

            // Notes from receipt
            if (transaction.bill.notes.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Receipt Notes", style = MaterialTheme.typography.labelMedium)
                            Text(transaction.bill.notes, style = MaterialTheme.typography.bodySmall)
                        }
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
                Text("Save")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailLineItemCard(
    item: LineItem,
    index: Int,
    currencyCode: String,
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
                    value = item.amount.amountMinor.toString(),
                    onValueChange = { text ->
                        text.toLongOrNull()?.let { onUpdate(item.copy(amount = item.amount.copy(amountMinor = it))) }
                    },
                    label = { Text("Amount ($currencyCode)") },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
}
