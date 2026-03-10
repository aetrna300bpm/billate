# Architecture Design

This document describes the architecture of **Billate**, an Android expense-tracking app with AI-powered receipt and bank-transfer OCR.

---

## Table of Contents

1. [High-Level Overview](#high-level-overview)
2. [Layer Diagram](#layer-diagram)
3. [Domain Model — Sealed Class Hierarchy](#domain-model--sealed-class-hierarchy)
4. [Data Flow](#data-flow)
5. [Database Schema](#database-schema)
6. [Gemini AI Integration](#gemini-ai-integration)
7. [Dependency Injection](#dependency-injection)
8. [Navigation Graph](#navigation-graph)
9. [Key Design Decisions](#key-design-decisions)

---

## High-Level Overview

Billate follows a **MVVM + Repository + Use Case** architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────┐
│                     UI Layer                            │
│  Screens (Compose) ─ Components ─ Theme ─ Navigation    │
└───────────────────────┬─────────────────────────────────┘
                        │ observes StateFlow
┌───────────────────────┴─────────────────────────────────┐
│                  ViewModel Layer                         │
│  HomeVM ─ TransactionDetailVM ─ InsightsVM ─ SettingsVM  │
└───────────────────────┬─────────────────────────────────┘
                        │ calls
┌───────────────────────┴─────────────────────────────────┐
│                  Domain Layer                            │
│  ProcessReceiptUseCase ─ SaveTransactionUseCase          │
│  DeleteTransactionUseCase                                │
└───────────────────────┬─────────────────────────────────┘
                        │ calls
┌───────────────────────┴─────────────────────────────────┐
│                   Data Layer                             │
│  TransactionRepository (interface)                       │
│  DefaultTransactionRepository (implementation)           │
│  ReceiptExtractor (Gemini API) ─ Room DB ─ ImageStorage  │
└─────────────────────────────────────────────────────────┘
```

**Data flows downward** (UI → ViewModel → UseCase → Repository → Database/API).  
**State flows upward** via Kotlin `StateFlow` and Compose's `collectAsStateWithLifecycle()`.

---

## Layer Diagram

### UI Layer (`ui/`)

| Component | Responsibility |
|---|---|
| `BillateNavHost` | 3-tab bottom navigation (Home, Insights, Settings) + screen routes |
| `HomeScreen` | Dashboard card, search bar, grouped transaction log, FAB (camera/gallery/manual) |
| `TransactionDetailScreen` | Edit/review form — renders type-specific sections based on sealed class |
| `InsightsScreen` | Spending summary card, AI insight generation button, cached insight display |
| `SettingsScreen` | API key input, model selector, currency dropdown |
| `DashboardCard` | Period dropdown, total spent, transaction count, pie chart, category legend |
| `CategoryPieChart` | Canvas-drawn donut chart from `CategoryAmount` list |
| `SearchBar` | Text query field + horizontally scrollable category filter chips |
| `FullScreenImageViewer` | Pinch-to-zoom image overlay (via `detectTransformGestures`) |

### ViewModel Layer (`viewmodel/`)

| ViewModel | State | Key Responsibilities |
|---|---|---|
| `HomeViewModel` | `DashboardState`, `List<GroupedTransactions>`, search state | Period management, dashboard computation, search/filter, image processing pipeline |
| `TransactionDetailViewModel` | `TransactionDetailUiState` (sealed: Editing, Saving, Saved, Deleted, Error) | CRUD operations, line item editing with mode toggle, sealed class field updates |
| `InsightsViewModel` | `InsightsUiState` | Spending summary computation, Gemini text-only insight generation, cache management |
| `SettingsViewModel` | `SettingsUiState` | API key, model name, default currency persistence |

### Domain Layer (`domain/usecase/`)

| Use Case | Flow |
|---|---|
| `ProcessReceiptUseCase` | Read bitmap → call `ReceiptExtractor` → save image to filesystem → map `GeminiReceiptResponse` to `Transaction.Receipt` or `Transaction.WireTransfer` → validate → return |
| `SaveTransactionUseCase` | Delegate to `TransactionRepository.save()` |
| `DeleteTransactionUseCase` | Delegate to `TransactionRepository.delete()` |

### Data Layer (`data/`)

| Component | Responsibility |
|---|---|
| `TransactionRepository` | Interface defining CRUD + date-range queries |
| `DefaultTransactionRepository` | Implementation using `TransactionDao` + `ReceiptImageStorage`; delete also removes image file |
| `TransactionDao` | Room DAO — `getAllWithLineItems`, `getByIdWithLineItems`, `getByDateRange`, composite insert/update with line items |
| `TransactionEntity` / `LineItemEntity` | Flat Room entities; `TransactionEntity` uses a `type` column to discriminate subtypes |
| `TransactionMappers` | Bidirectional mapping: `Entity → Domain` (via `when(type)`) and `Domain → Entity` (via `when(this)`) |
| `ReceiptExtractor` | Creates `GenerativeModel`, sends image + prompt, parses JSON response |
| `GeminiReceiptResponse` | `@Serializable` data class for Gemini's structured JSON output |
| `ApiKeyManager` | `SharedPreferences` wrapper for API key, model, currency, period, insight cache |
| `ReceiptImageStorage` | File I/O for receipt/transfer images in `filesDir/receipts/` |

### Core Layer (`core/`)

| Component | Responsibility |
|---|---|
| `Transaction` | Sealed class hierarchy (see below) |
| `Money` | `data class Money(amountMinor: Long, currency: String)` — amounts in minor units (cents/dong) |
| `MoneyFormatter` | Locale-aware formatting and parsing via `CurrencyConfig` |
| `Category` | Enum with 9 values + `fromString()` + `displayName` |
| Other models | `LineItem`, `CategoryAmount`, `DashboardState`, `GroupedTransactions`, `PeriodType` |

---

## Domain Model — Sealed Class Hierarchy

The central domain model uses a **sealed class** to represent three transaction types with shared and type-specific fields:

```
Transaction (sealed class)
├── id: Long
├── timestamp: Long
├── amount: Money
├── category: Category
├── name: String
├── note: String
├── createdAt: Long
│
├── Receipt
│   ├── lineItems: List<LineItem>
│   ├── serviceCharge: Money?
│   ├── discount: Money?
│   ├── tax: Money?
│   ├── totalAmountRaw: String
│   ├── transactionDateRaw: String
│   ├── merchantNameRaw: String
│   ├── imageUri: String?
│   └── extractionConfidence: Float
│
├── WireTransfer
│   ├── recipientName: String
│   ├── imageUri: String?
│   └── extractionConfidence: Float
│
└── Manual
    (no additional fields — uses base fields only)
```

### Why a Sealed Class?

- **Type safety at compile time** — `when(transaction)` expressions are exhaustive; the compiler enforces handling all subtypes.
- **No nullable field explosion** — Receipt-only fields (line items, adjustments) don't pollute Manual/WireTransfer.
- **Easy extensibility** — adding a new transaction type is a single new subclass with its own fields.
- **Clean mapping** — the `TransactionMappers` use `when` expressions for safe, readable conversions.

---

## Data Flow

### Receipt / Wire Transfer Scanning Flow

```
User taps camera/gallery
        │
        ▼
HomeViewModel.processImage(uri)
        │
        ▼
ProcessReceiptUseCase.invoke(uri)
        │
        ├── ContentResolver.readBitmap(uri)
        ├── ReceiptExtractor.extract(bitmap, apiKey, model)
        │       │
        │       ├── Build GenerativeModel(modelName, apiKey)
        │       ├── Send image + OCR prompt
        │       └── Parse JSON → GeminiReceiptResponse
        │
        ├── ReceiptImageStorage.save(bitmap, prefix)
        │       prefix = "transfer_" or "receipt_"
        │
        ├── mapResponseToTransaction(response, imageFilename)
        │       │
        │       ├── if response.type == "wire_transfer"
        │       │       → Transaction.WireTransfer (always ReviewNeeded)
        │       │
        │       └── else → Transaction.Receipt
        │               → validate (name required, amount > 0, confidence ≥ 0.3)
        │               → AutoSaved or ReviewNeeded
        │
        └── Return ProcessResult (AutoSaved / ReviewNeeded / Error)
                │
                ▼
HomeViewModel updates UI state
        │
        ├── AutoSaved → refresh transaction list
        └── ReviewNeeded → navigate to TransactionDetailScreen
```

### Dashboard Computation Flow

```
HomeViewModel observes TransactionRepository.getAll()
        │
        ▼
Compute date range from PeriodType (WEEK/MONTH/CUSTOM)
        │
        ▼
Filter transactions within range
        │
        ├── Category breakdown (group by category, compute percentages)
        ├── Total spent (sum of amounts)
        ├── Transaction count
        └── Grouped by day (Today / Yesterday / date labels)
                │
                ▼
        Emit DashboardState + List<GroupedTransactions> via StateFlow
                │
                ▼
        HomeScreen recomposes with new data
```

---

## Database Schema

**Room Database v4** — two tables:

### `transactions` table

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER (PK, autoGenerate) | |
| `type` | TEXT | `"receipt"`, `"wire_transfer"`, or `"manual"` |
| `name` | TEXT | Merchant name (receipt), recipient display name (wire), or user name (manual) |
| `merchantName` | TEXT | Column kept as `merchantName` for migration compatibility; maps to `name` |
| `recipientName` | TEXT? | Wire transfer recipient (null for other types) |
| `billNotes` | TEXT | User notes |
| `billDate` | INTEGER | Transaction timestamp (epoch ms) |
| `billTotal` | INTEGER | Amount in minor units |
| `billCurrency` | TEXT | ISO 4217 currency code |
| `billCategory` | TEXT | Category enum name |
| `billImageUri` | TEXT? | Image filename in `receipts/` directory |
| `serviceCharge` | INTEGER? | Receipt adjustment |
| `discount` | INTEGER? | Receipt adjustment (stored positive) |
| `tax` | INTEGER? | Receipt adjustment |
| `totalAmountRaw` | TEXT | Raw amount string from OCR |
| `transactionDateRaw` | TEXT | Raw date string from OCR |
| `extractionConfidence` | REAL | 0.0–1.0 confidence score |
| `createdAt` | INTEGER | Record creation timestamp |

### `line_items` table

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER (PK, autoGenerate) | |
| `transactionId` | INTEGER (FK) | References `transactions.id` |
| `description` | TEXT | Item description |
| `qty` | INTEGER | Quantity |
| `amountMinor` | INTEGER | Price in minor units |
| `amountCurrency` | TEXT | Currency code |
| `amountRaw` | TEXT | Raw amount string from OCR |

### Migrations

- **MIGRATION_3_4** (v3 → v4): Added `type`, `name`, `recipientName` columns; backfilled existing rows as `type = "receipt"` and `name = merchantName`.

---

## Gemini AI Integration

### Receipt/Transfer OCR (`ReceiptExtractor`)

- **Model configuration**: `GenerativeModel` with `temperature = 0.2f` for deterministic extraction.
- **System instruction**: `"You are an OCR assistant for receipt images or bank transfer screenshots."`
- **Prompt strategy**: The prompt instructs Gemini to:
  1. **Auto-detect** whether the image is a receipt or bank transfer.
  2. Return a structured JSON response with a `type` field (`"receipt"` or `"wire_transfer"`).
  3. For receipts: extract merchant, line items, adjustments, total, date, currency, category.
  4. For wire transfers: extract recipient name, bank, amount, reference, date.
  5. Assign a confidence score (0.0–1.0).

### AI Insights (`InsightsViewModel`)

- Uses **text-only** Gemini generation (no image).
- Sends a spending summary (total, daily average, category breakdown) as context.
- Asks for a concise analysis with trends, observations, and suggestions.
- Results are **cached** in `SharedPreferences` via `ApiKeyManager` with period label and timestamp.

### Supported Models

| Model | Description |
|---|---|
| `gemini-3-flash-preview` | Default — latest preview model |
| `gemini-2.5-flash` | Balanced speed/quality |
| `gemini-2.5-flash-lite` | Fastest, lowest cost |

---

## Dependency Injection

Hilt provides all dependencies via a single `@Module` (`AppModule`):

```
AppModule (@InstallIn SingletonComponent)
├── provideDatabase()       → BillateDatabase (Room, with MIGRATION_3_4)
├── provideTransactionDao() → TransactionDao (from database)
├── provideContentResolver() → ContentResolver (from application context)
└── bindTransactionRepository() → DefaultTransactionRepository implements TransactionRepository
```

Additional injectable components created at their use sites:
- `ReceiptExtractor` — instantiated in `ProcessReceiptUseCase`
- `ApiKeyManager` — injected via `@ApplicationContext` constructor
- `ReceiptImageStorage` — injected via `@ApplicationContext` constructor

---

## Navigation Graph

```
Bottom Navigation Bar
├── Home Tab          → HomeScreen
│   ├── + (FAB)       → Camera / Gallery → ProcessReceiptUseCase
│   │                   → if ReviewNeeded → "review" route (TransactionDetailScreen)
│   │                   → Manual          → "create" route (TransactionDetailScreen)
│   └── Tap card      → "edit/{id}" route (TransactionDetailScreen)
│
├── Insights Tab      → InsightsScreen
│
└── Settings Tab      → SettingsScreen
```

**Routes:**

| Route | Screen | Purpose |
|---|---|---|
| `home` | HomeScreen | Dashboard + transaction log |
| `insights` | InsightsScreen | Spending analysis + AI insights |
| `settings` | SettingsScreen | Configuration |
| `review` | TransactionDetailScreen | Review OCR result before saving |
| `create` | TransactionDetailScreen | New manual transaction |
| `edit/{transactionId}` | TransactionDetailScreen | Edit existing transaction |

---

## Key Design Decisions

### 1. Sealed Class over Single-Table Inheritance

Instead of a single `Transaction` data class with many nullable fields, the sealed class hierarchy keeps each type's fields scoped to its own subclass. The database still uses a single `transactions` table with a `type` discriminator — the `TransactionMappers` handle the bidirectional conversion.

### 2. Money in Minor Units

All monetary amounts are stored as `Long` (minor units — e.g., cents for USD, dong for VND). This avoids floating-point precision issues. The `MoneyFormatter` handles display formatting based on `CurrencyConfig`, which defines decimal places and symbols per currency.

### 3. Image Storage on Filesystem

Receipt/transfer images are stored as files in `filesDir/receipts/` rather than as BLOBs in the database. The database only stores the filename. This keeps the database lightweight and avoids memory issues with large images.

### 4. OCR Auto-Detection

Rather than requiring the user to specify "receipt" vs. "wire transfer" before scanning, the Gemini prompt includes auto-detection logic. The AI examines the image content and sets the `type` field in its response, which determines the resulting `Transaction` subclass.

### 5. Wire Transfers Always Go to Review

Since wire transfer screenshots are structurally diverse and confidence tends to be lower, all wire transfer extractions route to the review screen (`ReviewNeeded`). Receipts with confidence ≥ 0.3 and valid data are auto-saved.

### 6. Period State Shared via ApiKeyManager

The selected period (week/month/custom with dates) is persisted in `SharedPreferences` via `ApiKeyManager`. Both `HomeViewModel` and `InsightsViewModel` read from the same source, ensuring consistent period context across tabs. `HomeViewModel` writes period changes; `InsightsViewModel` observes them.

### 7. Line Item Edit Mode

When editing receipt line items, users can choose between two modes:
- **Recalculate Total** — changes to line item amounts automatically update the transaction total.
- **Keep Total** — line items can be adjusted without affecting the saved total (useful when the OCR misread individual items but the total is correct).
