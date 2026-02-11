# Restructure Plan (Modular + Currency Agnostic)

**Status:** ✅ COMPLETE (February 2026)

> **See Also:** 
> - [Restructure Guide](./restructure-guide.md) — Comprehensive architecture documentation
> - [Quick Reference](./quick-reference.md) — Common development tasks

## Summary of Implementation

The restructuring has been successfully completed and the app **builds with zero errors**. See the guides above for detailed documentation. Below is the original plan for reference.

---

## Original Plan

## Goals
- Make the app modular for future growth.
- Make money handling currency-agnostic (ISO 4217 string, no hard-coded currency logic).
- Support both receipt-derived transactions and manual transactions.
- Separate core domain from UI and vendor-specific integrations (Gemini).

## Core domain model (currency-agnostic)
**Main entity: `Transaction`**
- `id: Long`
- `timestamp: Long` (or `LocalDateTime` if you prefer)
- `amount: Money`
- `category: Category`
- `source: TransactionSource` (Receipt or Manual)
- `bill: Bill?` (optional attachment)

**Value object: `Money`**
- `amountMinor: Long` (store in minor units, e.g., cents)
- `currency: String` (ISO 4217)

**Bill (optional attachment, embedded in Transaction)**
- `id: Long` (or omit if embedded)
- `merchantName: String`
- `lineItems: List<LineItem>` (optional)
- `billImageUri: String?` (local file path or cache URI)
- `originalCurrency: String?` (optional, in case bill is in foreign currency but converted)

## Suggested package/module structure
```
app/
  src/main/java/com/billate/app/
    core/
      model/          // Money, Transaction, Category, Bill, LineItem
      util/           // Time, formatting, validation helpers
      currency/       // Currency formatting, symbol mapping, locale helpers
    data/
      local/          // Room entities + DAO + mappers
      remote/         // Gemini API, image reading
      repository/     // TransactionRepository, BillRepository (if needed)
    domain/
      usecase/        // Use cases for create, update, analyze, import
    ui/
      navigation/     // NavHost, Routes
      screens/        // Home, Dashboard, TransactionDetail, Settings
      components/     // Reusable composables
    viewmodel/        // ViewModels per screen

:feature-dashboard/
  ui/                // Charts, cards, filters
  domain/            // Analytics use cases
  data/              // Local analytics queries

:feature-receipt/
  data/              // Receipt extraction, Gemini prompt/parsing
  domain/            // ExtractReceiptUseCase
  ui/                // Scan flow
```

## Room schema mapping (example)
- `TransactionEntity` → single table with all fields (amount, currency, timestamp, category, source, merchant, billImageUri, etc.)
- Optional: split into separate `BillEntity` table if you want strict normalization, but embedding is simpler for 1-to-1 relationship.

## Currency strategy
- Use `Money(currency = "USD", amountMinor = 12345)` everywhere in domain.
- Formatting is done in `core/currency` (e.g., `MoneyFormatter`).
- Prompting for receipt extraction should request currency code + amounts.
- No business logic should assume VND.

## Transaction vs Bill
Your idea is sound: make `Transaction` the primary entity and attach an optional `Bill`.
This allows:
- Manual transactions (no receipt)
- Receipt-scanned transactions (have Bill attached)

Recommended extra fields:
- `note: String?` for manual entries
- `tags: List<String>` (optional)
- `paymentMethod: String?` (optional)

## File-by-file documentation (initial)
- `core/model/Money.kt` → Value object for amount + currency.
- `core/model/Transaction.kt` → Main domain entity (includes optional Bill fields).
- `core/model/LineItem.kt` → Optional detail items.
- `core/currency/CurrencyConfig.kt` → Currency-specific rules (display precision, rounding, symbols).
- `core/currency/MoneyFormatter.kt` → Formats Money by locale/currency.
- `data/local/TransactionEntity.kt` → Room table (flattened, includes bill fields).
- `data/local/LineItemEntity.kt` → Room table (one-to-many with Transaction).
- `data/local/TransactionDao.kt` → Queries for list, filter, range, by date.
- `data/repository/TransactionRepository.kt` → Interface + impl.
- `domain/usecase/CreateTransactionUseCase.kt` → Save manual or scanned.
- `domain/usecase/AnalyzeSpendingUseCase.kt` → Aggregations.
- `feature-receipt/data/ReceiptExtractor.kt` → Gemini prompt + parsing.
- `feature-receipt/domain/ExtractReceiptUseCase.kt` → Orchestrate extraction + transaction creation.
- `feature-receipt/ui/ReceiptScanScreen.kt` → Camera/gallery + processing UI.
- `feature-dashboard/ui/DashboardScreen.kt` → Summary + charts.
- `ui/screens/TransactionDetailScreen.kt` → View/edit transaction + bill image preview.

## Notes
- **Primary user flow**: Image → OCR → Transaction. Everything is designed to make this frictionless.
- You can view bill details (including image) from the transaction detail screen.
- Manual transaction entry is secondary (no bill image).
- This structure makes analytics clean (query `Transaction` by date, category, merchant).
- A future currency selector becomes a user setting, not a model change.
- Currency-specific logic (rounding, precision) is centralized in `core/currency/`.

## Suggested implementation order
1. Build `core/` (Money, Transaction, CurrencyConfig, formatters).
2. Build `data/` (Room entities, DAO, repository).
3. Build `feature-receipt/` (Gemini extraction, ReceiptScanScreen).
4. Build `feature-dashboard/` (analytics queries, UI).
5. Refactor existing UI screens to use new Transaction model.

## Decisions (confirmed)
1. **Money storage**: Store as `amountMinor: Long` (minor units). Currency-specific rounding logic goes in `core/currency/CurrencyConfig.kt`. Example:
   - VND: 1000 VND is the smallest displayable unit (no cents).
   - USD: 1 cent is the smallest unit.
   - Formatting uses `CurrencyConfig.displayPrecision(currency)` to decide decimal places.

2. **Bill embedding**: Bill is embedded inside Transaction (one-to-one). From user perspective, a transaction *is* a bill if it came from OCR. Room schema can flatten this into a single table with nullable bill fields.

3. **Single currency per user**: One currency only. Multi-currency conversions happen outside the app (e.g., via online banking). Transaction amount is always in the user's selected currency.

4. **Image storage**: Store the receipt image file reference (local URI or filename). No raw OCR text storage (too verbose). The extracted fields are what matter; the image is for user reference/audit.

## Critical user flow (primary path)
User → Camera/Gallery → Image → Gemini extracts → Transaction created → User can view/edit → Image stored with transaction.

This is the core of Billate. Everything else is optional.

## Storage strategy for receipt images
- Save to `app/cache` or `app/files` (internal storage, scoped to app).
- Store filename/URI in `TransactionEntity.billImageUri`.
- If user deletes transaction, delete the image file too.
- Consider lazy loading for list view (don't decode all images at once).
