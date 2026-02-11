package com.billate.app.domain.usecase

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.billate.app.core.model.Bill
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

            // 2. Extract receipt via Gemini
            val response = receiptExtractor.extract(bitmap)

            // 3. Save image to internal storage
            val imageFilename = "receipt_${UUID.randomUUID()}.jpg"
            contentResolver.openInputStream(imageUri)?.use { stream ->
                imageStorage.saveImage(stream, imageFilename)
            }

            // 4. Build domain Transaction
            val transaction = mapResponseToTransaction(response, imageFilename)

            // 5. Validate and save
            val validationIssue = validate(transaction)
            if (validationIssue == null) {
                val id = repository.save(transaction)
                ReceiptProcessingResult.AutoSaved(transaction.copy(id = id))
            } else {
                ReceiptProcessingResult.ReviewNeeded(transaction, validationIssue)
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

        val lineItems = response.lineItems.map { item ->
            LineItem(
                description = item.description,
                qty = item.qty,
                amount = Money(item.amount, currency),
                amountRaw = item.amountRaw,
            )
        }

        val bill = Bill(
            merchantName = response.merchantName,
            transactionDateRaw = response.transactionDateRaw,
            totalAmountRaw = response.totalAmountRaw,
            lineItems = lineItems,
            notes = response.notes,
            imageUri = imageFilename,
        )

        val timestamp = parseDate(response.transactionDate)

        return Transaction(
            timestamp = timestamp,
            amount = Money(response.totalAmount, currency),
            category = Category.fromString(response.category) ?: Category.Other,
            bill = bill,
        )
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun validate(transaction: Transaction): String? {
        if (transaction.bill?.merchantName.isNullOrBlank()) return "Merchant name is missing"
        if (transaction.amount.amountMinor <= 0) return "Total amount must be positive"
        return null
    }
}
