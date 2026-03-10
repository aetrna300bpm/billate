package com.billate.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.billate.app.core.currency.MoneyFormatter
import com.billate.app.core.model.Category
import com.billate.app.core.model.LineItem
import com.billate.app.core.model.Transaction
import com.billate.app.viewmodel.LineItemEditMode
import com.billate.app.viewmodel.TransactionDetailUiState
import com.billate.app.viewmodel.TransactionDetailViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            is TransactionDetailUiState.Deleted -> {
                Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
                onBack()
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
                actions = {
                    if (isEditing) {
                        IconButton(onClick = {
                            (viewModel as? TransactionDetailViewModel)?.let { /* handled below */ }
                        }) {
                            // Delete handled in content via dialog
                        }
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
                    isExisting = state.isExisting,
                    lineItemEditMode = viewModel.lineItemEditMode,
                    onLineItemEditModeChange = { viewModel.lineItemEditMode = it },
                    onMerchantChange = viewModel::updateName,
                    onNoteChange = viewModel::updateNote,
                    onTotalChange = { viewModel.updateTotal(it) },
                    onCategoryChange = viewModel::updateCategory,
                    onTimestampChange = viewModel::updateTimestamp,
                    onLineItemChange = viewModel::updateLineItem,
                    onAddLineItem = viewModel::addLineItem,
                    onRemoveLineItem = viewModel::removeLineItem,
                    onServiceChargeChange = viewModel::updateServiceCharge,
                    onDiscountChange = viewModel::updateDiscount,
                    onTaxChange = viewModel::updateTax,
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
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
                // Initial / Saved / Deleted / Error — handled via LaunchedEffect
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailContent(
    transaction: Transaction,
    isExisting: Boolean,
    lineItemEditMode: LineItemEditMode,
    onLineItemEditModeChange: (LineItemEditMode) -> Unit,
    onMerchantChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onTotalChange: (Long) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onTimestampChange: (Long) -> Unit,
    onLineItemChange: (Int, LineItem) -> Unit,
    onAddLineItem: () -> Unit,
    onRemoveLineItem: (Int) -> Unit,
    onServiceChargeChange: (Long?) -> Unit,
    onDiscountChange: (Long?) -> Unit,
    onTaxChange: (Long?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currencyCode = transaction.amount.currency
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Confidence indicator ──
        val extractionConfidence = when (transaction) {
            is Transaction.Receipt -> transaction.extractionConfidence
            is Transaction.WireTransfer -> transaction.extractionConfidence
            is Transaction.Manual -> 1.0f
        }
        if (extractionConfidence < 1.0f) {
            item {
                val pct = (extractionConfidence * 100).toInt()
                val color = if (pct < 50) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.tertiary
                Card(
                    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "⚠️ Extraction confidence: $pct%. Please verify the details below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        // ── Receipt/WireTransfer image thumbnail ──
        val imageUri = when (transaction) {
            is Transaction.Receipt -> transaction.imageUri
            is Transaction.WireTransfer -> transaction.imageUri
            is Transaction.Manual -> null
        }
        if (imageUri != null) {
            item {
                val receiptsDir = remember {
                    File(context.filesDir, "receipts").also { it.mkdirs() }
                }
                val imageFile = remember(imageUri) {
                    File(receiptsDir, imageUri)
                }
                if (imageFile.exists()) {
                    var showFullScreen by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFullScreen = true },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = imageFile),
                            contentDescription = "Receipt image — tap to enlarge",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    if (showFullScreen) {
                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = { showFullScreen = false },
                            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                        ) {
                            com.billate.app.ui.components.FullScreenImageViewer(
                                imageFile = imageFile,
                                onDismiss = { showFullScreen = false },
                            )
                        }
                    }
                }
            }
        }

        // ── Date picker ──
        item {
            OutlinedTextField(
                value = dateFormat.format(Date(transaction.timestamp)),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                singleLine = true,
                enabled = false, // visual cue that tapping opens picker
            )
        }

        // ── Note ──
        item {
            OutlinedTextField(
                value = transaction.note,
                onValueChange = onNoteChange,
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        // ── Total amount ──
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
            if (transaction is Transaction.Receipt && transaction.totalAmountRaw.isNotBlank()) {
                Text(
                    text = "Raw from receipt: ${transaction.totalAmountRaw}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
        }

        // ── Category dropdown ──
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

        // ── Name field (for Manual & WireTransfer) ──
        if (transaction !is Transaction.Receipt) {
            item {
                OutlinedTextField(
                    value = transaction.name,
                    onValueChange = onMerchantChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }

        // ══════════════════════════════════════════════
        // RECEIPT SECTION — only for Receipt type
        // ══════════════════════════════════════════════
        if (transaction is Transaction.Receipt) {
            // ── Merchant ──
            item {
                OutlinedTextField(
                    value = transaction.name,
                    onValueChange = onMerchantChange,
                    label = { Text("Merchant Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // ── Receipt date raw ──
            if (transaction.transactionDateRaw.isNotBlank()) {
                item {
                    OutlinedTextField(
                        value = transaction.transactionDateRaw,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date from receipt") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            // ── Line item edit mode toggle ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("When editing items:", style = MaterialTheme.typography.labelMedium)
                    FilterChip(
                        selected = lineItemEditMode == LineItemEditMode.RECALCULATE_TOTAL,
                        onClick = { onLineItemEditModeChange(LineItemEditMode.RECALCULATE_TOTAL) },
                        label = { Text("Update total") },
                    )
                    FilterChip(
                        selected = lineItemEditMode == LineItemEditMode.KEEP_TOTAL,
                        onClick = { onLineItemEditModeChange(LineItemEditMode.KEEP_TOTAL) },
                        label = { Text("Keep total") },
                    )
                }
            }

            // ── Line items header ──
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

            // ── Line items ──
            itemsIndexed(transaction.lineItems) { index, item ->
                DetailLineItemCard(
                    item = item,
                    index = index,
                    currencyCode = currencyCode,
                    onUpdate = { updated -> onLineItemChange(index, updated) },
                    onRemove = { onRemoveLineItem(index) },
                )
            }

            // ── Adjustments section ──
            if (transaction.serviceCharge != null ||
                transaction.discount != null ||
                transaction.tax != null
            ) {
                item {
                    Text("Adjustments", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Service charge
            if (transaction.serviceCharge != null) {
                item {
                    AdjustmentField(
                        label = "Service Charge",
                        amountMinor = transaction.serviceCharge.amountMinor,
                        currencyCode = currencyCode,
                        onChange = onServiceChargeChange,
                    )
                }
            }

            // Discount (negative)
            if (transaction.discount != null) {
                item {
                    AdjustmentField(
                        label = "Discount",
                        amountMinor = transaction.discount.amountMinor,
                        currencyCode = currencyCode,
                        onChange = onDiscountChange,
                    )
                }
            }

            // Tax
            if (transaction.tax != null) {
                item {
                    AdjustmentField(
                        label = "Tax",
                        amountMinor = transaction.tax.amountMinor,
                        currencyCode = currencyCode,
                        onChange = onTaxChange,
                    )
                }
            }

            // ── Notes from receipt ──
            if (transaction.note.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Receipt Notes", style = MaterialTheme.typography.labelMedium)
                            Text(transaction.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════
        // WIRE TRANSFER SECTION — only for WireTransfer type
        // ══════════════════════════════════════════════
        if (transaction is Transaction.WireTransfer) {
            // ── Original recipient (read-only) ──
            if (transaction.recipientName.isNotBlank()) {
                item {
                    Text(
                        text = "Recipient: ${transaction.recipientName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }

            // ── Extraction confidence ──
            if (transaction.extractionConfidence < 1.0f) {
                item {
                    val pct = (transaction.extractionConfidence * 100).toInt()
                    Text(
                        text = "Extraction confidence: $pct%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        // ── Save button ──
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }

        // ── Delete button (only for existing transactions) ──
        if (isExisting) {
            item {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Transaction")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        } else {
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // ── Date picker dialog ──
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = transaction.timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { onTimestampChange(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Delete confirmation dialog ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction?") },
            text = { Text("This action cannot be undone.") },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
        )
    }
}

@Composable
private fun AdjustmentField(
    label: String,
    amountMinor: Long,
    currencyCode: String,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = amountMinor.toString(),
        onValueChange = { text ->
            if (text.isBlank()) {
                onChange(null)
            } else {
                text.toLongOrNull()?.let { onChange(it) }
            }
        },
        label = { Text("$label ($currencyCode)") },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = {
            val money = com.billate.app.core.model.Money(amountMinor, currencyCode)
            Text("Display: ${MoneyFormatter.format(money)}")
        },
    )
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
