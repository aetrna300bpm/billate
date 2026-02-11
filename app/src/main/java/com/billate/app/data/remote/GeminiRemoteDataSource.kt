package com.billate.app.data.remote

import android.graphics.Bitmap
import com.billate.app.data.local.ApiKeyManager
import com.billate.app.model.GeminiReceiptResponse
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRemoteDataSource @Inject constructor(
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

    suspend fun extractReceipt(bitmap: Bitmap): GeminiReceiptResponse {
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
    """.trimIndent()
}
