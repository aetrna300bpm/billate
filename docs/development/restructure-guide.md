# Billate App Restructuring Guide

**Last Updated:** February 2026  
**Status:** ✅ Complete & Built Successfully

## Table of Contents

1. [Overview](#overview)
2. [Architecture Principles](#architecture-principles)
3. [Detailed Layer Documentation](#detailed-layer-documentation)
4. [Core Concepts](#core-concepts)
5. [How to Add Features](#how-to-add-features)
6. [Common Development Patterns](#common-development-patterns)
7. [Data Flow Examples](#data-flow-examples)
8. [Database Schema](#database-schema)
9. [Navigation & Routes](#navigation--routes)
10. [Testing & Debugging](#testing--debugging)

---

## Overview

### What Changed

The app was restructured from a **Bill-centric architecture** to a **Transaction-centric, currency-agnostic architecture**. The old system treated bills as the primary entity; the new system treats **transactions** as primary, with bills as optional attachments for receipt-scanned transactions.

### Why These Changes?

1. **Transaction as Primary Entity** — Supports both manual entries (no bill) and scanned receipts (with bill)
2. **Currency-Agnostic Design** — Not hardcoded to VND; supports USD, EUR, JPY, and any ISO 4217 currency
3. **Modularity** — Clear separation of concerns across `core/`, `data/`, `domain/`, and `ui/` layers
4. **Fresh Start** — All new code, clean DB schema, no legacy migration burden

### Key Achievements

- ✅ **33 total `.kt` files** in clean modular structure
- ✅ **Build successful** — zero compilation errors
- ✅ **No old code** — completely removed legacy Bill-centric files
- ✅ **Currency support** — VND, USD, EUR, JPY built-in (extensible to any ISO 4217)
- ✅ **Image storage** — Receipt images saved to internal storage, deleted with transaction

---

## Architecture Principles

### 1. Clean Architecture Layers

```
┌─────────────────────────────────────────┐
│         UI Layer (Screens)              │ ← User Interface
├─────────────────────────────────────────┤
│     Presentation (ViewModels)           │ ← State & Logic
├─────────────────────────────────────────┤
│    Domain Layer (Use Cases)             │ ← Business Logic
├─────────────────────────────────────────┤
│   Data Layer (Repos, DAOs, API)         │ ← Data Access
├─────────────────────────────────────────┤
│    Core Layer (Models, Currency)        │ ← Definitions & Utilities
└─────────────────────────────────────────┘
```

**Dependency Rule:** Each layer depends only on layers below it. Never import from `ui/` or `viewmodel/` into lower layers.

### 2. Transaction Model as Core Entity

Every financial record is a **`Transaction`**:

```kotlin
data class Transaction(
    val id: Long = 0,
    val timestamp: Long,           // When it occurred
    val amount: Money,             // Normalized amount + currency
    val category: Category,        // Spending category
    val bill: Bill? = null,        // Optional receipt data
    val note: String = "",         // Free-text (useful for manual entries)
    val createdAt: Long = System.currentTimeMillis(),
)
```

- **Manual entries**: `bill = null`, use `note` field
- **Receipt scans**: `bill != null`, contains merchant, items, image

### 3. Currency-Agnostic Design

All monetary amounts use the `Money` value object:

```kotlin
data class Money(
    val amountMinor: Long,   // Stored in smallest unit (e.g., cents for USD, đồng for VND)
    val currency: String,    // ISO 4217 code: "VND", "USD", "EUR", "JPY"
)
```

**Benefits:**
- No hardcoded currency assumptions
- Each transaction can theoretically have different currency (though current design is single-currency-per-user)
- Extensible to multi-currency in future

**Currency Rules** are defined in `CurrencyConfig`:

```kotlin
data class CurrencyConfig(
    val code: String,              // "VND"
    val decimalPlaces: Int,        // 0 for VND, 2 for USD
    val minorPerMajor: Long,       // 1 for VND, 100 for USD
    val displayLocale: Locale,
    val symbol: String,            // "₫" or "$"
)
```

### 4. Single Responsibility Principle

Each file/class has **one reason to change**:

- `TransactionEntity` → Room representation
- `TransactionDao` → Database queries
- `TransactionMappers` → Entity ↔ Domain conversion
- `TransactionRepository` → Business logic for transaction persistence
- `HomeViewModel` → UI state for home screen
- `HomeScreen` → Composable rendering

---

## Detailed Layer Documentation

### Core Layer (`core/`)

**Purpose:** Domain models and utilities independent of Android/Room/API.

#### `core/model/`

| File | Responsibility |
|------|---|
| `Money.kt` | Currency-agnostic monetary value |
| `Transaction.kt` | Primary domain entity |
| `Bill.kt` | Optional receipt data (merchant, items, image) |
| `LineItem.kt` | Single receipt line item |
| `Category.kt` | Spending categories enum |

**Imports into this package:** None (leaf package)

**Usage Example:**
```kotlin
val transaction = Transaction(
    timestamp = System.currentTimeMillis(),
    amount = Money(1500000, "VND"),
    category = Category.Dining,
    bill = Bill(merchantName = "Pho Hoa", lineItems = listOf(...)),
)
```

#### `core/currency/`

| File | Responsibility |
|------|---|
| `CurrencyConfig.kt` | Per-currency rules (decimals, symbol, locale) |
| `MoneyFormatter.kt` | Format & parse `Money` for display |

**Key Function:** `MoneyFormatter.format(Money) → String`
```kotlin
MoneyFormatter.format(Money(150000, "VND"))  // → "150.000 ₫"
MoneyFormatter.format(Money(1299, "USD"))     // → "12.99 $"
```

**Key Function:** `MoneyFormatter.parseToMinor(String, String) → Long?`
```kotlin
MoneyFormatter.parseToMinor("12.99", "USD")  // → 1299
```

**Adding New Currency:** Edit `CurrencyConfig.forCode()`:
```kotlin
"GBP" -> CurrencyConfig("GBP", 2, 100, Locale.UK, "£")
```

---

### Data Layer (`data/`)

**Purpose:** All persistence, API communication, and data access logic.

#### `data/local/` — Room Database

| File | Responsibility |
|------|---|
| `TransactionEntity.kt` | Room entity (flattened bill fields as nullable columns) |
| `LineItemEntity.kt` | FK to TransactionEntity |
| `TransactionWithLineItems.kt` | Room @Relation for loading parent + children |
| `TransactionDao.kt` | All DB queries (CRUD, date range, composite operations) |
| `TransactionMappers.kt` | Extension functions: Entity ↔ Domain conversion |
| `BillateDatabase.kt` | Room DB (v2) with DAO accessor |
| `ApiKeyManager.kt` | SharedPreferences for API key & model selection |

**Database Schema:**

```sql
-- transactions table (all fields nullable except id, timestamp, amountMinor, currency, category, createdAt)
CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    amountMinor INTEGER NOT NULL,
    currency TEXT NOT NULL,
    category TEXT NOT NULL,
    note TEXT DEFAULT '',
    merchantName TEXT,              -- Bill field
    transactionDateRaw TEXT,        -- Bill field
    totalAmountRaw TEXT,            -- Bill field
    billNotes TEXT,                 -- Bill field
    billImageUri TEXT,              -- Bill field (filename in internal storage)
    createdAt INTEGER NOT NULL
)

-- line_items table (tied to transaction)
CREATE TABLE line_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transactionId INTEGER NOT NULL,
    description TEXT NOT NULL,
    qty INTEGER NOT NULL,
    amountMinor INTEGER NOT NULL,
    currency TEXT NOT NULL,
    amountRaw TEXT,
    FOREIGN KEY(transactionId) REFERENCES transactions(id) ON DELETE CASCADE
)
```

**Key DAO Operations:**

```kotlin
// Read
fun getAllWithLineItems(): Flow<List<TransactionWithLineItems>>
suspend fun getByIdWithLineItems(id: Long): TransactionWithLineItems?
fun getByDateRange(startMs: Long, endMs: Long): Flow<List<TransactionWithLineItems>>

// Write
suspend fun insertWithLineItems(entity: TransactionEntity, lineItems: List<LineItemEntity>): Long
suspend fun updateWithLineItems(entity: TransactionEntity, lineItems: List<LineItemEntity>)
suspend fun deleteTransaction(id: Long)

// Image URI lookup
suspend fun getImageUri(id: Long): String?
```

**Entity ↔ Domain Mapping:**

Use `TransactionMappers`:
```kotlin
// Entity → Domain
val domain: Transaction = dbRow.toDomain()

// Domain → Entity
val entity: TransactionEntity = transaction.toEntity()
val lineItems: List<LineItemEntity> = transaction.lineItemEntities()
```

**Important:** Bill fields in `TransactionEntity` are **all nullable**. When loading:
- If `merchantName == null` → no bill, entity represents manual transaction
- If `merchantName != null` → bill exists, reconstruct from flattened fields

---

#### `data/remote/` — Gemini API

| File | Responsibility |
|------|---|
| `GeminiReceiptResponse.kt` | Data class for Gemini API response (currency-agnostic) |
| `ReceiptExtractor.kt` | Calls Gemini API with receipt bitmap, returns `GeminiReceiptResponse` |

**API Response Structure:**
```kotlin
data class GeminiReceiptResponse(
    val merchantName: String,
    val transactionDate: String,        // "yyyy-MM-dd" format
    val transactionDateRaw: String,     // Original from receipt
    val totalAmount: Long,              // In minor units
    val totalAmountRaw: String,         // Original from receipt
    val currency: String,               // ISO 4217 code ("VND", "USD", etc.)
    val category: String,               // Category name
    val lineItems: List<LineItemDto>,
    val notes: String,
)
```

**Gemini Prompt:** Located in `ReceiptExtractor.kt`, requests:
1. Merchant name
2. Transaction date
3. Total amount **in the currency's smallest unit** (important!)
4. **Currency detection** (looks at receipt language, symbols)
5. Line items with amounts
6. Spending category

**Extending the API:**
To request additional data from Gemini:
1. Update `GeminiReceiptResponse` with new fields
2. Modify the prompt in `ReceiptExtractor.kt`
3. Update `ProcessReceiptUseCase.mapResponseToTransaction()` to use new fields

---

#### `data/repository/` — Business Logic Layer

| File | Responsibility |
|------|---|
| `TransactionRepository.kt` | Interface defining transaction operations |
| `DefaultTransactionRepository.kt` | Implementation using DAO + image storage |
| `ReceiptImageStorage.kt` | File I/O for receipt images in `context.filesDir/receipts/` |

**Repository Pattern Benefits:**
- Abstracts database/API calls from domain logic
- Enables testing via mock implementations
- Single place to add business logic (e.g., validation, image cleanup)

**Key Implementation Detail:**

When deleting a transaction, `DefaultTransactionRepository.delete()`:
1. Retrieves the image URI from the DB
2. Deletes the image file via `ReceiptImageStorage.deleteImage()`
3. Deletes the transaction from DB (cascades to line items)

```kotlin
override suspend fun delete(id: Long) {
    val imageUri = dao.getImageUri(id)
    if (imageUri != null) {
        imageStorage.deleteImage(imageUri)  // Delete file first
    }
    dao.deleteTransaction(id)  // Then delete DB record
}
```

---

### Domain Layer (`domain/usecase/`)

**Purpose:** Business logic orchestration and workflow.

#### `ProcessReceiptUseCase`

**Responsibility:** End-to-end receipt processing pipeline.

**Input:** `Uri` (image from camera/gallery)  
**Output:** `ReceiptProcessingResult` (sealed class)

**Flow:**
1. Read bitmap from URI
2. Extract data via Gemini
3. Save image to internal storage
4. Build `Transaction` domain object
5. Validate (merchant name, positive amount)
6. Auto-save if valid, or request manual review if issues

**Result Types:**
```kotlin
sealed class ReceiptProcessingResult {
    data class AutoSaved(val transaction: Transaction) : ReceiptProcessingResult()
    data class ReviewNeeded(val transaction: Transaction, val reason: String) : ReceiptProcessingResult()
    data class Failed(val message: String) : ReceiptProcessingResult()
}
```

**Usage in ViewModel:**
```kotlin
when (val result = processReceipt(uri)) {
    is ReceiptProcessingResult.AutoSaved → {
        // Navigate back to home, show toast
    }
    is ReceiptProcessingResult.ReviewNeeded → {
        // Navigate to detail screen with the transaction for manual review
    }
    is ReceiptProcessingResult.Failed → {
        // Show error toast
    }
}
```

#### `SaveTransactionUseCase`

Simple wrapper around `repository.save()`. Can be extended with:
- Additional validation
- Duplicate detection
- Analytics logging

#### `DeleteTransactionUseCase`

Simple wrapper around `repository.delete()`. Image cleanup is handled in the repository.

---

### Presentation Layer

#### `viewmodel/HomeViewModel`

**Responsibility:** Home screen state and receipt processing.

**State:**
```kotlin
sealed class HomeUiState {
    object Initial : HomeUiState()
    object Processing : HomeUiState()                              // Processing receipt
    data class AutoSaved(val transaction: Transaction) : HomeUiState()
    data class ReviewNeeded(val transaction: Transaction, val reason: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
```

**Key Functions:**
```kotlin
fun hasApiKey(): Boolean  // Check before allowing receipt scan
fun onImageSelected(uri: Uri)  // Trigger processing
fun resetState()  // Clear UI state after navigation
```

**Exposed State:**
```kotlin
val transactions: StateFlow<List<Transaction>>  // From repository
val uiState: StateFlow<HomeUiState>
```

#### `viewmodel/TransactionDetailViewModel`

**Responsibility:** Transaction editing/creation (manual or from receipt review).

**State:**
```kotlin
sealed class TransactionDetailUiState {
    object Initial : TransactionDetailUiState()
    data class Editing(val transaction: Transaction, val isExisting: Boolean) : TransactionDetailUiState()
    object Saving : TransactionDetailUiState()
    object Saved : TransactionDetailUiState()
    data class Error(val message: String) : TransactionDetailUiState()
}
```

**Key Functions:**
```kotlin
fun loadTransaction(tx: Transaction)  // For new receipt review
fun loadTransactionById(id: Long)  // For editing existing
fun updateMerchant(name: String)
fun updateNote(note: String)
fun updateTotal(amountMinor: Long)
fun updateCategory(category: Category)
fun updateLineItem(index: Int, item: LineItem)
fun addLineItem()
fun removeLineItem(index: Int)
fun save()  // Insert or update based on isExisting flag
```

**How Updates Work:**
```kotlin
private fun updateTransaction(transform: (Transaction) -> Transaction) {
    val state = _uiState.value as? TransactionDetailUiState.Editing ?: return
    _uiState.value = state.copy(transaction = transform(state.transaction))
}
```

All update functions use this pattern — it's immutable and unidirectional.

#### `viewmodel/SettingsViewModel`

**Responsibility:** User settings (API key, model selection).

**State:**
```kotlin
data class SettingsUiState(
    val apiKey: String = "",
    val modelName: String = ApiKeyManager.DEFAULT_MODEL,
    val saved: Boolean = false,
)
```

---

### UI Layer (`ui/`)

#### `ui/screens/HomeScreen`

**Responsibility:** Display transaction list, trigger receipt capture.

**Key Features:**
- Empty state if no transactions
- Floating action button (FAB) to add receipt
- Camera + gallery picker dialog
- Transaction cards showing merchant/total/date/category
- Tap transaction → navigate to detail screen

**MoneyFormatter Usage:**
```kotlin
Text(
    text = MoneyFormatter.format(transaction.amount),
    style = MaterialTheme.typography.titleMedium,
)
```

#### `ui/screens/TransactionDetailScreen`

**Responsibility:** Review/edit transaction details.

**Features:**
- Edit transaction fields: note, total, category
- If bill present: edit merchant, show date/raw amounts, manage line items
- Add/remove line items with description, qty, amount
- Save button (insert for new, update for existing)

**Layout:**
```
- Note field (all transactions)
- Total amount input (all transactions)
- Category dropdown (all transactions)
- [IF BILL EXISTS]
  - Merchant name
  - Date from receipt
  - Line items list (add/remove buttons)
  - Receipt notes (read-only)
```

#### `ui/navigation/BillateNavHost`

**Routes:**
- `"home"` → `HomeScreen()`
- `"settings"` → `SettingsScreen()`
- `"review"` → `TransactionDetailScreen()` (from FAB press)
- `"edit/{transactionId}"` → `TransactionDetailScreen()` (from existing transaction tap)

**State Management:**
Uses `remember { mutableStateOf<Transaction?>(null) }` to pass transaction to review screen.

---

## Core Concepts

### Money & Currency

**Design:**
- Money stored in **minor units** (smallest subdivision)
  - VND 150,000 → stored as `150000` minor units (1 đồng = 1 minor unit)
  - USD 12.99 → stored as `1299` minor units (1 cent = 1 minor unit)
  
- Currency code is **ISO 4217** (3-letter code)
  - VND, USD, EUR, JPY, GBP, etc.

**Converting User Input to Minor Units:**
```kotlin
// User enters "150000" in VND field
MoneyFormatter.parseToMinor("150000", "VND")  // → 150000

// User enters "12.99" in USD field
MoneyFormatter.parseToMinor("12.99", "USD")   // → 1299
```

**Displaying to User:**
```kotlin
// Display 150000 VND minor units
MoneyFormatter.format(Money(150000, "VND"))  // → "150.000 ₫"

// Display 1299 USD minor units
MoneyFormatter.format(Money(1299, "USD"))    // → "12.99 $"
```

### Bill as Optional Attachment

**Manual Transaction (no receipt):**
```kotlin
Transaction(
    id = 1,
    timestamp = System.currentTimeMillis(),
    amount = Money(50000, "VND"),
    category = Category.Transport,
    bill = null,           // No bill
    note = "Taxi to office",
)
```

**Receipt-Scanned Transaction:**
```kotlin
Transaction(
    id = 2,
    timestamp = receipt.date,
    amount = Money(1500000, "VND"),
    category = Category.Dining,
    bill = Bill(
        merchantName = "Pho Hoa",
        transactionDateRaw = "2026-02-11",
        totalAmountRaw = "150.000 VND",
        lineItems = listOf(
            LineItem(description = "Pho Bo", qty = 2, amount = Money(100000, "VND")),
            LineItem(description = "Bia Saigon", qty = 1, amount = Money(50000, "VND")),
        ),
        notes = "Lunch with client",
        imageUri = "receipt_uuid.jpg",
    ),
    note = "",  // Usually empty for scanned
)
```

**Determining Transaction Type in UI:**
```kotlin
if (transaction.bill != null) {
    // Scanned receipt — show merchant, line items
} else {
    // Manual entry — show note
}
```

### Image Storage

**Where Images Are Stored:**
```
context.filesDir/receipts/receipt_uuid.jpg
```

**Lifecycle:**
1. When `ProcessReceiptUseCase` saves an image:
   ```kotlin
   imageStorage.saveImage(stream, "receipt_uuid.jpg")
   ```
   → Image written to internal storage

2. `Bill.imageUri` field stores filename only: `"receipt_uuid.jpg"`

3. When transaction is deleted:
   ```kotlin
   imageStorage.deleteImage(imageUri)  // Deletes the file
   ```

**Displaying Receipt Image:**
```kotlin
// In TransactionDetailScreen
if (transaction.bill?.imageUri != null) {
    val file = File(context.filesDir, "receipts/${transaction.bill.imageUri}")
    if (file.exists()) {
        // Show image using Coil/Glide
        AsyncImage(model = file, contentDescription = "Receipt")
    }
}
```

### Category Enum

**Current Categories:**
```
Groceries, Dining, Shopping, Transport, Utilities, Health, Entertainment, Education, Other
```

**Adding New Category:**
1. Add to enum:
   ```kotlin
   enum class Category(val displayName: String) {
       // ... existing ...
       Entertainment("Entertainment"),
       NewCategory("Display Name"),  // Add here
   }
   ```

2. Update Gemini prompt to recognize it in `ReceiptExtractor.kt`

3. No DB migration needed (categories stored as strings in DB)

---

## How to Add Features

### Scenario 1: Add a New Field to Transaction

**Example:** Add `receiptNumber` field

**Steps:**

1. **Update Domain Model** (`core/model/Transaction.kt`):
   ```kotlin
   data class Transaction(
       // ... existing fields ...
       val receiptNumber: String? = null,  // Add here
   )
   ```

2. **Update Room Entity** (`data/local/TransactionEntity.kt`):
   ```kotlin
   @Entity(tableName = "transactions")
   data class TransactionEntity(
       // ... existing fields ...
       val receiptNumber: String? = null,  // Add here
   )
   ```

3. **Update Mapper** (`data/local/TransactionMappers.kt`):
   ```kotlin
   fun TransactionWithLineItems.toDomain(): Transaction {
       val tx = transaction
       // ... existing code ...
       return Transaction(
           // ... existing mappings ...
           receiptNumber = tx.receiptNumber,
       )
   }
   
   fun Transaction.toEntity() = TransactionEntity(
       // ... existing mappings ...
       receiptNumber = receiptNumber,
   )
   ```

4. **Update API Response** (`data/remote/GeminiReceiptResponse.kt`):
   ```kotlin
   data class GeminiReceiptResponse(
       // ... existing fields ...
       val receiptNumber: String? = null,
   )
   ```

5. **Update Gemini Prompt** (`data/remote/ReceiptExtractor.kt`):
   - Ask Gemini to extract receipt number

6. **Update Use Case** (`domain/usecase/ProcessReceiptUseCase.kt`):
   ```kotlin
   private fun mapResponseToTransaction(...): Transaction {
       // ... existing code ...
       return Transaction(
           // ... existing mappings ...
           receiptNumber = response.receiptNumber,
       )
   }
   ```

7. **Update ViewModel** (`viewmodel/TransactionDetailViewModel.kt`):
   ```kotlin
   fun updateReceiptNumber(number: String) {
       updateTransaction { it.copy(receiptNumber = number) }
   }
   ```

8. **Update UI** (`ui/screens/TransactionDetailScreen.kt`):
   ```kotlin
   OutlinedTextField(
       value = transaction.bill?.receiptNumber ?: "",
       onValueChange = { /* call viewModel function */ },
       label = { Text("Receipt Number") },
   )
   ```

9. **Database Migration:** On first run, Room will detect schema change and use `fallbackToDestructiveMigration()` (deletes old data). For production, implement proper migration.

---

### Scenario 2: Add a New Use Case

**Example:** Generate monthly spending report

**Steps:**

1. **Create Use Case** (`domain/usecase/GenerateMonthlyReportUseCase.kt`):
   ```kotlin
   class GenerateMonthlyReportUseCase @Inject constructor(
       private val repository: TransactionRepository,
   ) {
       suspend operator fun invoke(year: Int, month: Int): MonthlyReport {
           val start = Calendar.getInstance().apply {
               set(year, month - 1, 1, 0, 0, 0)
           }.timeInMillis
           val end = Calendar.getInstance().apply {
               set(year, month - 1, 1, 0, 0, 0)
               add(Calendar.MONTH, 1)
           }.timeInMillis
           
           val txs = repository.getByDateRange(start, end).first()
           
           // Calculate totals by category
           return MonthlyReport(
               totalByCategory = txs.groupBy { it.category }
                   .mapValues { (_, txList) -> 
                       txList.sumOf { it.amount.amountMinor }
                   },
               total = txs.sumOf { it.amount.amountMinor },
           )
       }
   }
   ```

2. **Update ViewModel** (e.g., create new `StatsViewModel`):
   ```kotlin
   @HiltViewModel
   class StatsViewModel @Inject constructor(
       private val generateReport: GenerateMonthlyReportUseCase,
   ) : ViewModel() {
       // Expose data
   }
   ```

3. **Create UI Screen** to display the report

---

### Scenario 3: Add Support for a New Currency

**Steps:**

1. **Update `CurrencyConfig.kt`:**
   ```kotlin
   object CurrencyConfig {
       fun forCode(code: String): CurrencyConfig = when (code) {
           // ... existing ...
           "CHF" -> CurrencyConfig("CHF", 2, 100, Locale("de", "CH"), "Fr")
           else -> CurrencyConfig(code, 2, 100, Locale.US, code)  // Fallback
       }
   }
   ```

2. **Update Gemini prompt** in `ReceiptExtractor.kt` to recognize the new currency

3. Test with a sample receipt in that currency

---

### Scenario 4: Modify Gemini Extraction

**Example:** Extract tax amount separately

**Steps:**

1. **Update `GeminiReceiptResponse`:**
   ```kotlin
   data class GeminiReceiptResponse(
       // ... existing ...
       val taxAmount: Long = 0,
       val taxRate: Double = 0.0,
   )
   ```

2. **Update Gemini Prompt** in `ReceiptExtractor.kt`:
   - Add instruction: "Extract tax amount and tax rate if visible"

3. **Update `ProcessReceiptUseCase.mapResponseToTransaction()`** to handle tax fields

4. If you want to store tax, add to `Bill` model and DB entity

---

## Common Development Patterns

### Pattern 1: Immutable State Updates

**In ViewModels**, always use immutable updates:

```kotlin
// ✅ GOOD
fun updateCategory(category: Category) {
    updateTransaction { it.copy(category = category) }
}

private fun updateTransaction(transform: (Transaction) -> Transaction) {
    val state = _uiState.value as? TransactionDetailUiState.Editing ?: return
    _uiState.value = state.copy(transaction = transform(state.transaction))
}

// ❌ BAD (mutable)
fun updateCategory(category: Category) {
    val state = (_uiState.value as TransactionDetailUiState.Editing).transaction
    state.category = category  // Wrong! Transaction is immutable
    _uiState.value = TransactionDetailUiState.Editing(state)
}
```

### Pattern 2: Repository as Abstraction

Always depend on `TransactionRepository` interface, not the implementation:

```kotlin
// ✅ GOOD
class HomeViewModel @Inject constructor(
    repository: TransactionRepository,  // Interface
) : ViewModel()

// ❌ BAD (concrete implementation)
class HomeViewModel @Inject constructor(
    repository: DefaultTransactionRepository,  // Implementation
) : ViewModel()
```

This allows testing with mock implementations.

### Pattern 3: Sealed Classes for Results

Use sealed classes for operations with multiple outcomes:

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

// Usage
when (val result = someOperation()) {
    is Result.Success -> { /* handle data */ }
    is Result.Error -> { /* handle error */ }
    Result.Loading -> { /* show spinner */ }
}
```

Already used in `ReceiptProcessingResult`.

### Pattern 4: Extensions for Mapping

Keep mapping logic in extension functions:

```kotlin
// ✅ In TransactionMappers.kt
fun TransactionWithLineItems.toDomain(): Transaction = ...
fun Transaction.toEntity(): TransactionEntity = ...

// Usage
val domain = dbRow.toDomain()
val entity = domain.toEntity()
```

### Pattern 5: Flow for Reactive Data

Use `Flow` for read operations, `suspend` for writes:

```kotlin
// ✅ Read — reactive, updates when data changes
fun getAll(): Flow<List<Transaction>>

// ✅ Write — one-time operation
suspend fun save(transaction: Transaction): Long

// Usage
val transactions: StateFlow<List<Transaction>> = 
    repository.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

---

## Data Flow Examples

### Data Flow: Scan Receipt → Auto-Save

```
User presses FAB
  ↓
HomeScreen shows camera/gallery picker
  ↓
User takes photo or picks image
  ↓
HomeViewModel.onImageSelected(uri)
  ↓
ProcessReceiptUseCase invoked
  ├─ Read bitmap from Uri
  ├─ Call Gemini API (ReceiptExtractor)
  ├─ Save image to internal storage
  ├─ Map GeminiReceiptResponse → Transaction
  ├─ Validate (merchant name, positive amount)
  └─ repository.save(transaction)
  ↓
ReceiptProcessingResult.AutoSaved emitted
  ↓
HomeViewModel updates state → shows toast "Transaction saved!"
  ↓
User navigates back to home
  ↓
HomeScreen displays updated transaction list (from Flow)
```

### Data Flow: Scan Receipt → Manual Review

```
[Same as above until validation]
  ↓
Validation fails: "Receipt is unclear, please review"
  ↓
ReceiptProcessingResult.ReviewNeeded emitted
  ↓
HomeViewModel updates state
  ↓
HomeScreen navigates to TransactionDetailScreen with transaction
  ↓
User reviews/edits fields:
  ├─ Corrects merchant name
  ├─ Fixes total amount
  └─ Adjusts line items
  ↓
User presses "Save"
  ↓
TransactionDetailViewModel.save()
  ├─ Call repository.save(updatedTransaction)
  └─ Emit Saved state
  ↓
TransactionDetailScreen pops back to home
  ↓
Updated transaction appears in list
```

### Data Flow: Edit Existing Transaction

```
User taps transaction card on HomeScreen
  ↓
HomeScreen.onNavigateToEdit(transactionId)
  ↓
BillateNavHost navigates to "edit/{transactionId}"
  ↓
TransactionDetailScreen loads via ViewModel
  ├─ Call repository.getById(transactionId)
  └─ Set state to Editing(transaction, isExisting=true)
  ↓
User modifies fields
  ↓
User presses "Save"
  ↓
TransactionDetailViewModel.save()
  ├─ Since isExisting=true, call repository.update() (not save)
  └─ Emit Saved state
  ↓
Updated transaction appears in list
```

### Data Flow: Delete Transaction with Image Cleanup

```
[Via hypothetical delete action]
  ↓
User confirms delete
  ↓
ViewModel calls repository.delete(transactionId)
  ↓
DefaultTransactionRepository.delete()
  ├─ Query DB: getImageUri(transactionId) → "receipt_uuid.jpg"
  ├─ Delete file: ReceiptImageStorage.deleteImage("receipt_uuid.jpg")
  └─ Delete DB record: dao.deleteTransaction(transactionId)
      (cascades to line_items via FK constraint)
  ↓
Image file removed from internal storage
Transaction and line items removed from DB
  ↓
Repository.getAll() Flow emits updated list
  ↓
HomeScreen automatically updates (recomposes)
```

---

## Database Schema

### Version 2 (Current)

**Tables:**

#### `transactions`
| Column | Type | Constraints | Notes |
|--------|------|---|---|
| `id` | INTEGER | PK, AUTO_INCREMENT | Transaction ID |
| `timestamp` | INTEGER | NOT NULL | When transaction occurred (epoch ms) |
| `amountMinor` | INTEGER | NOT NULL | Total in minor units |
| `currency` | TEXT | NOT NULL | ISO 4217 code (VND, USD, etc.) |
| `category` | TEXT | NOT NULL | Category display name |
| `note` | TEXT | DEFAULT '' | User note (for manual entries) |
| `merchantName` | TEXT | NULLABLE | Bill: merchant name |
| `transactionDateRaw` | TEXT | NULLABLE | Bill: original date string from receipt |
| `totalAmountRaw` | TEXT | NULLABLE | Bill: original amount string from receipt |
| `billNotes` | TEXT | NULLABLE | Bill: notes from receipt |
| `billImageUri` | TEXT | NULLABLE | Bill: filename in internal storage |
| `createdAt` | INTEGER | NOT NULL | When record created |

**Indexes:** None currently (can add on frequently-queried columns)

#### `line_items`
| Column | Type | Constraints | Notes |
|--------|------|---|---|
| `id` | INTEGER | PK, AUTO_INCREMENT | Line item ID |
| `transactionId` | INTEGER | FK → `transactions(id)` | Parent transaction |
| `description` | TEXT | NOT NULL | Item description |
| `qty` | INTEGER | NOT NULL | Quantity |
| `amountMinor` | INTEGER | NOT NULL | Item amount in minor units |
| `currency` | TEXT | NOT NULL | Currency code |
| `amountRaw` | TEXT | | Original amount from receipt |

**Indexes:** `transactionId`

**Foreign Keys:** `transactionId` → `transactions(id)` ON DELETE CASCADE

**Rationale for Flattened Bill Fields:**
- Simpler queries (no JOIN required for most operations)
- Easy to add/remove bill fields without schema changes
- Seamless distinction between manual entries (bill fields NULL) and scanned receipts (bill fields populated)

### Migration Strategy

**Current:** `fallbackToDestructiveMigration()` — deletes old DB on schema change

**For Production:**
Implement `Migration` objects:
```kotlin
val migration1to2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            ALTER TABLE transactions ADD COLUMN newField TEXT DEFAULT NULL
        """)
    }
}

Room.databaseBuilder(context, BillateDatabase::class.java, "billate.db")
    .addMigrations(migration1to2)
    .build()
```

---

## Navigation & Routes

### Route Structure

```
BillateNavHost (Scaffold with bottom nav bar)
├── home (visible in bottom nav)
│   └── HomeScreen
│       ├── onNavigateToReview → navigate("review")
│       └── onNavigateToEdit(id) → navigate("edit/$id")
├── review (NOT visible in bottom nav)
│   └── TransactionDetailScreen (new transaction from receipt)
├── edit/{transactionId} (NOT visible in bottom nav)
│   └── TransactionDetailScreen (existing transaction)
└── settings (visible in bottom nav)
    └── SettingsScreen
```

### Key Navigation Patterns

**From HomeScreen to review new receipt:**
```kotlin
HomeScreen(
    onNavigateToReview = { transaction ->
        transactionToReview = transaction  // Store in remember state
        navController.navigate("review")
    },
)
```

**From HomeScreen to edit existing:**
```kotlin
HomeScreen(
    onNavigateToEdit = { id ->
        navController.navigate("edit/$id")
    },
)
```

**Back Navigation:**
```kotlin
TransactionDetailScreen(
    onBack = { navController.popBackStack() },
    onSaved = { navController.popBackStack() },
)
```

### Adding New Routes

**Example: Add a dashboard route**

1. **Add route in `BillateNavHost`:**
   ```kotlin
   composable("dashboard") {
       DashboardScreen()
   }
   ```

2. **Add button in navigation:**
   ```kotlin
   NavigationBarItem(
       icon = { Icon(Icons.Default.Dashboard, "Dashboard") },
       label = { Text("Dashboard") },
       selected = currentRoute == "dashboard",
       onClick = { navController.navigate("dashboard") },
   )
   ```

3. **Create screen & ViewModel:**
   - `ui/screens/DashboardScreen.kt`
   - `viewmodel/DashboardViewModel.kt`

---

## Testing & Debugging

### Unit Testing: Domain Layer

**Example: Test `ProcessReceiptUseCase`**

```kotlin
@RunWith(RobolectricTestRunner::class)
class ProcessReceiptUseCaseTest {
    
    private val mockExtractor: ReceiptExtractor = mockk()
    private val mockRepository: TransactionRepository = mockk()
    private val mockImageStorage: ReceiptImageStorage = mockk()
    private val mockContentResolver: ContentResolver = mockk()
    
    private val useCase = ProcessReceiptUseCase(
        mockExtractor, mockRepository, mockImageStorage, mockContentResolver
    )
    
    @Test
    fun `should auto-save valid receipt`() = runBlocking {
        val response = GeminiReceiptResponse(
            merchantName = "Restaurant",
            totalAmount = 500000,
            currency = "VND",
            // ... other fields
        )
        coEvery { mockExtractor.extract(any()) } returns response
        coEvery { mockRepository.save(any()) } returns 1L
        coEvery { mockImageStorage.saveImage(any(), any()) } just runs
        
        val result = useCase(mockUri)
        
        assert(result is ReceiptProcessingResult.AutoSaved)
    }
}
```

### ViewModel Testing

**Example: Test `TransactionDetailViewModel`**

```kotlin
@RunWith(RobolectricTestRunner::class)
class TransactionDetailViewModelTest {
    
    private val mockRepository: TransactionRepository = mockk()
    private val mockUseCase: SaveTransactionUseCase = mockk()
    private val viewModel = TransactionDetailViewModel(mockUseCase, mockRepository)
    
    @Test
    fun `updating merchant should reflect in state`() {
        val transaction = Transaction(
            timestamp = now,
            amount = Money(100, "VND"),
            category = Category.Dining,
            bill = Bill(),
        )
        viewModel.loadTransaction(transaction)
        
        viewModel.updateMerchant("New Restaurant")
        
        val state = viewModel.uiState.value as TransactionDetailUiState.Editing
        assertEquals("New Restaurant", state.transaction.bill?.merchantName)
    }
}
```

### Integration Testing: Repository

**Example: Test `DefaultTransactionRepository`**

```kotlin
@RunWith(RobolectricTestRunner::class)
class DefaultTransactionRepositoryTest {
    
    private val db: BillateDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        BillateDatabase::class.java
    ).build()
    
    private val repository = DefaultTransactionRepository(db.transactionDao(), mockImageStorage)
    
    @Test
    fun `saving transaction should persist to DB`() = runBlocking {
        val tx = Transaction(
            timestamp = now,
            amount = Money(100, "VND"),
            category = Category.Shopping,
        )
        
        val id = repository.save(tx)
        
        val retrieved = repository.getById(id)
        assertEquals(tx.copy(id = id), retrieved)
    }
}
```

### Debugging Tips

#### 1. Check Data in Database

```kotlin
// In an Activity/Fragment or test
val db: BillateDatabase = /* get instance */
val txs = db.transactionDao().getAllWithLineItems()
txs.first().forEach { println(it.transaction) }
```

#### 2. Log Gemini API Responses

In `ReceiptExtractor.kt`:
```kotlin
val response = client.generateContent(content)
Log.d("ReceiptExtractor", "Response: $response")
```

#### 3. Inspect Internal Storage Files

```bash
# In adb shell
adb shell
ls -la /data/data/com.billate.app/files/receipts/
cat /data/data/com.billate.app/files/receipts/receipt_uuid.jpg > /sdcard/receipt.jpg
```

#### 4. Monitor StateFlow in ViewModel

```kotlin
// In tests
val state = viewModel.uiState.value
println("Current state: $state")

// In Logcat with collect
viewModel.transactions.collect { txs ->
    Log.d("HomeVM", "Transactions updated: ${txs.size}")
}
```

#### 5. Room Query Logging

```kotlin
Room.databaseBuilder(context, BillateDatabase::class.java, "billate.db")
    .setQueryCallback({ sqlQuery, _ ->
        Log.d("RoomDB", sqlQuery)
    }, CoroutineScope(Dispatchers.Main))
    .build()
```

---

## Conclusion

This restructured architecture provides:

✅ **Clear separation of concerns** — each layer has a single responsibility  
✅ **Currency-agnostic design** — extensible to any ISO 4217 currency  
✅ **Transactionas primary** — supports both manual and scanned entries  
✅ **Immutable state** — easier to reason about and test  
✅ **Clean code** — follows Android best practices and SOLID principles  

**Next Development Priorities (as noted in development-notes.md):**
- [ ] Dashboard with monthly/category analytics
- [ ] Advanced filtering (date range, category, amount)
- [ ] Developer mode (duplicate detection, export data)
- [ ] Receipt image viewer in transaction detail
- [ ] Recurring transactions
- [ ] Budget alerts
- [ ] Multi-currency support per transaction

---

**Questions or Issues?** Refer to this guide or check the source code comments.
