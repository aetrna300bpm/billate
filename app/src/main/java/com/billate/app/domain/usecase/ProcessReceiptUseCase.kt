package com.billate.app.domain.usecase

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.billate.app.core.model.Category
import com.billate.app.core.model.LineItem
import com.billate.app.core.model.Money
import com.billate.app.core.model.Transaction
import com.billate.app.data.remote.GeminiReceiptResponse
import com.billate.app.data.remote.ReceiptExtractor
import com.billate.app.data.repository.ReceiptImageStorage
import com.billate.app.data.repository.TransactionRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Outcome of processing a receipt image.
 */
sealed class ReceiptProcessingResult {
    /** Transaction was valid and auto-saved. */
    data class AutoSaved(val transaction: Transaction) : ReceiptProcessingResult()
    /** Transaction requires manual review before saving. */
    data class ReviewNeeded(val transaction: Transaction, val reason: String) : ReceiptProcessingResult()
    /** Processing failed entirely. */
    data class Failed(val message: String) : ReceiptProcessingResult()
}

/**
 * End-to-end use case: image → Gemini extraction → save image → create Transaction.
 *
 * Gemini auto-detects whether the image is a receipt or a wire transfer.
 * - Receipts follow the existing validate-then-save flow.
 * - Wire transfers ALWAYS go to review mode so the user can set a category.
 */
class ProcessReceiptUseCase @Inject constructor(
    private val receiptExtractor: ReceiptExtractor,
    private val repository: TransactionRepository,
    private val imageStorage: ReceiptImageStorage,
    private val contentResolver: ContentResolver,
) {
    suspend operator fun invoke(imageUri: Uri): ReceiptProcessingResult {
        return try {
            // 1. Read bitmap
            val bitmap = readBitmap(imageUri)
                ?: return ReceiptProcessingResult.Failed("Could not read image")

            // 2. Extract via Gemini (auto-detects receipt vs wire transfer)
            val response = receiptExtractor.extract(bitmap)

            // 3. Save image to internal storage
            val prefix = if (response.type == "wire_transfer") "transfer" else "receipt"
            val imageFilename = "${prefix}_${UUID.randomUUID()}.jpg"
            contentResolver.openInputStream(imageUri)?.use { stream ->
                imageStorage.saveImage(stream, imageFilename)
            }

            // 4. Build domain Transaction (receipt or wire transfer)
            val transaction = mapResponseToTransaction(response, imageFilename)

            // 5. Validate and save
            when (transaction) {
                is Transaction.WireTransfer -> {
                    // Wire transfers always go to review so user can pick a category
                    ReceiptProcessingResult.ReviewNeeded(transaction, "Wire transfer detected — please verify details")
                }
                is Transaction.Receipt -> {
                    val issue = validateReceipt(transaction)
                    if (issue == null) {
                        val id = repository.save(transaction)
                        ReceiptProcessingResult.AutoSaved(transaction.copy(id = id))
                    } else {
                        ReceiptProcessingResult.ReviewNeeded(transaction, issue)
                    }
                }
                is Transaction.Manual -> {
                    // Should never happen from OCR, but handle gracefully
                    val id = repository.save(transaction)
                    ReceiptProcessingResult.AutoSaved(transaction.copy(id = id))
                }
            }
        } catch (e: Exception) {
            ReceiptProcessingResult.Failed(e.message ?: "Unknown error during processing")
        }
    }

    private fun readBitmap(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun mapResponseToTransaction(
        response: GeminiReceiptResponse,
        imageFilename: String,
    ): Transaction {
        val currency = response.currency.ifBlank { "VND" }
        val amount = Money(response.finalTotal, currency)
        val timestamp = parseDate(response.transactionDate)

        return when (response.type) {
            "wire_transfer" -> {
                val recipient = response.recipientName ?: response.merchantName
                Transaction.WireTransfer(
                    timestamp = timestamp,
                    amount = amount,
                    category = Category.Other,
                    name = recipient,
                    recipientName = recipient,
                    note = buildString {
                        response.recipientBank?.let { appendLine("Via: $it") }
                        response.transactionReference?.let { appendLine("Ref: $it") }
                        if (response.notes.isNotBlank()) append(response.notes)
                    }.trim(),
                    imageUri = imageFilename,
                    extractionConfidence = response.confidence,
                )
            }
            else -> {
                val lineItems = response.lineItems.map { item ->
                    LineItem(
                        description = item.description,
                        qty = item.qty,
                        amount = Money(item.amount, currency),
                        amountRaw = item.amountRaw,
                    )
                }
                val adj = response.adjustments
                val serviceCharge = adj?.serviceCharge?.takeIf { it.amount != 0L }
                    ?.let { Money(it.amount, currency) }
                val discount = adj?.discount?.takeIf { it.amount != 0L }
                    ?.let { Money(it.amount, currency) }
                val tax = adj?.tax?.takeIf { it.amount != 0L }
                    ?.let { Money(it.amount, currency) }

                Transaction.Receipt(
                    timestamp = timestamp,
                    amount = amount,
                    category = Category.fromString(response.category) ?: Category.Other,
                    name = response.merchantName,
                    note = response.notes,
                    merchantNameRaw = response.merchantName,
                    transactionDateRaw = response.transactionDateRaw,
                    totalAmountRaw = response.totalAmountRaw,
                    lineItems = lineItems,
                    serviceCharge = serviceCharge,
                    discount = discount,
                    tax = tax,
                    imageUri = imageFilename,
                    extractionConfidence = response.confidence,
                )
            }
        }
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun validateReceipt(transaction: Transaction.Receipt): String? {
        if (transaction.name.isBlank()) return "Merchant name is missing"
        if (transaction.amount.amountMinor <= 0) return "Total amount must be positive"
        if (transaction.extractionConfidence < 0.3f) {
            return "Receipt unclear (${(transaction.extractionConfidence * 100).toInt()}% confident). Please verify."
        }
        return null
    }
}
