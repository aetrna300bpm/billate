# Gemini API Debugging Guide (Billate MVP)

This document explains exactly how the app communicates with the Gemini API, where the prompt lives, what gets sent, and how the response is parsed. Use this for debugging quota issues, parsing errors, and unexpected outputs.

## 1) High-Level Request Flow

1. **User selects a receipt image** (Gallery picker in Home screen).
2. **Uri → Bitmap conversion** via `ImageDataSource`.
3. **Gemini API call** with **image + strict JSON prompt**.
4. **Raw response text** returned by Gemini.
5. **JSON parsing** into `GeminiReceiptResponse` via Kotlinx Serialization.
6. **Normalization + validation**.
7. **Auto-save** to Room or **Review** screen.

## 2) Key Files (Entry Points)

- Prompt + API call: [app/src/main/java/com/billate/app/data/remote/GeminiRemoteDataSource.kt](app/src/main/java/com/billate/app/data/remote/GeminiRemoteDataSource.kt)
- Uri → Bitmap: [app/src/main/java/com/billate/app/data/remote/ImageDataSource.kt](app/src/main/java/com/billate/app/data/remote/ImageDataSource.kt)
- Response model: [app/src/main/java/com/billate/app/model/GeminiReceiptResponse.kt](app/src/main/java/com/billate/app/model/GeminiReceiptResponse.kt)
- Repository orchestration: [app/src/main/java/com/billate/app/data/DefaultBillRepository.kt](app/src/main/java/com/billate/app/data/DefaultBillRepository.kt)
- Validation + normalization: [app/src/main/java/com/billate/app/domain/ValidateBillUseCase.kt](app/src/main/java/com/billate/app/domain/ValidateBillUseCase.kt), [app/src/main/java/com/billate/app/domain/NormalizeCurrencyUseCase.kt](app/src/main/java/com/billate/app/domain/NormalizeCurrencyUseCase.kt)
- API key storage: [app/src/main/java/com/billate/app/data/local/ApiKeyManager.kt](app/src/main/java/com/billate/app/data/local/ApiKeyManager.kt)

## 3) The Prompt (Exact Text)

The prompt is built in `GeminiRemoteDataSource.buildPrompt()` and sent alongside the image.

```
Extract the receipt details and return ONLY valid JSON following this schema:

{
  "merchant_name": "string",
  "transaction_date": "YYYY-MM-DD",
  "transaction_date_raw": "string (raw date text as seen on the receipt)",
  "currency": "VND",
  "total_amount_vnd": integer (exact VND, no decimals),
  "total_amount_raw": "string (raw text from receipt)",
  "category": "one of: Groceries, Dining, Shopping, Transport, Utilities, Health, Entertainment, Education, Other",
  "line_items": [
    {
      "description": "string",
      "qty": integer,
      "amount_vnd": integer (exact VND, no decimals),
      "amount_raw": "string"
    }
  ],
  "notes": "string"
}

Rules:
1) Output only JSON. No markdown, no extra text.
2) If unsure, use empty string or 0, and explain briefly in notes.
3) Normalize amounts into VND integers (no decimals).
4) Preserve the raw amount text exactly as printed.
5) If quantity is missing, use 1.
6) For transaction_date, make a best guess in YYYY-MM-DD, and always include the exact transaction_date_raw string.
7) Category must be exactly one of: Groceries, Dining, Shopping, Transport, Utilities, Health, Entertainment, Education, Other.
```

System instruction (applied at model creation):

```
You are an OCR assistant. Extract data from receipt images and return only JSON following the exact schema provided. Do not include any extra text or markdown.
```

## 4) How the API Call Is Made

### 4.1 Model creation
The model is created dynamically per request using the stored API key (from Settings):

```kotlin
private fun createModel(): GenerativeModel {
    val apiKey = apiKeyManager.getApiKey()
    require(apiKey.isNotBlank()) { "API key not set. Please add your Gemini API key in Settings." }
    return GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.2f
            topK = 32
            topP = 1f
            maxOutputTokens = 4096
        },
        systemInstruction = content {
            text("You are an OCR assistant. Extract data from receipt images and return only JSON following the exact schema provided. Do not include any extra text or markdown.")
        },
    )
}
```

### 4.2 Request payload
The image and the prompt are sent together:

```kotlin
val inputContent = content {
    image(bitmap)
    text(prompt)
}

val response = model.generateContent(inputContent)
```

## 5) Response Parsing

The SDK returns a text response. The app strips code fences if Gemini adds them, then parses strict JSON.

```kotlin
val rawText = response.text ?: throw IllegalStateException("Empty response from Gemini")

val jsonText = rawText
    .replace(Regex("```json\\s*"), "")
    .replace(Regex("```\\s*"), "")
    .trim()

return json.decodeFromString<GeminiReceiptResponse>(jsonText)
```

## 6) Example LLM Output (Expected JSON)

```json
{
  "merchant_name": "Circle K",
  "transaction_date": "2026-02-10",
  "transaction_date_raw": "10/02/2026",
  "currency": "VND",
  "total_amount_vnd": 45000,
  "total_amount_raw": "45.000",
  "category": "Shopping",
  "line_items": [
    { "description": "Coca Cola", "qty": 2, "amount_vnd": 20000, "amount_raw": "20.000" },
    { "description": "Banh mi", "qty": 1, "amount_vnd": 25000, "amount_raw": "25.000" }
  ],
  "notes": ""
}
```

## 7) Validation & Decision Path

- **NormalizeCurrencyUseCase** applies VND x1000 heuristic if line item sum mismatches total.
- **ValidateBillUseCase** checks:
  - Merchant name present
  - Transaction date present
  - Total > 0
  - Category is valid
  - `sum(line_items.amount_vnd * qty) == total_amount_vnd`

If valid → auto-save. If invalid → Review screen with pre-filled fields.

## 8) Common Failure Points

- **429 TooManyRequests** → Rate limit exceeded. Wait 1–2 minutes, then retry.
- **Invalid API key** → Key not saved or incorrect.
- **JSON parse error** → Gemini returned malformed JSON or extra text.
- **Missing fields** → Gemini returned empty strings or 0s; Review screen required.

## 9) Debug Checklist

1. Check API key saved in Settings.
2. Verify API usage and rate limits in AI Studio.
3. Inspect `response.text` to see if Gemini returned non-JSON.
4. Confirm prompt was sent with the image.
5. Confirm validation logic didn’t reject a correct response.

## 10) Related Dependencies

- Google AI SDK: `com.google.ai.client.generativeai:generativeai`
- Kotlinx Serialization: `org.jetbrains.kotlinx:kotlinx-serialization-json`
