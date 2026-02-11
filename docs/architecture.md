# Billate — Architecture (MVP)

## Architectural Style
Billate follows a **layered MVVM architecture** with **UDF (Unidirectional Data Flow)** and a clear separation of concerns:

- **UI Layer (Compose):** renders state and emits user actions.
- **Domain Layer:** business rules and validation logic.
- **Data Layer:** repository and data sources for Gemini and Room.

This aligns with the recommended Android architecture guidance.

---

## Modules / Packages (single‑module MVP)
Suggested package structure (within the `app` module):

- `ui/` — screens, components, navigation
- `viewmodel/` — ViewModels and UiState
- `domain/` — use cases and validation logic
- `data/` — repository and data sources
- `data/local/` — Room entities, DAO, database
- `data/remote/` — Gemini API client and DTOs
- `model/` — shared domain models

---

## UI Layer
### Screens
- **HomeScreen**: list of saved transactions.
- **BillReviewScreen**: editable receipt fields, including add/delete line items.

### UiState (single state per screen)
Use sealed classes for each screen state. Example:
- Initial
- Loading / Processing
- Success (data)
- Error (message)

### UI Actions
- `onAddBillClicked()`
- `onImageSelected(uri)`
- `onSaveReviewClicked(editedBill)`

---

## Domain Layer (Use Cases)
### 1. `ProcessBillUseCase`
- Input: image URI
- Orchestrates:
  1. Call Gemini once with a **strict JSON prompt**.
  2. Normalize currency values (using the model’s normalized VND outputs).
  3. Validate consistency.
  4. If valid → save.
  5. If invalid → return a reviewable model.

### 2. `ValidateBillUseCase`
- Checks:
  - Line item sum equals total after normalization.
  - Category is within the fixed list.
  - Basic sanity checks (non‑empty merchant, positive total).

### 3. `NormalizeCurrencyUseCase`
- Rule for VND formatting:
  - Ask Gemini to output **raw numeric text** from the receipt.
  - Ask Gemini to output **normalized VND integers** per amount.
  - If totals do not reconcile, force manual review.

### 4. `SaveBillUseCase`
- Persists to Room via repository.

---

## Data Layer
### Repository (Single Source of Truth)
`BillRepository` exposes:
- `getBills(): Flow<List<BillTransaction>>`
- `processBill(imageUri): Result<BillProcessingOutcome>`
- `saveBill(bill): Unit`

### Remote Data Source
`GeminiRemoteDataSource`:
- Handles API calls and response parsing.
- Single call per receipt with strict JSON output.
- Uses the official Google AI Android SDK.

### Image Conversion
- Use an `ImageReader`/`ImageDataSource` abstraction that wraps `ContentResolver` to read a Uri into bytes.
- Inject this abstraction into the repository to keep the repository Kotlin‑only and unit‑testable.

### Local Data Source
`BillLocalDataSource` (Room):
- Entities for transactions and line items.
- DAO for CRUD operations.

---

## Validation Strategy (Detailed)
Auto‑save only when **all** conditions pass:
1. Sum(line items) == total after VND normalization.
2. Category is in the fixed list.
3. Required fields are present and valid.

If validation fails, still return the **best‑effort parsed data** so the Review screen is pre‑filled.

The exact JSON schema and prompt format are defined in [docs/prompt-spec.md](docs/prompt-spec.md).

Otherwise, return `BillProcessingOutcome.RequiresReview`.

---

## Dependency Injection
Use Hilt for:
- Repository and data sources.
- Gemini API client.
- Room database and DAO.
- Use cases.

---

## Error Handling
- Surface AI or network errors as a UI `Error` state.
- If parsing fails or response is incomplete, require manual review.

## Background Processing (MVP)
- Processing runs in `viewModelScope` while the screen is active.
- If the app is closed before saving, the in‑progress scan is discarded.
- WorkManager can be introduced later for reliability in the background.

---

## Privacy & Security (MVP)
- Images are sent directly to Gemini via API.
- No server or user accounts.
- Local data only.

---

## Open Architecture Items
- Add offline queue when a backend exists.
- Add analytics and export.
- Add editable categories.
