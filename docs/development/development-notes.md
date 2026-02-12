# Development Notes: Phase 2 (UI/UX + Bill Refinement)

**Status:** ✅ Implementation Complete (pending testing)  
**Last Updated:** July 2025

> This document outlines the upcoming enhancements to make the app more user-friendly and feature-complete. It covers three areas: **Bill object structure**, **Gemini prompt & parsing**, and **UI/UX updates**.

---

## 1. Bill Object Structure Refinement

### Current State
```kotlin
data class Bill(
    val merchantName: String = "",
    val transactionDateRaw: String = "",
    val totalAmountRaw: String = "",
    val lineItems: List<LineItem> = emptyList(),
    val notes: String = "",
    val imageUri: String? = null,
)
```

**Problem:** No way to represent tax, discount, or service charge separately. Users can't distinguish what they actually bought vs. what was added/subtracted.

### Proposed Structure

```kotlin
data class Bill(
    val merchantName: String = "",
    val transactionDateRaw: String = "",
    val totalAmountRaw: String = "",
    
    // Core transaction items (what the user bought)
    val lineItems: List<LineItem> = emptyList(),
    
    // Adjustments (transparent, non-item charges/credits)
    val serviceCharge: Money? = null,           // e.g., "Service: 20,000 VND"
    val discount: Money? = null,                // e.g., "Discount: -10,000 VND"
    val tax: Money? = null,                     // e.g., "Tax: 10,000 VND" (if shown separately on receipt)
    
    // Metadata
    val notes: String = "",
    val imageUri: String? = null,
    val extractionConfidence: Float = 1.0f,    // 0.0-1.0 (optional: indicates if OCR was uncertain)
)
```

### Rationale

**User perspective:**
- Line items represent what they purchased
- Service charge, tax, discount are visible adjustments
- Clear calculation path: `sum(lineItems) + serviceCharge - discount + tax = totalAmount`

**Implementation advantage:**
- Service charge/discount/tax are optional fields, so existing data doesn't break
- Simple to display in UI (show as rows in the line items list with different styling)
- Easy to edit (adjust service charge percentage, update discount amount)
- Aligns with how receipts typically present this information

### Database Migration Strategy

**TransactionEntity** gets new nullable columns:

```kotlin
@Entity(tableName = "transactions")
data class TransactionEntity(
    // ... existing fields ...
    
    // NEW: Bill adjustments
    val serviceChargeMinor: Long? = null,
    val serviceChargeCurrency: String? = null,
    val discountMinor: Long? = null,
    val discountCurrency: String? = null,
    val taxMinor: Long? = null,
    val taxCurrency: String? = null,
    
    // Optional
    val extractionConfidence: Float = 1.0f,
)
```

**Mapper changes:**
```kotlin
// Entity → Domain
val bill = if (hasBill) {
    Bill(
        // ... existing fields ...
        serviceCharge = if (tx.serviceChargeMinor != null) 
            Money(tx.serviceChargeMinor!!, tx.serviceChargeCurrency!!) else null,
        discount = if (tx.discountMinor != null)
            Money(tx.discountMinor!!, tx.discountCurrency!!) else null,
        tax = if (tx.taxMinor != null)
            Money(tx.taxMinor!!, tx.taxCurrency!!) else null,
        extractionConfidence = tx.extractionConfidence,
    )
} else null

// Domain → Entity
TransactionEntity(
    // ... existing fields ...
    serviceChargeMinor = bill?.serviceCharge?.amountMinor,
    serviceChargeCurrency = bill?.serviceCharge?.currency,
    discountMinor = bill?.discount?.amountMinor,
    discountCurrency = bill?.discount?.currency,
    taxMinor = bill?.tax?.amountMinor,
    taxCurrency = bill?.tax?.currency,
    extractionConfidence = bill?.extractionConfidence ?: 1.0f,
)
```

---

## 2. Gemini Prompt & Parsing Strategy

### Current Prompt Issues

1. **No distinction** between what was bought vs. what was added/subtracted
2. **No confidence metric** — can't tell if extraction was reliable
3. **Brittle JSON parsing** — fails silently or throws on bad data

### Proposed Gemini Response Schema

```json
{
  "merchant_name": "string",
  "transaction_date": "YYYY-MM-DD",
  "transaction_date_raw": "string",
  "currency": "ISO 4217 3-letter code",
  
  "line_items": [
    {
      "description": "string (what was bought)",
      "qty": integer,
      "unit_price": integer (in minor units),
      "amount": integer (qty × unit_price, in minor units),
      "amount_raw": "string"
    }
  ],
  
  "adjustments": {
    "subtotal": integer (sum of line items, in minor units),
    "service_charge": {
      "amount": integer (in minor units),
      "amount_raw": "string (e.g., '5% Service Charge')"
    },
    "discount": {
      "amount": integer (NEGATIVE — e.g., -10000),
      "amount_raw": "string (e.g., '10% Off')"
    },
    "tax": {
      "amount": integer (in minor units, if shown separately),
      "amount_raw": "string (e.g., 'VAT 10%')"
    }
  },
  
  "final_total": integer (in minor units — this is the authoritative total),
  "total_amount_raw": "string (exactly as printed on receipt)",
  
  "category": "one of: Groceries, Dining, Shopping, Transport, Utilities, Health, Entertainment, Education, Other",
  "notes": "string (any discrepancies, unclear items, or special notes)",
  "confidence": 0.0-1.0 (float, 1.0 = fully confident, 0.5 = some uncertainty, 0.0 = very unreliable)
}
```

### Updated Prompt Text

```
You are an OCR assistant. Extract data from receipt images and return only valid JSON.

Key instructions:
1. Output ONLY valid JSON. No markdown, no extra text.
2. Always include "final_total" — this is the amount the customer paid.
3. Extract line items as PURCHASED ITEMS (what the customer bought).
4. Service charges, discounts, and taxes are ADJUSTMENTS, not items.
5. All amounts are integers in the currency's smallest unit (e.g., cents for USD, đồng for VND).
6. If you're unsure about any field, set confidence < 1.0 and explain in notes.
7. Detect the currency from the receipt and report as ISO 4217 code.

Calculation rule:
  final_total = sum(line_items) + service_charge - discount + tax

Rules for each section:

LINE ITEMS:
- Extract each item the customer purchased.
- Include qty and unit_price if visible.
- Amount = qty × unit_price (or exact amount if printed).
- Keep amount_raw exactly as printed.

ADJUSTMENTS:
- service_charge: Gratuity, delivery fee, service tax, etc. (positive number)
- discount: Promotional discount, coupon, membership discount (NEGATIVE number)
- tax: Sales tax, VAT, or other tax if shown separately (positive number)
- All are OPTIONAL. If not present on receipt, omit or use 0.

CONFIDENCE:
- 1.0: Receipt is clear, all items legible, numbers match
- 0.8: Mostly clear but some items partially obscured
- 0.5: Significant uncertainty in items or totals
- < 0.5: Receipt is too damaged/unclear to rely on

Example response:
{
  "merchant_name": "Pho House",
  "transaction_date": "2026-02-11",
  "transaction_date_raw": "11/02/2026",
  "currency": "VND",
  "line_items": [
    {"description": "Pho Bo", "qty": 2, "unit_price": 80000, "amount": 160000, "amount_raw": "80.000 x 2"},
    {"description": "Bia Saigon", "qty": 1, "unit_price": 25000, "amount": 25000, "amount_raw": "25.000"}
  ],
  "adjustments": {
    "subtotal": 185000,
    "service_charge": {"amount": 0, "amount_raw": ""},
    "discount": {"amount": 0, "amount_raw": ""},
    "tax": {"amount": 0, "amount_raw": ""}
  },
  "final_total": 185000,
  "total_amount_raw": "185.000 VND",
  "category": "Dining",
  "notes": "Clear receipt, all items legible",
  "confidence": 1.0
}
```

### Parsing Strategy

**Update `GeminiReceiptResponse` data class:**

```kotlin
data class GeminiReceiptResponse(
    val merchantName: String,
    val transactionDate: String,
    val transactionDateRaw: String,
    val currency: String,
    
    val lineItems: List<LineItemDto>,
    
    // Adjustments (nullable, use 0 if omitted)
    val serviceCharge: Long? = null,
    val serviceChargeRaw: String? = null,
    val discount: Long? = null,                 // NEGATIVE for discounts
    val discountRaw: String? = null,
    val tax: Long? = null,
    val taxRaw: String? = null,
    
    val finalTotal: Long,
    val totalAmountRaw: String,
    
    val category: String,
    val notes: String,
    val confidence: Float = 1.0f,
)

data class LineItemDto(
    val description: String,
    val qty: Int,
    val unitPrice: Long? = null,
    val amount: Long,
    val amountRaw: String,
)
```

**Update `ProcessReceiptUseCase` mapping logic:**

```kotlin
private fun mapResponseToTransaction(
    response: GeminiReceiptResponse,
    imageFilename: String,
): Transaction {
    val currency = response.currency.ifBlank { "VND" }
    
    val lineItems = response.lineItems.map { item ->
        LineItem(
            description = item.description,
            qty = item.qty,
            amount = Money(item.amount, currency),
            amountRaw = item.amountRaw,
        )
    }
    
    val bill = Bill(
        merchantName = response.merchantName,
        transactionDateRaw = response.transactionDateRaw,
        totalAmountRaw = response.totalAmountRaw,
        lineItems = lineItems,
        
        // NEW: Adjustments
        serviceCharge = response.serviceCharge?.let { Money(it, currency) },
        discount = response.discount?.let { Money(it, currency) },  // Will be negative for actual discounts
        tax = response.tax?.let { Money(it, currency) },
        
        notes = response.notes,
        imageUri = imageFilename,
        extractionConfidence = response.confidence,
    )
    
    val timestamp = parseDate(response.transactionDate)
    
    return Transaction(
        timestamp = timestamp,
        amount = Money(response.finalTotal, currency),
        category = Category.fromString(response.category) ?: Category.Other,
        bill = bill,
    )
}
```

**Validation strategy:**

```kotlin
private fun validate(transaction: Transaction): String? {
    if (transaction.bill?.merchantName.isNullOrBlank()) {
        return "Merchant name is missing"
    }
    if (transaction.amount.amountMinor <= 0) {
        return "Total amount must be positive"
    }
    if (transaction.bill?.extractionConfidence ?: 1.0f < 0.5f) {
        return "Receipt unclear (confidence: ${transaction.bill?.extractionConfidence}). Please review."
    }
    return null
}
```

---

## 3. UI/UX Updates & Refactoring

### A. HomeScreen Updates

**Changes needed:**

1. **FAB button behavior** — Instead of direct camera/gallery dialog, show:
   - "Scan Receipt" (camera/gallery) — requires API key
   - "Add Manually" — no API key needed
   - "Cancel"

2. **Transaction card styling** — Add visual indicators:
   - Receipt icon badge for scanned receipts
   - Different text color or font weight for manual entries
   - Show service charge/discount if present (e.g., "150k (+ 5k service)")

**Pseudocode:**
```kotlin
FloatingActionButton(onClick = {
    showAddDialog = true  // NEW: three options
}) { Icon(Icons.Default.Add, "Add") }

if (showAddDialog) {
    AlertDialog(
        title = { Text("Add Transaction") },
        text = {
            Column {
                if (viewModel.hasApiKey()) {
                    Row(...) { Text("Scan Receipt") }  // Camera + Gallery
                }
                Row(...) { Text("Add Manually") }      // Blank transaction
            }
        },
    )
}

// On "Add Manually":
navController.navigate("create")  // NEW ROUTE

// In navHost:
composable("create") {
    TransactionDetailScreen(
        initialTransaction = Transaction(
            timestamp = System.currentTimeMillis(),
            amount = Money(0, DEFAULT_CURRENCY),  // From Settings
            category = Category.Other,
        ),
        onSaved = { navController.popBackStack() },
    )
}
```

### B. TransactionDetailScreen Updates

**Changes needed:**

1. **Amount input refinement** — Accept user-friendly input (major units):
   - VND: User types `150000` → stored as `Money(150000, "VND")`
   - USD: User types `12.99` → stored as `Money(1299, "USD")`
   - Use `MoneyFormatter.parseToMinor()` internally

2. **Bill section conditional rendering:**
   - If `bill == null` → hide merchant, line items, etc.
   - If `bill != null` → show all bill details (current behavior)

3. **New bill adjustments section** (when bill is present):
   ```
   Service Charge: [input field] or [display if read-only from receipt]
   Discount: [input field] or [display if read-only from receipt]
   Tax: [input field] or [display if read-only from receipt]
   ```

4. **Receipt image thumbnail** — If `bill.imageUri` exists:
   ```kotlin
   Card {
       AsyncImage(
           model = File(context.filesDir, "receipts/${bill.imageUri}"),
           contentDescription = "Receipt",
           modifier = Modifier.height(150.dp).clickable { showFullImage = true }
       )
   }
   ```

5. **Confidence indicator** — If extraction confidence < 1.0:
   ```
   Text("⚠️ Receipt unclear (${(confidence * 100).toInt()}% confident). Please verify.")
   ```

6. **Date picker** — For manual entries or editing:
   ```kotlin
   Button(onClick = { showDatePicker = true }) {
       Text("Date: ${formatDate(transaction.timestamp)}")
   }
   ```

**Pseudocode for amount field:**
```kotlin
OutlinedTextField(
    value = displayAmount,  // User-readable (major units)
    onValueChange = { newDisplayValue ->
        val minorUnits = MoneyFormatter.parseToMinor(newDisplayValue, currencyCode)
        if (minorUnits != null) {
            viewModel.updateTotal(minorUnits)
        }
    },
    label = { Text("Total Amount") },
)
Text("Display: ${MoneyFormatter.format(transaction.amount)}")
```

### C. New Manual Transaction Route

**Route:** `composable("create")`
- Opens `TransactionDetailScreen` with blank `Transaction`
- No receipt image, merchant, line items initially visible
- User fills: note, amount, category, date
- Save button inserts into DB

### D. Delete Functionality

**Options:**

**Option 1:** Swipe-to-dismiss on list
```kotlin
items(...) { tx ->
    SwipeToDismiss(
        state = rememberDismissState(
            confirmValueChange = {
                viewModel.deleteTransaction(tx.id)
                true
            }
        ),
        background = { /* Red delete background */ },
        dismissContent = { TransactionCard(...) }
    )
}
```

**Option 2:** Delete button on detail screen
```kotlin
TopAppBar(
    title = { Text("Edit Transaction") },
    actions = {
        IconButton(onClick = {
            showDeleteConfirm = true
        }) { Icon(Icons.Default.Delete, "Delete") }
    }
)

if (showDeleteConfirm) {
    AlertDialog(
        title = { Text("Delete Transaction?") },
        text = { Text("This cannot be undone.") },
        dismissButton = { TextButton(onClick = { ... }) { Text("Cancel") } },
        confirmButton = { Button(onClick = {
            viewModel.deleteTransaction(tx.id)
            onBack()
        }) { Text("Delete") } }
    )
}
```

**Recommendation:** Start with **Option 2** (detail screen button). It's clearer for the user. Swipe-to-dismiss is a nice-to-have for Phase 3.

### E. Settings: Default Currency

**New setting in `SettingsViewModel` & `ApiKeyManager`:**

```kotlin
fun getDefaultCurrency(): String = prefs.getString("default_currency", "VND")
fun setDefaultCurrency(code: String) = prefs.edit().putString("default_currency", code).apply()
```

**In SettingsScreen:**
```kotlin
// New section
CurrencyDropdown(
    selected = uiState.defaultCurrency,
    options = listOf("VND", "USD", "EUR", "JPY"),  // or all known currencies
    onSelected = viewModel::setDefaultCurrency,
)
```

**Used in:**
- Manual transaction creation: `Money(0, apiKeyManager.getDefaultCurrency())`
- Receipt processing: If Gemini detects wrong currency, we could optionally convert (future)

---

## 4. Implementation Priority & Tasks

### Phase 2a: Bill Object & Database (Foundation)
- [x] Update `Bill` data class with `serviceCharge`, `discount`, `tax`, `extractionConfidence`
- [x] Update `TransactionEntity` with new nullable columns
- [x] Update `TransactionMappers` for new fields
- [x] Build and verify database migration

### Phase 2b: Gemini Prompt & Parsing (Backend)
- [x] Update `GeminiReceiptResponse` with new schema
- [x] Update `ReceiptExtractor` prompt
- [x] Update `ProcessReceiptUseCase` mapping logic
- [ ] Test with sample receipts (different currencies, with/without tax, with discounts)

### Phase 2c: Manual Transaction Entry (Core Feature)
- [x] Add `"create"` route in `BillateNavHost`
- [x] Update `HomeScreen` FAB to show "Scan Receipt" / "Add Manually" dialog
- [x] Update `SettingsViewModel` & `ApiKeyManager` for default currency
- [ ] Test creating manual transactions with various amounts & categories

### Phase 2d: UI Refinements (Polish)
- [x] **TransactionDetailScreen:** Conditional rendering of bill section
- [x] **TransactionDetailScreen:** Add service charge / discount / tax fields
- [x] **TransactionDetailScreen:** Add receipt image thumbnail viewer
- [x] **TransactionDetailScreen:** Add confidence indicator
- [x] **TransactionDetailScreen:** Add date picker
- [x] **TransactionDetailScreen:** Add line item edit mode toggle (recalculate total vs keep total)
- [x] **HomeScreen:** Update transaction card styling (receipt badge)
- [x] **Delete functionality:** Add delete button to detail screen with confirmation

### Phase 2e: Settings & Polish (Final)
- [x] **SettingsScreen:** Add default currency dropdown
- [ ] Test all workflows: manual entry, receipt scan, edit, delete
- [x] Build & verify

---

## 5. ~~Questions & Decisions Needed~~ (All Resolved — see §6)

1. **Confidence threshold for auto-save** — Currently `validate()` checks `confidence < 0.5`. Should this be:
   - Stricter: `< 0.8` (most receipts go to review)
   - Looser: `< 0.3` (only very bad receipts trigger review)
   - Or always require manual review for receipts (auto-save is disabled)?

2. **Service charge / discount UI** — When editing a transaction, should these fields be:
   - Editable (user can change 5% service to 10% or remove it)?
   - Read-only from receipt (can only be edited if manually entered)?
   - Both (show original from receipt, allow override)?

3. **Line item editing for scanned receipts** — Currently users can add/remove/edit line items. Should this be:
   - Allowed (as currently)?
   - Read-only (can view, but can't change)?
   - Optional per item (some items locked, some editable)?

4. **Discount representation** — Should a discount be:
   - A negative `Money` amount (e.g., `-10000`)?
   - Stored as positive and negated in display?
   - Current approach works, just want to clarify for consistency.

5. **Currency conversion** — If Gemini detects USD on a receipt but the user's default is VND, should the app:
   - Store as-is in USD?
   - Ask user to convert/confirm?
   - Auto-convert at today's rate (add exchange rate API)?
   - Just store as-is and let user manually adjust if needed?

6. **Photo/image display** — For receipt image thumbnail in detail screen:
   - Use Coil library (cleaner, more features)?
   - Use Android built-in `BitmapFactory` + `Image` (fewer dependencies)?
   - Just link to file browser (minimal UI)?

---

## 6. Design Decisions (Resolved)

1. **Confidence threshold:** `0.3` — very low bar; users can always edit later with the receipt image visible.
2. **Adjustments (service charge, discount, tax):** Only shown if explicitly listed on the receipt. Editable amounts (minor units). Multiple tax brackets summed into one `tax` field.
3. **Line item editing:** Allowed. When editing items, user can toggle between two modes:
   - **Recalculate total** (default): total = sum(items) + adjustments. Used for correcting OCR errors.
   - **Keep total**: total unchanged. Used for bill splitting (removing items you didn't order).
4. **Discount:** Stored as negative `Money` amount.
5. **Currency:** Stored as-is from receipt. No auto-conversion. Default currency (configurable in Settings) used for manual entries.
6. **Receipt image:** Displayed in-app using Coil `AsyncImage` (already a dependency). Shown as a 200dp thumbnail in the detail screen.

## 7. Implementation Summary

### Files Modified
| Layer | File | Changes |
|-------|------|---------|
| Domain | `Bill.kt` | Added `serviceCharge`, `discount`, `tax` (Money?), `extractionConfidence` |
| Data | `TransactionEntity.kt` | 7 new nullable columns for adjustments + confidence |
| Data | `TransactionMappers.kt` | Maps adjustment columns ↔ Money objects |
| Data | `BillateDatabase.kt` | Version bumped 2 → 3 |
| Remote | `GeminiReceiptResponse.kt` | Complete rewrite — `GeminiAdjustments`, `confidence`, `finalTotal` |
| Remote | `ReceiptExtractor.kt` | Prompt rewritten with adjustment rules |
| UseCase | `ProcessReceiptUseCase.kt` | Maps adjustments, confidence < 0.3 triggers review |
| ViewModel | `TransactionDetailViewModel.kt` | `LineItemEditMode`, adjustment update methods, delete, `maybeRecalculateTotal()` |
| UI | `TransactionDetailScreen.kt` | Confidence banner, receipt image, date picker, adjustments section, delete with confirmation, edit mode toggle |
| UI | `HomeScreen.kt` | FAB → "Take Photo" / "Gallery" / "Add Manually"; receipt badge on cards |
| Navigation | `BillateNavHost.kt` | Added `create` route for manual entry |
| Settings | `ApiKeyManager.kt` | `getDefaultCurrency()` / `setDefaultCurrency()` |
| Settings | `SettingsViewModel.kt` | Currency state + `onDefaultCurrencyChange()` |
| Settings | `SettingsScreen.kt` | Default currency dropdown card |

### Status
- ✅ Compiles successfully
- ⏳ Not yet tested on device
- ⏳ Not pushed to GitHub (user request)
