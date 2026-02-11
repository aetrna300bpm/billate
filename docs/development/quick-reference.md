# Quick Reference: Common Development Tasks

**Last Updated:** February 2026

This is a quick lookup guide for the most common development tasks. For detailed explanations, see [restructure-guide.md](./restructure-guide.md).

---

## Table of Contents

1. [Adding a New Field to Transaction](#adding-a-new-field-to-transaction)
2. [Adding a New UI Screen](#adding-a-new-ui-screen)
3. [Modifying Gemini Extraction](#modifying-gemini-extraction)
4. [Adding Currency Support](#adding-currency-support)
5. [Database Queries](#database-queries)
6. [State Management in ViewModel](#state-management-in-viewmodel)
7. [Navigation](#navigation)
8. [Testing](#testing)

---

## Adding a New Field to Transaction

### Example: Add `receiptId` field

**Files to modify:**

1. **`core/model/Transaction.kt`** — Add to data class
   ```kotlin
   data class Transaction(
       // ... existing ...
       val receiptId: String? = null,  // NEW
   )
   ```

2. **`data/local/TransactionEntity.kt`** — Add to Room entity
   ```kotlin
   @Entity(tableName = "transactions")
   data class TransactionEntity(
       // ... existing ...
       val receiptId: String? = null,  // NEW
   )
   ```

3. **`data/local/TransactionMappers.kt`** — Add mappings in both directions
   ```kotlin
   fun TransactionWithLineItems.toDomain(): Transaction {
       // ... existing code ...
       receiptId = tx.receiptId,  // ADD HERE
   }
   
   fun Transaction.toEntity() = TransactionEntity(
       // ... existing ...
       receiptId = receiptId,  // ADD HERE
   )
   ```

4. **`data/remote/GeminiReceiptResponse.kt`** — Add to API response (if from receipt)
   ```kotlin
   data class GeminiReceiptResponse(
       // ... existing ...
       val receiptId: String? = null,  // NEW
   )
   ```

5. **`domain/usecase/ProcessReceiptUseCase.kt`** — Extract from API
   ```kotlin
   private fun mapResponseToTransaction(...): Transaction {
       // ... existing code ...
       receiptId = response.receiptId,  // ADD HERE
   }
   ```

6. **`viewmodel/TransactionDetailViewModel.kt`** — Add update function
   ```kotlin
   fun updateReceiptId(id: String) {
       updateTransaction { it.copy(receiptId = id) }
   }
   ```

7. **UI Screen** — Add input field and call update function

**Database:** Room auto-detects schema changes. On first run with `fallbackToDestructiveMigration()`, old DB is deleted.

---

## Adding a New UI Screen

### Example: Add `AnalyticsScreen`

**Step 1: Create ViewModel** (`viewmodel/AnalyticsViewModel.kt`)
```kotlin
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: TransactionRepository,
) : ViewModel() {
    
    val monthlyTotal: StateFlow<Long> = repository.getAll()
        .map { txs -> txs.sumOf { it.amount.amountMinor } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    
    // ... other data/logic ...
}
```

**Step 2: Create Screen** (`ui/screens/AnalyticsScreen.kt`)
```kotlin
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val monthlyTotal by viewModel.monthlyTotal.collectAsStateWithLifecycle()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Monthly Total: ${MoneyFormatter.format(Money(monthlyTotal, "VND"))}")
        // ... rest of UI ...
    }
}
```

**Step 3: Add Route** (`ui/navigation/BillateNavHost.kt`)
```kotlin
composable("analytics") {
    AnalyticsScreen()
}
```

**Step 4: Add Navigation Item** (in `BillateNavHost` bottom bar)
```kotlin
NavigationBarItem(
    icon = { Icon(Icons.Default.Analytics, "Analytics") },
    label = { Text("Analytics") },
    selected = currentRoute == "analytics",
    onClick = { navController.navigate("analytics") },
)
```

---

## Modifying Gemini Extraction

### Example: Extract discount amount separately

**Step 1: Update API response** (`data/remote/GeminiReceiptResponse.kt`)
```kotlin
data class GeminiReceiptResponse(
    // ... existing ...
    val discountAmount: Long = 0,  // NEW
    val discountReason: String = "",  // NEW
)
```

**Step 2: Update Gemini prompt** (`data/remote/ReceiptExtractor.kt`)
```kotlin
private fun buildPrompt(): String {
    return """
        Extract from the receipt:
        ...
        12. Discount amount (in smallest unit): <number>
        13. Reason for discount: <text>
        ...
    """.trimIndent()
}
```

**Step 3: Update use case** (`domain/usecase/ProcessReceiptUseCase.kt`)
```kotlin
private fun mapResponseToTransaction(...): Transaction {
    // ... existing code ...
    // If you want to store discount as separate field:
    // val transaction = Transaction(..., discountAmount = response.discountAmount)
}
```

**Step 4: Test** with sample receipt containing discount

---

## Adding Currency Support

### Example: Add GBP (British Pound)

**Single File:** `core/currency/CurrencyConfig.kt`

```kotlin
object CurrencyConfig {
    private val configs = mapOf(
        "VND" to CurrencyConfig("VND", 0, 1, Locale("vi", "VN"), "₫"),
        "USD" to CurrencyConfig("USD", 2, 100, Locale.US, "$"),
        "EUR" to CurrencyConfig("EUR", 2, 100, Locale("de", "DE"), "€"),
        "JPY" to CurrencyConfig("JPY", 0, 1, Locale.JAPAN, "¥"),
        "GBP" to CurrencyConfig("GBP", 2, 100, Locale.UK, "£"),  // ADD HERE
    )
    
    fun forCode(code: String): CurrencyConfig = 
        configs[code] ?: CurrencyConfig(code, 2, 100, Locale.US, code)  // Fallback
}
```

**That's it!** The rest of the system uses `CurrencyConfig.forCode(currency)` to handle formatting and parsing.

---

## Database Queries

### Accessing the DAO directly (for testing/debugging)

```kotlin
val db = hiltTestInstance(BillateDatabase::class.java)
val dao = db.transactionDao()

// Get all transactions
val txs = dao.getAllWithLineItems().first()

// Get by ID
val tx = dao.getByIdWithLineItems(1L)

// Get by date range
val rangeTxs = dao.getByDateRange(startMs, endMs).first()

// Insert
val id = dao.insertWithLineItems(entity, lineItems)

// Update
dao.updateWithLineItems(entity, lineItems)

// Delete
dao.deleteTransaction(id)
```

### Adding a new query

**Example: Get transactions by category**

In `data/local/TransactionDao.kt`:
```kotlin
@Query("SELECT * FROM transactions WHERE category = :category ORDER BY timestamp DESC")
fun getByCategory(category: String): Flow<List<TransactionEntity>>
```

Then expose in `TransactionRepository`:
```kotlin
fun getByCategory(category: Category): Flow<List<Transaction>> =
    dao.getByCategory(category.displayName).map { list ->
        list.map { /* mappers */ }
    }
```

---

## State Management in ViewModel

### Immutable Updates (Preferred)

```kotlin
// ✅ Use copy() for immutability
fun updateMerchant(name: String) {
    updateTransaction { tx ->
        tx.copy(bill = (tx.bill ?: Bill()).copy(merchantName = name))
    }
}

private fun updateTransaction(transform: (Transaction) -> Transaction) {
    val state = _uiState.value as? TransactionDetailUiState.Editing ?: return
    _uiState.value = state.copy(transaction = transform(state.transaction))
}
```

### Exposing State

```kotlin
private val _uiState = MutableStateFlow<MyUiState>(MyUiState.Initial)
val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()  // Read-only exposure

// In Composable
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

### Side Effects (Navigation, Toasts)

```kotlin
LaunchedEffect(uiState) {
    when (uiState) {
        is MyUiState.Saved -> {
            Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
        is MyUiState.Error -> {
            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
        }
        else -> {}
    }
}
```

---

## Navigation

### Navigate to Screen

```kotlin
// Navigate to a simple route
navController.navigate("settings")

// Navigate with argument
navController.navigate("edit/123")  // transactionId = 123

// Navigate with pop behavior
navController.navigate("home") {
    popUpTo("home") { inclusive = true }  // Clear back stack
}
```

### Define Route in NavHost

```kotlin
composable("home") {
    HomeScreen(
        onNavigateToReview = { tx ->
            transactionToReview = tx
            navController.navigate("review")
        },
    )
}

composable(
    "edit/{transactionId}",
    arguments = listOf(navArgument("transactionId") { type = NavType.LongType })
) { backStack ->
    val id = backStack.arguments?.getLong("transactionId") ?: return@composable
    TransactionDetailScreen(
        transactionId = id,
        onSaved = { navController.popBackStack() },
    )
}
```

### Add New Navigation Item

```kotlin
NavigationBarItem(
    icon = { Icon(Icons.Default.MyIcon, "Label") },
    label = { Text("Label") },
    selected = currentRoute == "myroute",
    onClick = { if (currentRoute != "myroute") navController.navigate("myroute") },
)
```

---

## Testing

### Unit Test: Domain Use Case

```kotlin
@RunWith(RobolectricTestRunner::class)
class MyUseCaseTest {
    
    private val mockRepository: TransactionRepository = mockk()
    private val useCase = MyUseCase(mockRepository)
    
    @Test
    fun `should return correct result`() = runBlocking {
        coEvery { mockRepository.getAll() } returns flowOf(emptyList())
        
        val result = useCase.invoke()
        
        assertTrue(result.isEmpty())
    }
}
```

### ViewModel Test

```kotlin
@RunWith(RobolectricTestRunner::class)
class MyViewModelTest {
    
    private val mockUseCase: MyUseCase = mockk()
    private val viewModel = MyViewModel(mockUseCase)
    
    @Test
    fun `state should update on action`() {
        viewModel.doSomething()
        
        val state = viewModel.uiState.value
        assertTrue(state is MyUiState.ExpectedState)
    }
}
```

### Integration Test: Database

```kotlin
@RunWith(RobolectricTestRunner::class)
class TransactionDaoTest {
    
    private val db: BillateDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        BillateDatabase::class.java
    ).build()
    
    @Test
    fun `insert and retrieve transaction`() = runBlocking {
        val entity = TransactionEntity(...)
        val id = db.transactionDao().insertTransaction(entity)
        
        val retrieved = db.transactionDao().getByIdWithLineItems(id)
        
        assertNotNull(retrieved)
    }
}
```

---

## Dependency Injection (Hilt)

### Inject ViewModel in Composable

```kotlin
@Composable
fun MyScreen(
    viewModel: MyViewModel = hiltViewModel(),  // Auto-injected
) {
    // Use viewModel
}
```

### Inject Class in ViewModel

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val useCase: MyUseCase,
) : ViewModel()
```

### Provide Custom Binding

In `di/AppModule.kt`:
```kotlin
@Provides
@Singleton
fun provideMyService(impl: MyServiceImpl): MyService = impl
```

---

## Common Errors & Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `java.lang.NullPointerException: Attempt to read from field 'com.billate.app.core.model.Transaction' on a null object reference` | Transaction is null | Check `repository.getById()` returns non-null before using |
| `kotlin.KotlinNullPointerException: tx.bill is null` | Accessing null bill | Use `if (tx.bill != null)` or `tx.bill?.merchantName` |
| `Room error: cannot access table transactions` | Database not initialized | Ensure `BillateDatabase.getInstance()` is called in `AppModule` |
| `Gemini API error: 400 Bad Request` | Malformed API request | Check prompt syntax in `ReceiptExtractor.kt` |
| `Cannot find symbol TransactionRepository` | Missing import | Add `import com.billate.app.data.repository.TransactionRepository` |
| `@HiltViewModel not found` | Not using Hilt properly | Ensure class extends `ViewModel` and is in `viewmodel/` package |

---

## Checklist: Before Committing Code

- [ ] Compiled without errors: `./gradlew assembleDebug`
- [ ] No unused imports (use IDE refactoring)
- [ ] No TODO/FIXME comments (or documented in issue)
- [ ] Followed immutability patterns (no var in domain models)
- [ ] Added comments for non-obvious logic
- [ ] Database changes documented (if any)
- [ ] Tests pass (if tests exist)
- [ ] Committed with clear message: `"Add [feature]: [description]"`

---

**Need more details?** See [restructure-guide.md](./restructure-guide.md) for comprehensive documentation.
