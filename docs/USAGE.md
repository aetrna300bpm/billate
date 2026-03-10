# App Usage Instructions

A complete guide to using **Billate** — from first launch to daily expense tracking.

---

## Table of Contents

1. [Initial Setup](#initial-setup)
2. [Home Screen Overview](#home-screen-overview)
3. [Scanning a Receipt](#scanning-a-receipt)
4. [Scanning a Wire Transfer](#scanning-a-wire-transfer)
5. [Adding a Manual Transaction](#adding-a-manual-transaction)
6. [Reviewing & Editing a Transaction](#reviewing--editing-a-transaction)
7. [Using the Dashboard](#using-the-dashboard)
8. [Searching & Filtering Transactions](#searching--filtering-transactions)
9. [Getting AI Insights](#getting-ai-insights)
10. [Settings & Configuration](#settings--configuration)
11. [Supported Currencies](#supported-currencies)
12. [Tips & Best Practices](#tips--best-practices)

---

## Initial Setup

### 1. Install the App

Build and install via Android Studio or `./gradlew installDebug` (see [README](../README.md) for build instructions).

### 2. Get a Gemini API Key

1. Go to [aistudio.google.com](https://aistudio.google.com/).
2. Sign in with your Google account.
3. Create an API key (it's free for moderate usage).
4. Copy the key.

### 3. Configure the App

1. Open Billate → tap the **Settings** tab (bottom navigation).
2. Paste your API key into the **API Key** field.
3. Tap **Save Key** — you'll see a "✓ Saved" confirmation.
4. (Optional) Select a **Model**:
   - `gemini-3-flash-preview` — default, latest model
   - `gemini-2.5-flash` — balanced
   - `gemini-2.5-flash-lite` — fastest, best for low-latency
5. (Optional) Set your **Default Currency** — used for manual transactions. Scanned receipts detect currency automatically.

You're ready to go!

---

## Home Screen Overview

The Home screen has three main sections:

### Dashboard Card (top)
- Shows **total spent**, **transaction count**, and a **category pie chart** for the selected period.
- Tap the **period dropdown** to switch between This Week, This Month, or Custom date range.

### Search Bar (below dashboard)
- Type to search transactions by name or note.
- Scroll the **category chips** horizontally and tap to filter by one or more categories.

### Transaction Log (scrollable list)
- Transactions are grouped by day with sticky headers: "Today", "Yesterday", or the formatted date.
- Each card shows the transaction name, amount, date, category, and an icon if it was scanned.
- **Tap any card** to open the edit screen.

### Floating Action Button (+)
- Tap the **+** button in the bottom-right corner.
- Three options appear:
  - **📷 Camera** — take a photo of a receipt or transfer screen.
  - **🖼️ Gallery** — pick an existing image.
  - **✏️ Manual** — create a transaction by hand.

---

## Scanning a Receipt

1. Tap **+** → **Camera** (or **Gallery**).
2. Point at the receipt and take a clear photo. Ensure:
   - The entire receipt is visible.
   - Text is legible (not blurry or cut off).
   - Good lighting with minimal glare.
3. The app sends the image to Gemini AI for extraction. A loading indicator appears.
4. Gemini returns extracted data:
   - **Merchant name**, **date**, **total amount**, **currency**
   - **Line items** (description, quantity, price)
   - **Adjustments** (service charge, discount, tax)
   - **Category** (auto-classified)
   - **Confidence score** (0–100%)

### What Happens Next?

- **High confidence (≥ 30%) with valid data** → the transaction is **auto-saved** and appears in your log immediately.
- **Low confidence or missing data** → you're taken to the **Review screen** to verify and correct the details before saving.

---

## Scanning a Wire Transfer

1. Tap **+** → **Camera** or **Gallery**.
2. Take a screenshot of your banking app's transfer confirmation, or photograph a transfer slip.
3. Gemini automatically detects that the image is a bank transfer (not a receipt).
4. Extracted data includes:
   - **Recipient name** and **bank**
   - **Transfer amount** and **currency**
   - **Transaction reference**
   - **Date**
   - **Category** (auto-classified)

### Wire Transfers Always Go to Review

Because transfer screenshots vary widely between banks, all wire transfer extractions are sent to the Review screen. Verify the details — especially the amount — before tapping **Save**.

---

## Adding a Manual Transaction

1. Tap **+** → **Manual**.
2. You're taken to the transaction form with empty fields.
3. Fill in:
   - **Name** — what this transaction is for.
   - **Total** — amount in minor units (e.g., `150000` for 150,000 VND or `1299` for $12.99).
   - **Category** — pick from the dropdown.
   - **Date** — tap to open a date picker.
   - **Note** — optional description.
4. Tap **Save**.

---

## Reviewing & Editing a Transaction

### Review Screen (after scanning)

When Gemini needs confirmation, you'll see the Review screen with:

- **Confidence indicator** — a warning banner showing extraction confidence percentage. Below 50% is shown in red.
- **Image thumbnail** — tap to view full-screen with pinch-to-zoom.
- **All extracted fields** — editable so you can correct any OCR mistakes.

### Edit Screen (from transaction log)

Tap any transaction card in the Home screen to open the Edit screen.

### Receipt-Specific Fields

- **Merchant Name** — the store or vendor.
- **Date from receipt** — the raw date string extracted by OCR (read-only).
- **Line Items** — each with description, quantity, and amount. You can:
  - Edit any field inline.
  - **Add** new items via the "Add" button.
  - **Remove** items via the delete icon.
  - Choose **edit mode**: "Update total" (recalculates from line items) or "Keep total" (preserves the original total).
- **Adjustments** — service charge, discount, tax (editable).

### Wire Transfer-Specific Fields

- **Recipient** — shown as read-only text (extracted from the image).
- **Extraction confidence** — shown below the recipient.

### Common Actions

- **Save** — saves changes and returns to the Home screen.
- **Delete** — appears only for existing transactions. Tap → confirm → the transaction and its image file are permanently deleted.

---

## Using the Dashboard

### Period Selection

The dashboard card at the top of the Home screen shows spending for a selected time period:

| Period | Range |
|---|---|
| **This Week** | Monday through today (or Sunday, depending on locale) |
| **This Month** | 1st of the current month through today |
| **Custom** | User-defined start and end dates |

Tap the dropdown to switch periods. For **Custom**, two date fields appear below the chart — tap each to pick dates.

### Pie Chart

The chart shows your spending distribution by category. The **legend** (below the chart) lists the top 5 categories with their percentage share.

### Summary Stats

- **Total Spent** — sum of all transaction amounts in the period.
- **Transaction Count** — number of transactions.

---

## Searching & Filtering Transactions

### Text Search

Type in the search bar to filter transactions. The search matches against:
- Transaction **name** (merchant name, recipient, manual name)
- Transaction **note**

The filter is case-insensitive and updates in real-time as you type.

### Category Filter

Below the search bar, scroll through the **category chips** (color-coded). Tap one or more to filter:
- Selecting a chip turns it on (highlighted with category color).
- Tap again to deselect.
- When **no chips** are selected, all categories are shown.
- When **one or more chips** are selected, only matching transactions appear.

Search and category filters work together — both must match for a transaction to appear.

---

## Getting AI Insights

1. Navigate to the **Insights** tab (bottom navigation).
2. You'll see:
   - **Period label** — the same period as your Home dashboard.
   - **Spending Summary card** — total spent, daily average, top category, transaction count.
3. Tap **Get AI Insight**.
4. Gemini analyzes your spending data and generates a text-based insight covering:
   - Spending patterns and trends
   - Category observations
   - Suggestions for saving
5. The insight is displayed in a card with the generation timestamp.

### Caching

- Insights are cached per period. If you tap "Get AI Insight" again for the same period, the cached version is shown instantly.
- Changing the period (via Home dashboard) clears the cache for a fresh analysis.

### Requirements

- At least one transaction must exist in the current period.
- A valid Gemini API key must be configured.

---

## Settings & Configuration

Navigate to the **Settings** tab to manage:

### Model Selection

Choose which Gemini model processes your images:

| Model | Characteristics |
|---|---|
| `gemini-3-flash-preview` | Latest, best accuracy (default) |
| `gemini-2.5-flash` | Good balance of speed and accuracy |
| `gemini-2.5-flash-lite` | Fastest response, lower cost |

### API Key

- Enter your Google AI API key.
- Toggle visibility with the eye icon.
- Tap **Save Key** to persist.
- The key is stored locally on your device in `SharedPreferences`.

### Default Currency

- Select from the supported currency list.
- This currency is used when creating **manual** transactions.
- **Scanned** transactions detect currency from the image content.

---

## Supported Currencies

| Code | Currency | Symbol | Minor Unit |
|---|---|---|---|
| VND | Vietnamese Dong | ₫ | 1 (no decimals) |
| USD | US Dollar | $ | 100 (2 decimals) |
| EUR | Euro | € | 100 (2 decimals) |
| GBP | British Pound | £ | 100 (2 decimals) |
| JPY | Japanese Yen | ¥ | 1 (no decimals) |
| KRW | Korean Won | ₩ | 1 (no decimals) |
| THB | Thai Baht | ฿ | 100 (2 decimals) |
| SGD | Singapore Dollar | S$ | 100 (2 decimals) |
| AUD | Australian Dollar | A$ | 100 (2 decimals) |
| CAD | Canadian Dollar | C$ | 100 (2 decimals) |

Amounts are stored in **minor units** internally (e.g., `$12.99` = `1299`, `150,000₫` = `150000`).

---

## Tips & Best Practices

1. **For best OCR results**: Take photos in good lighting, ensure the full receipt is visible, and avoid angles or shadows.
2. **Always review wire transfers**: They always go to the review screen — double-check the amount before saving.
3. **Use the confidence indicator**: A low score (red warning) means the AI was unsure. Pay close attention to the extracted amount and category.
4. **Line item mode matters**: If the total looks correct but individual items are off, use "Keep total" mode when editing line items.
5. **Leverage category filters**: Use the colored chips to quickly isolate spending in a specific category.
6. **Check insights regularly**: The AI summary can reveal spending patterns you might miss.
7. **Custom date ranges**: Use the Custom period to analyze spending for a specific trip, event, or pay period.
