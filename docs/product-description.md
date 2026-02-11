# Billate — Product Description (MVP)

## Vision
Billate is a personal spending tracker that removes manual data entry. The goal is to capture real-world spending with minimal effort by letting the user take a photo of a bill or receipt and letting AI extract and categorize the expense. The product is designed for fast capture, quick review when necessary, and a clean, searchable history of spending.

## Problem Statement
Manually logging expenses is slow and error-prone. Most people skip it entirely because it requires typing, categorizing, and remembering each transaction. Billate replaces this with a photo-first flow that automates extraction and categorization.

## Target User (MVP)
- Personal, single-user use.
- Android users.
- English UI.
- Vietnam context with VND amounts and common receipt formats.

## Core User Journey (MVP)
1. **Home Screen (Dashboard)**
   - A simple list of past transactions with merchant, date, category, and total.
   - This is the default landing screen.

2. **Add a Bill**
   - Floating action button (+).
   - User chooses **Camera** or **Gallery**.

3. **AI Extraction**
    - The selected image is sent to Gemini via the API.
    - The model returns a **single strict JSON** response with:
       - Merchant name
       - Transaction date
       - Total amount (normalized to VND)
       - Currency (VND)
       - Category (from the fixed list)
       - Line items (description + normalized VND price + raw text)

4. **Validation & Save**
   - If the extraction is consistent and reliable, the transaction is auto‑saved.
   - If inconsistencies are detected, the user is taken to a Review screen.
   - If the user exits before saving, the draft is discarded (no persistent drafts in MVP).

5. **Review (Human‑in‑the‑Loop)**
   - Editable fields for merchant, date, total, category, and line items.
   - Users can add or delete line items.
   - The screen is pre‑filled with the best‑effort extraction.
   - User makes corrections and taps Save.

6. **Stored Locally**
   - The transaction is saved in a local Room database.
   - It appears on the Home Screen immediately.

## Validation Policy (MVP)
Billate uses a **single‑response, strict JSON strategy** for predictable parsing. The prompt instructs the model to:
- Choose a category **only from the fixed list**.
- Output both **normalized VND amounts** and the **raw amount text** seen on the receipt.

The exact JSON schema is defined in [docs/prompt-spec.md](docs/prompt-spec.md).

Auto‑save only happens when:
1. Line items sum to the total after VND normalization.
2. Required fields are present and valid (merchant, date, total, category).

If any check fails, the Review screen is required.

## VND Normalization (MVP)
Receipts sometimes write 100.00 to mean 100.000 VND. The app should normalize values so totals and line items match.

Recommended approach:
- Ask the model to output **raw numeric text as seen** on the receipt.
- Ask the model to output **normalized VND integers** for each amount.
- If totals do not match after normalization, require manual review.

## Scope Decisions (MVP)
- Android only.
- Online processing only (Gemini API).
- Local storage only (Room).
- No user accounts or cloud sync.
- Fixed categories.
- English UI.

## End Goal (Beyond MVP)
- Optional backend for sync and multi‑device support.
- Editable categories and budget goals.
- Export (CSV/PDF).
- Analytics and spending insights.
- Multi‑currency support.
