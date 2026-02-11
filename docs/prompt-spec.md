# Billate — Prompt & JSON Schema (MVP)

## Purpose
This document defines the **exact prompt** and **strict JSON schema** used to parse receipt data from Gemini. The schema is designed for deterministic parsing and VND normalization.

---

## Fixed Categories (MVP)
The model must choose **one** from:
- Groceries
- Dining
- Shopping
- Transport
- Utilities
- Health
- Entertainment
- Education
- Other

---

## Required JSON Schema (MVP)
The model must return **only** a JSON object with this structure:

```json
{
  "merchant_name": "string",
  "transaction_date": "YYYY-MM-DD",
  "transaction_date_raw": "string",
  "currency": "VND",
  "total_amount_vnd": 0,
  "total_amount_raw": "string",
  "category": "Groceries|Dining|Shopping|Transport|Utilities|Health|Entertainment|Education|Other",
  "line_items": [
    {
      "description": "string",
      "qty": 1,
      "amount_vnd": 0,
      "amount_raw": "string"
    }
  ],
  "notes": "string"
}
```

### Field Rules
- `total_amount_vnd` and `amount_vnd` are **integers** representing exact VND (no decimals).
- `total_amount_raw` and `amount_raw` are **verbatim** text as seen on the receipt (including separators like “.” or “,”).
- `notes` is optional and can be an empty string; use it for unclear items or anomalies.
- If a field is unknown, return an empty string (or `0` for numeric fields). Do not omit fields.

---

## Prompt (Draft for MVP)

**System**
You are an OCR assistant. Extract data from the receipt image and return only JSON, following the exact schema provided. Do not include any extra text.

**User**
Extract the receipt details and return ONLY valid JSON following this schema:

- merchant_name (string)
- transaction_date (YYYY-MM-DD)
- transaction_date (YYYY-MM-DD)
- transaction_date_raw (string, raw date text as seen on the receipt)
- currency (must be "VND")
- total_amount_vnd (integer, exact VND)
- total_amount_raw (string, raw text from receipt)
- category (must be one of: Groceries, Dining, Shopping, Transport, Utilities, Health, Entertainment, Education, Other)
- line_items (array of { description, qty, amount_vnd, amount_raw })
- notes (string)

Rules:
1) Output only JSON. No markdown.
2) If unsure, use empty string or 0, and explain briefly in notes.
3) Normalize amounts into VND integers (no decimals).
4) Preserve the raw amount text exactly as printed.
5) If quantity is missing, use 1.
6) For `transaction_date`, make a best guess in YYYY-MM-DD, and always include the exact `transaction_date_raw` string.

---

## Parsing / Validation Notes
- If `sum(line_items.amount_vnd)` equals `total_amount_vnd`, the bill can be auto‑saved.
- If the sum does not match, send to Review.
- If category is not in the fixed list, send to Review.
- If `transaction_date` looks invalid or ambiguous, fall back to `transaction_date_raw` in the Review screen.

### VND Correction Heuristic (MVP)
If `sum(line_items.amount_vnd) != total_amount_vnd`, check whether `sum * 1000 == total_amount_vnd`. If yes, auto‑correct the line item amounts by multiplying by 1000, then re‑validate.

---

## Prompt Decisions (MVP)
- No dedicated tax/discount fields.
- Line items include `qty` (default 1).
- No confidence field.
