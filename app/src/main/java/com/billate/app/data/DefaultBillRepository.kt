package com.billate.app.data

import android.net.Uri
import com.billate.app.data.local.BillLocalDataSource
import com.billate.app.data.remote.GeminiRemoteDataSource
import com.billate.app.data.remote.ImageDataSource
import com.billate.app.domain.NormalizeCurrencyUseCase
import com.billate.app.domain.ValidateBillUseCase
import com.billate.app.model.BillProcessingOutcome
import com.billate.app.model.BillTransaction
import com.billate.app.model.Category
import com.billate.app.model.LineItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBillRepository @Inject constructor(
    private val localDataSource: BillLocalDataSource,
    private val remoteDataSource: GeminiRemoteDataSource,
    private val imageDataSource: ImageDataSource,
    private val validateBill: ValidateBillUseCase,
    private val normalizeCurrency: NormalizeCurrencyUseCase,
) : BillRepository {

    override fun getBills(): Flow<List<BillTransaction>> =
        localDataSource.getAllBills()

    override suspend fun getBillById(billId: Long): BillTransaction? =
        localDataSource.getBillById(billId)

    override suspend fun processBill(imageUri: Uri): BillProcessingOutcome {
        return try {
            val bitmap = imageDataSource.readBitmap(imageUri)
                ?: return BillProcessingOutcome.Failed("Could not read image")

            val geminiResponse = remoteDataSource.extractReceipt(bitmap)

            val category = Category.fromString(geminiResponse.category) ?: Category.Other
            val lineItems = geminiResponse.lineItems.map {
                LineItem(
                    description = it.description,
                    qty = it.qty,
                    amountVnd = it.amountVnd,
                    amountRaw = it.amountRaw,
                )
            }

            var bill = BillTransaction(
                merchantName = geminiResponse.merchantName,
                transactionDate = geminiResponse.transactionDate,
                transactionDateRaw = geminiResponse.transactionDateRaw,
                currency = geminiResponse.currency,
                totalAmountVnd = geminiResponse.totalAmountVnd,
                totalAmountRaw = geminiResponse.totalAmountRaw,
                category = category,
                lineItems = lineItems,
                notes = geminiResponse.notes,
            )

            // Attempt VND normalization correction
            bill = normalizeCurrency(bill)

            // Validate
            val validationResult = validateBill(bill)
            if (validationResult == null) {
                // Valid — auto-save
                val id = localDataSource.saveBill(bill)
                BillProcessingOutcome.AutoSaved(bill.copy(id = id))
            } else {
                // Needs review
                BillProcessingOutcome.RequiresReview(bill, validationResult)
            }
        } catch (e: Exception) {
            BillProcessingOutcome.Failed(e.message ?: "Unknown error during processing")
        }
    }

    override suspend fun saveBill(bill: BillTransaction): Long =
        localDataSource.saveBill(bill)

    override suspend fun updateBill(bill: BillTransaction) =
        localDataSource.updateBill(bill)

    override suspend fun deleteBill(billId: Long) =
        localDataSource.deleteBill(billId)
}
