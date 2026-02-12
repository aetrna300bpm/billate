package com.billate.app.data.remote

import android.graphics.Bitmap
import com.billate.app.data.local.ApiKeyManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends receipt images to Gemini and parses the structured response.
 * The prompt is currency-agnostic — the model detects the currency.
 */
@Singleton
class ReceiptExtractor @Inject constructor(
    private val apiKeyManager: ApiKeyManager,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createModel(): GenerativeModel {
        val apiKey = apiKeyManager.getApiKey()
        require(apiKey.isNotBlank()) { "API key not set. Please add your Gemini API key in Settings." }
        val modelName = apiKeyManager.getModelName()
        return GenerativeModel(
            modelName = modelName,
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

    suspend fun extract(bitmap: Bitmap): GeminiReceiptResponse {
        val model = createModel()
        val prompt = buildPrompt()
        val inputContent = content {
            image(bitmap)
            text(prompt)
        }
        val response = model.generateContent(inputContent)
        val rawText = response.text
            ?: throw IllegalStateException("Empty response from Gemini")

        // Strip markdown code fences if present
        val jsonText = rawText
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        return json.decodeFromString<GeminiReceiptResponse>(jsonText)
    }

    private fun buildPrompt(): String = """
Extract the receipt details and return ONLY valid JSON following this schema:

{
  "merchant_name": "string",
  "transaction_date": "YYYY-MM-DD",
  "transaction_date_raw": "string (raw date text as seen on the receipt)",
  "currency": "ISO 4217 3-letter code (e.g. VND, USD, EUR, JPY)",
  "final_total": integer (the amount the customer actually paid, in the smallest unit of the currency),
  "total_amount_raw": "string (raw total text from receipt)",
  "category": "one of: Groceries, Dining, Shopping, Transport, Utilities, Health, Entertainment, Education, Other",
  "line_items": [
    {
      "description": "string",
      "qty": integer,
      "unit_price": integer or null (per-unit price if shown, in smallest currency unit),
      "amount": integer (total for this line, in smallest currency unit),
      "amount_raw": "string"
    }
  ],
  "adjustments": {
    "service_charge": { "amount": integer, "amount_raw": "string" },
    "discount": { "amount": integer (NEGATIVE), "amount_raw": "string" },
    "tax": { "amount": integer, "amount_raw": "string" }
  },
  "notes": "string",
  "confidence": float (0.0 to 1.0)
}

Rules:
1) Output ONLY valid JSON. No markdown, no extra text.
2) Detect the currency from the receipt and report it as an ISO 4217 code.
3) ALL amounts are integers in the currency's smallest unit (e.g. cents for USD, đồng for VND).
4) final_total is the FINAL amount the customer paid. This is always required.
5) Preserve raw amount text exactly as printed on the receipt.

LINE ITEMS:
- Extract each item the customer purchased.
- Include unit_price if visible on the receipt; otherwise set to null.
- amount = qty × unit_price, or the total amount printed for that line.
- If quantity is not shown, default to 1.

ADJUSTMENTS — CRITICAL RULE:
- ONLY include an adjustment field if it is EXPLICITLY listed as a separate line on the receipt.
- If the receipt shows items at their final price (tax-inclusive) with no separate tax line, do NOT include a tax adjustment.
- If there is no service charge line on the receipt, do NOT include service_charge.
- If there is no discount line on the receipt, do NOT include discount.
- If none of the three adjustments apply, omit the "adjustments" field entirely or set it to null.
- service_charge: positive amount (gratuity, delivery fee, service fee, etc.)
- discount: NEGATIVE amount (coupon, promotion, membership discount, etc.)
- tax: positive amount. If the receipt lists multiple tax lines (e.g. VAT and luxury tax), SUM them into one tax amount and list all tax names in amount_raw.

CONFIDENCE:
- 1.0: Receipt is clear, all items legible, totals match
- 0.8: Mostly clear, minor items partially obscured
- 0.5: Significant uncertainty in items or totals
- Below 0.3: Receipt is too damaged or unclear to rely on

CATEGORY must be exactly one of: Groceries, Dining, Shopping, Transport, Utilities, Health, Entertainment, Education, Other.

For transaction_date, make a best guess in YYYY-MM-DD and always include the exact raw date string.
If unsure about any field, set confidence accordingly and explain in notes.
    """.trimIndent()
}
