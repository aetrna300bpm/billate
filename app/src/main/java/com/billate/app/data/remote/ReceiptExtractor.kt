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
                text("You are an OCR assistant. Extract data from receipt images or bank transfer screenshots and return only JSON following the exact schema provided. Do not include any extra text or markdown.")
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
Analyze this financial image and return ONLY valid JSON.

IMAGE TYPE DETECTION:
First, determine what kind of financial image this is:
- "receipt" — a bill from a store, restaurant, or service provider
- "wire_transfer" — a bank transfer confirmation screenshot (e.g., VCB, BIDV, MB Bank, Vietcombank, Techcombank, etc.)

═══════════════════════════════════════════
JSON SCHEMA
═══════════════════════════════════════════

{
  "type": "receipt" or "wire_transfer",
  "transaction_date": "YYYY-MM-DD",
  "transaction_date_raw": "string (raw date text as printed)",
  "currency": "ISO 4217 3-letter code (e.g. VND, USD, EUR, JPY)",
  "final_total": integer (amount in smallest currency unit),
  "total_amount_raw": "string (raw amount text)",
  "category": "one of: Groceries, Dining, Shopping, Transport, Utilities, Health, Entertainment, Education, Other",
  "notes": "string",
  "confidence": float (0.0 to 1.0),

  // RECEIPT-ONLY fields (omit or leave default for wire transfers):
  "merchant_name": "string",
  "line_items": [ { "description": "string", "qty": integer, "unit_price": integer or null, "amount": integer, "amount_raw": "string" } ],
  "adjustments": { "service_charge": { "amount": integer, "amount_raw": "string" }, "discount": { "amount": integer (NEGATIVE), "amount_raw": "string" }, "tax": { "amount": integer, "amount_raw": "string" } },

  // WIRE TRANSFER-ONLY fields (omit or leave null for receipts):
  "recipient_name": "string (person or company receiving money)",
  "recipient_bank": "string (bank of the recipient, if shown)",
  "transaction_reference": "string (reference/transaction code, if shown)"
}

═══════════════════════════════════════════
IF THIS IS A WIRE TRANSFER:
═══════════════════════════════════════════
- Set "type" to "wire_transfer"
- Extract the transfer amount as "final_total" (in smallest currency unit)
- Set "recipient_name" to the name of the person or company receiving the money
- Set "recipient_bank" to the recipient's bank name if visible
- Set "transaction_reference" to the reference/transaction code if visible
- Set "merchant_name" to "" (empty — not applicable)
- Set "line_items" to []
- Set "adjustments" to null
- Set "category" to "Other"
- In "notes", mention the source bank app if identifiable (e.g. "Via Vietcombank app")

═══════════════════════════════════════════
IF THIS IS A RECEIPT:
═══════════════════════════════════════════
- Set "type" to "receipt"
- Set "recipient_name", "recipient_bank", "transaction_reference" to null
- Proceed with receipt extraction:

Rules:
1) Output ONLY valid JSON. No markdown, no extra text.
2) Detect the currency and report it as an ISO 4217 code.
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
- If there is no service charge line, do NOT include service_charge.
- If there is no discount line, do NOT include discount.
- If none apply, omit "adjustments" or set to null.
- service_charge: positive amount (gratuity, delivery fee, service fee, etc.)
- discount: NEGATIVE amount (coupon, promotion, membership discount, etc.)
- tax: positive amount. Sum multiple tax lines into one.

CONFIDENCE:
- 1.0: Image is clear, all data legible, totals make sense
- 0.8: Mostly clear, minor parts obscured
- 0.5: Significant uncertainty
- Below 0.3: Image too damaged/unclear to rely on

CATEGORY must be exactly one of: Groceries, Dining, Shopping, Transport, Utilities, Health, Entertainment, Education, Other.

For transaction_date, make a best guess in YYYY-MM-DD and always include the raw date string.
If unsure about any field, set confidence accordingly and explain in notes.
    """.trimIndent()
}
