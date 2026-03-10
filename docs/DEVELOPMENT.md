# Development Guide

A guide for developers working on **Billate** — covering environment setup, project conventions, and step-by-step instructions for common development tasks like adding new features, modifying the database, and tuning the AI prompt.

---

## Table of Contents

1. [Environment Setup](#environment-setup)
2. [Building & Running](#building--running)
3. [Project Conventions](#project-conventions)
4. [Adding a New Transaction Type](#adding-a-new-transaction-type)
5. [Adding a New Category](#adding-a-new-category)
6. [Adding a New Currency](#adding-a-new-currency)
7. [Database Migrations](#database-migrations)
8. [Modifying the Gemini OCR Prompt](#modifying-the-gemini-ocr-prompt)
9. [Adding a New Screen](#adding-a-new-screen)
10. [Adding a New UI Component](#adding-a-new-ui-component)
11. [Debugging Gemini Responses](#debugging-gemini-responses)
12. [Common Pitfalls](#common-pitfalls)

---

## Environment Setup

### Required Tools

| Tool | Version | Notes |
|---|---|---|
| Android Studio | Hedgehog (2023.1.1)+ | Recommended: latest stable |
| JDK | 17 | Bundled with Android Studio or install separately |
| Android SDK | 34 | Install via SDK Manager |
| Gradle | 8.7 | Managed by wrapper (`gradlew`) |

### Setup Steps

1. **Clone the repository:**
   ```bash
   git clone https://github.com/<your-username>/billate.git
   cd billate
   ```

2. **Open in Android Studio:**
   - File → Open → select the project root.
   - Wait for Gradle sync to complete.
   - If prompted, install any missing SDK components.

3. **Verify JDK 17:**
   - File → Project Structure → SDK Location → JDK.
   - Should be JDK 17. If not, point to a JDK 17 installation.

4. **Set up a device:**
   - Use a physical Android device (USB debugging enabled), or
   - Create an AVD (Android Virtual Device) with API 26+ (API 34 recommended).

5. **Create `local.properties`** (auto-created by Android Studio):
   ```properties
   sdk.dir=C\:\\Users\\<username>\\AppData\\Local\\Android\\Sdk
   ```

---

## Building & Running

### From Android Studio

- Click the green **Run** button (▶) or press `Shift+F10`.
- Select your target device.
- The app builds and installs automatically.

### From Command Line

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run all checks
./gradlew check

# Clean build
./gradlew clean assembleDebug
```

### Build Output

APK location: `app/build/outputs/apk/debug/app-debug.apk`

---

## Project Conventions

### Package Structure

```
com.billate.app
├── core.model       # Pure Kotlin domain models (no Android dependencies)
├── core.currency    # Currency formatting utilities
├── data.local       # Room entities, DAO, database, mappers
├── data.remote      # Gemini API client + response models
├── data.repository  # Repository interface + implementation
├── domain.usecase   # Business logic use cases
├── di               # Hilt DI modules
├── viewmodel        # ViewModels with StateFlow
└── ui               # Compose screens, components, navigation, theme
```

### Naming Conventions

| Type | Convention | Example |
|---|---|---|
| Sealed class subtype | PascalCase noun | `Transaction.Receipt` |
| ViewModel | `<Feature>ViewModel` | `HomeViewModel` |
| Use Case | `<Verb><Noun>UseCase` | `ProcessReceiptUseCase` |
| Screen composable | `<Name>Screen` | `HomeScreen` |
| Component composable | PascalCase descriptive | `DashboardCard` |
| Room entity | `<Name>Entity` | `TransactionEntity` |
| Room DAO | `<Name>Dao` | `TransactionDao` |
| Mapper functions | `toDomain()` / `toEntity()` | `TransactionWithLineItems.toDomain()` |

### Code Style

- **Kotlin official style** (`kotlin.code.style=official` in `gradle.properties`).
- Trailing commas on multi-line parameter lists.
- `@OptIn(ExperimentalMaterial3Api::class)` on composables that use M3 experimental APIs.
- ViewModels expose `StateFlow` — UI collects with `collectAsStateWithLifecycle()`.

---

## Adding a New Transaction Type

Suppose you want to add a `Transaction.Subscription` type for recurring payments.

### Step 1: Add the Subclass

In `core/model/Transaction.kt`:

```kotlin
sealed class Transaction {
    // ... existing subtypes ...

    data class Subscription(
        override val id: Long = 0,
        override val timestamp: Long,
        override val amount: Money,
        override val category: Category,
        override val name: String,
        override val note: String = "",
        override val createdAt: Long = System.currentTimeMillis(),
        val billingCycle: String,       // "monthly", "yearly"
        val nextBillingDate: Long,
    ) : Transaction()
}
```

### Step 2: Update the Database Entity

In `TransactionEntity.kt`, add columns:

```kotlin
@ColumnInfo(name = "billingCycle") val billingCycle: String? = null,
@ColumnInfo(name = "nextBillingDate") val nextBillingDate: Long? = null,
```

### Step 3: Write a Migration

In `BillateDatabase.kt`, increment the version and add a migration:

```kotlin
@Database(version = 5, ...)
abstract class BillateDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN billingCycle TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN nextBillingDate INTEGER")
            }
        }
    }
}
```

Register the migration in `AppModule.kt`:

```kotlin
Room.databaseBuilder(...)
    .addMigrations(BillateDatabase.MIGRATION_3_4, BillateDatabase.MIGRATION_4_5)
    .build()
```

### Step 4: Update Mappers

In `TransactionMappers.kt`:

**`toDomain()`** — add a branch in the `when(tx.type)`:
```kotlin
"subscription" -> Transaction.Subscription(
    id = tx.id,
    timestamp = tx.billDate,
    amount = Money(tx.billTotal, tx.billCurrency),
    category = Category.fromString(tx.billCategory),
    name = tx.name,
    note = tx.billNotes,
    createdAt = tx.createdAt,
    billingCycle = tx.billingCycle ?: "monthly",
    nextBillingDate = tx.nextBillingDate ?: tx.billDate,
)
```

**`toEntity()`** — add a branch in the `when(this)`:
```kotlin
is Transaction.Subscription -> TransactionEntity(
    id = id,
    type = "subscription",
    name = name,
    // ... map all fields ...
    billingCycle = billingCycle,
    nextBillingDate = nextBillingDate,
)
```

### Step 5: Update UI

Add a section in `TransactionDetailScreen.kt` inside the `TransactionDetailContent` composable:

```kotlin
if (transaction is Transaction.Subscription) {
    item {
        OutlinedTextField(
            value = transaction.billingCycle,
            onValueChange = onBillingCycleChange,
            label = { Text("Billing Cycle") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

### Step 6: Update ViewModel

Add update methods in `TransactionDetailViewModel.kt` for new fields, following the existing pattern:

```kotlin
fun updateBillingCycle(cycle: String) {
    val tx = currentTransaction ?: return
    if (tx is Transaction.Subscription) {
        currentTransaction = tx.copy(billingCycle = cycle)
        emitEditing()
    }
}
```

---

## Adding a New Category

### Step 1: Update the Enum

In `core/model/Category.kt`:

```kotlin
enum class Category(val displayName: String) {
    Groceries("Groceries"),
    // ... existing ...
    Subscriptions("Subscriptions"),   // ← new
    Other("Other");
}
```

### Step 2: Add a Color

In `ui/theme/CategoryColors.kt`:

```kotlin
fun categoryColor(category: Category): Color = when (category) {
    // ... existing ...
    Category.Subscriptions -> Color(0xFF3F51B5) // indigo
    Category.Other         -> Color(0xFF795548)
}
```

That's it — the `fromString()` function and all UI elements (dropdowns, charts, filters) use `Category.entries` and will pick up the new value automatically.

---

## Adding a New Currency

### Step 1: Update CurrencyConfig

In `core/currency/MoneyFormatter.kt`, add to the `CurrencyConfig.configs` map:

```kotlin
"INR" to CurrencyConfig("INR", "₹", 2, Locale("en", "IN")),
```

### Step 2: Update Supported Codes

In the `CurrencyConfig.supportedCodes` list:

```kotlin
val supportedCodes = listOf("VND", "USD", "EUR", ..., "INR")
```

The `MoneyFormatter.format()` and `parseToMinor()` functions will handle the new currency automatically based on the config.

---

## Database Migrations

Room requires **explicit migrations** when the schema changes. Never change the database schema without writing a migration.

### Migration Pattern

1. **Increment** the database version in `@Database(version = N)`.
2. **Create** a migration object:
   ```kotlin
   val MIGRATION_N_N1 = object : Migration(N, N + 1) {
       override fun migrate(db: SupportSQLiteDatabase) {
           // SQL statements to alter tables
           db.execSQL("ALTER TABLE transactions ADD COLUMN newColumn TEXT")
       }
   }
   ```
3. **Register** the migration in `AppModule.kt`'s `provideDatabase()`:
   ```kotlin
   .addMigrations(BillateDatabase.MIGRATION_3_4, BillateDatabase.MIGRATION_N_N1)
   ```
4. **Update** `TransactionEntity` (or `LineItemEntity`) to include the new column with a default value.

### Migration Rules

- **Never rename or remove columns** without a full table-rebuild migration.
- **Always provide defaults** for new columns so existing rows don't break.
- **Test migrations** on a device with existing data before release.

### Current Migration History

| Migration | Changes |
|---|---|
| v3 → v4 (`MIGRATION_3_4`) | Added `type TEXT DEFAULT 'receipt'`, `name TEXT DEFAULT ''`, `recipientName TEXT`; backfilled `type` and `name` from existing data |

---

## Modifying the Gemini OCR Prompt

The OCR prompt lives in `data/remote/ReceiptExtractor.kt` inside the `extract()` function.

### Prompt Structure

```
1. IMAGE TYPE DETECTION instruction
2. JSON SCHEMA with field descriptions
3. Wire transfer-specific rules
4. Receipt-specific rules
5. General rules (currency, confidence, category assignment)
```

### Making Changes

1. **Edit the prompt string** in `ReceiptExtractor.kt`.
2. **Update `GeminiReceiptResponse.kt`** if you add/remove fields — it must match the JSON schema in the prompt.
3. **Update `ProcessReceiptUseCase.kt`** if new fields need to be mapped to the domain model.
4. **Test with sample images** — use the app to scan a few receipts/transfers and verify extraction quality.

### Tips for Prompt Engineering

- **Be explicit** about the JSON schema — Gemini follows structured instructions well.
- **Use examples** in the prompt for tricky fields (e.g., date formats).
- **Keep temperature low** (0.2f) for deterministic extraction.
- **Test edge cases**: receipts without totals, blurry images, multi-currency, foreign languages.
- **Log the raw response** during development — add a `Log.d()` before JSON parsing in `ReceiptExtractor` to see exactly what Gemini returns.

### Response Format

The prompt instructs Gemini to return JSON like:

```json
{
  "type": "receipt",
  "merchantName": "Coffee Shop",
  "transactionDate": "2024-03-15",
  "currency": "USD",
  "lineItems": [
    { "description": "Latte", "qty": 1, "amount": "4.50" }
  ],
  "adjustments": {
    "serviceCharge": null,
    "discount": null,
    "tax": "0.36"
  },
  "finalTotal": "4.86",
  "totalAmountRaw": "$4.86",
  "category": "Dining",
  "notes": "",
  "confidence": 0.92
}
```

For wire transfers:

```json
{
  "type": "wire_transfer",
  "recipientName": "John Doe",
  "recipientBank": "Chase Bank",
  "transactionReference": "REF123456",
  "transactionDate": "2024-03-15",
  "currency": "USD",
  "finalTotal": "500.00",
  "totalAmountRaw": "$500.00",
  "category": "Other",
  "notes": "Monthly rent",
  "confidence": 0.85
}
```

---

## Adding a New Screen

### Step 1: Create the Screen Composable

Create a new file in `ui/screens/`:

```kotlin
// ui/screens/StatisticsScreen.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Statistics") })
        },
    ) { padding ->
        // Screen content
    }
}
```

### Step 2: Create a ViewModel

```kotlin
// viewmodel/StatisticsViewModel.kt
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repository: TransactionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()
}
```

### Step 3: Add a Route

In `ui/navigation/BillateNavHost.kt`:

1. Add a bottom nav item if it's a tab:
   ```kotlin
   NavigationBarItem(
       icon = { Icon(Icons.Default.BarChart, contentDescription = "Statistics") },
       label = { Text("Stats") },
       selected = currentRoute == "statistics",
       onClick = { navController.navigate("statistics") { ... } },
   )
   ```

2. Add the composable route:
   ```kotlin
   composable("statistics") {
       StatisticsScreen()
   }
   ```

---

## Adding a New UI Component

1. Create a file in `ui/components/` (e.g., `SpendingChart.kt`).
2. Make it a `@Composable` function with appropriate parameters.
3. Follow the existing pattern — accept data via parameters, not ViewModels.
4. Components should be **stateless** — state management belongs in the parent screen or ViewModel.

Example:

```kotlin
@Composable
fun SpendingChart(
    data: List<DailySpending>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        // Drawing logic
    }
}
```

---

## Debugging Gemini Responses

### Enable Logging

Add temporary logging in `ReceiptExtractor.kt` before JSON parsing:

```kotlin
val rawText = response.text ?: ""
android.util.Log.d("ReceiptExtractor", "Raw Gemini response: $rawText")
```

### Common Issues

| Issue | Likely Cause | Fix |
|---|---|---|
| `SerializationException` | Gemini returned malformed JSON | Check for markdown fences (the extractor strips them, but edge cases exist) |
| Wrong `type` field | Ambiguous image (e.g., a receipt that looks like a transfer) | Refine the prompt's detection rules |
| Missing `finalTotal` | Receipt doesn't show a clear total | Add fallback logic in `ProcessReceiptUseCase` |
| Incorrect currency | Multi-currency receipt or unfamiliar symbol | Add the currency symbol to the prompt's currency list |
| Low confidence | Blurry image, unusual format | Improve image quality or adjust confidence threshold |

### Testing with Specific Images

1. Save test images to the emulator's gallery.
2. Use the Gallery picker to process them.
3. Check the review screen for extraction accuracy.
4. Compare with the raw log output.

---

## Common Pitfalls

### 1. Forgetting to Update Mappers

When adding fields to the domain model, you must update **both directions** in `TransactionMappers.kt`:
- `TransactionWithLineItems.toDomain()` — entity → domain
- `Transaction.toEntity()` — domain → entity

### 2. Missing Migration

Adding a column to `TransactionEntity` without a corresponding Room migration will crash the app on existing installations with: `IllegalStateException: Room cannot verify the data integrity`.

### 3. Sealed Class Exhaustiveness

After adding a new `Transaction` subtype, the compiler will flag every `when(transaction)` expression that doesn't handle the new type. Fix them all — this is the sealed class working as intended.

### 4. Minor Units vs. Display Amounts

All amounts in the domain model and database are in **minor units** (`Long`). Never store or compute with `Double`/`Float` for money. Use `MoneyFormatter.parseToMinor()` to convert user input and `MoneyFormatter.format()` for display.

### 5. SharedPreferences Threading

`ApiKeyManager` reads/writes are synchronous on the calling thread. ViewModels should access it from coroutines or during initialization. Avoid calling `ApiKeyManager` methods on the main thread in loops.

### 6. Image File Cleanup

When deleting a transaction, `DefaultTransactionRepository.delete()` also removes the associated image file. If you add a new transaction type with images, make sure to include the same cleanup logic.

### 7. Compose Recomposition

Avoid creating objects inside composable functions that aren't wrapped in `remember`. Use `remember { ... }` for `SimpleDateFormat`, `File` objects, and other non-trivial allocations to prevent unnecessary recomposition overhead.
