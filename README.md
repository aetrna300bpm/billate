# Billate

**AI-powered expense tracker for Android** — scan receipts and bank transfer screenshots with Google Gemini, or add transactions manually. Track spending by category with a visual dashboard, search and filter your history, and get AI-generated spending insights.

---

## Features

| Feature | Description |
|---|---|
| **Receipt OCR** | Point your camera at a receipt or pick from gallery — Gemini extracts merchant, line items, adjustments, total, category, and currency automatically. |
| **Wire Transfer OCR** | Scan bank transfer screenshots — Gemini detects the transfer type and extracts recipient, amount, reference, and bank details. |
| **Manual Entry** | Add transactions by hand with amount, category, date, and notes. |
| **Dashboard** | Period-based spending summary (week / month / custom range) with category pie chart and legend. |
| **Grouped Transaction Log** | Transactions grouped by day with sticky date headers — "Today", "Yesterday", or formatted dates. |
| **Search & Filter** | Full-text search across transaction names and notes, plus category filter chips with color coding. |
| **AI Insights** | One-tap AI-generated spending analysis for the current period, with caching. |
| **Multi-Currency** | Supports VND, USD, EUR, GBP, JPY, KRW, THB, SGD, AUD, CAD with correct decimal handling. |
| **Image Viewer** | Tap any receipt/transfer image to view full-screen with pinch-to-zoom. |

---

## Screenshots

> *Coming soon — add screenshots to a `screenshots/` folder and reference them here.*

---

## Quick Start

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17**
- **Android SDK 34** (compileSdk)
- A **Google AI API key** from [aistudio.google.com](https://aistudio.google.com/)

### Build & Run

```bash
# Clone the repository
git clone https://github.com/<your-username>/billate.git
cd billate

# Open in Android Studio — it will sync Gradle automatically.
# Or build from command line:
./gradlew assembleDebug

# Install on a connected device / emulator:
./gradlew installDebug
```

### First Launch

1. Open the app → navigate to the **Settings** tab.
2. Paste your **Gemini API key** and tap **Save Key**.
3. (Optional) Choose your preferred **AI model** and **default currency**.
4. Go back to **Home** → tap the **+** button to scan a receipt or add a transaction manually.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9.24 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM · Repository · Use Cases |
| DI | Hilt (Dagger) 2.51.1 |
| Database | Room 2.6.1 (SQLite) |
| AI / OCR | Google Generative AI SDK 0.9.0 (Gemini) |
| Image Loading | Coil 2.6.0 |
| Serialization | kotlinx.serialization 1.6.3 |
| Navigation | Jetpack Navigation Compose 2.7.7 |
| Build | Gradle 8.7 · AGP 8.5.2 · KSP |
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 34 (Android 14) |

---

## Project Structure

```
app/src/main/java/com/billate/app/
├── MainActivity.kt                          # Hilt entry point
├── BillateApplication.kt                    # Application class (@HiltAndroidApp)
│
├── core/
│   ├── model/                               # Domain models (pure Kotlin)
│   │   ├── Transaction.kt                   #   Sealed class: Receipt, WireTransfer, Manual
│   │   ├── Money.kt                         #   Currency-aware amount (minor units)
│   │   ├── Category.kt                      #   9 spending categories
│   │   ├── LineItem.kt                      #   Receipt line item
│   │   ├── CategoryAmount.kt                #   Category with percentage (dashboard)
│   │   ├── DashboardState.kt                #   Dashboard UI state
│   │   ├── GroupedTransactions.kt            #   Day-grouped transaction list
│   │   └── PeriodType.kt                    #   WEEK / MONTH / CUSTOM enum
│   └── currency/
│       └── MoneyFormatter.kt                #   Format & parse amounts per currency
│
├── data/
│   ├── local/                               # Room database
│   │   ├── BillateDatabase.kt               #   Room DB v4 + migrations
│   │   ├── TransactionEntity.kt             #   Flat entity (all tx types)
│   │   ├── LineItemEntity.kt                #   Line items entity
│   │   ├── TransactionWithLineItems.kt      #   Room @Relation
│   │   ├── TransactionDao.kt                #   DAO with CRUD + date-range queries
│   │   └── TransactionMappers.kt            #   Entity ↔ Domain mappers
│   ├── remote/
│   │   ├── ReceiptExtractor.kt              #   Gemini API client + OCR prompt
│   │   └── GeminiReceiptResponse.kt         #   Serializable API response model
│   └── repository/
│       ├── TransactionRepository.kt         #   Repository interface
│       ├── DefaultTransactionRepository.kt  #   Repository implementation
│       └── ReceiptImageStorage.kt           #   File-based image storage
│
├── domain/usecase/                          # Business logic
│   ├── ProcessReceiptUseCase.kt             #   Image → Gemini → save → Transaction
│   ├── SaveTransactionUseCase.kt
│   └── DeleteTransactionUseCase.kt
│
├── di/
│   └── AppModule.kt                         # Hilt dependency injection module
│
├── viewmodel/
│   ├── HomeViewModel.kt                     # Dashboard, search, grouped list, image processing
│   ├── TransactionDetailViewModel.kt        # Edit/review screen state machine
│   ├── InsightsViewModel.kt                 # AI insight generation + spending summary
│   └── SettingsViewModel.kt                 # API key, model, currency preferences
│
└── ui/
    ├── navigation/
    │   └── BillateNavHost.kt                # 3-tab nav + routes (home, insights, settings, review, create, edit)
    ├── screens/
    │   ├── HomeScreen.kt                    # Dashboard + search + grouped log + FAB
    │   ├── TransactionDetailScreen.kt       # Edit/review form with type-specific sections
    │   ├── InsightsScreen.kt                # Summary + AI insight card
    │   └── SettingsScreen.kt                # API key, model, currency settings
    ├── components/
    │   ├── DashboardCard.kt                 # Period selector, summary stats, pie chart, legend
    │   ├── CategoryPieChart.kt              # Canvas donut chart
    │   ├── FullScreenImageViewer.kt         # Pinch-to-zoom overlay
    │   └── SearchBar.kt                     # Search field + category filter chips
    └── theme/
        ├── Theme.kt                         # Material 3 light theme
        └── CategoryColors.kt               # Color mapping for 9 categories
```

---

## Documentation

| Document | Description |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architecture design, data flow diagrams, sealed class hierarchy, database schema |
| [docs/USAGE.md](docs/USAGE.md) | Step-by-step app usage instructions for end users |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Developer guide — environment setup, adding features, database migrations, Gemini prompt tuning |

---

## License

This project is provided as-is for educational and personal use.
