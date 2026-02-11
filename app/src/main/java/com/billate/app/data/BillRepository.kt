package com.billate.app.data

import android.net.Uri
import com.billate.app.model.BillProcessingOutcome
import com.billate.app.model.BillTransaction
import kotlinx.coroutines.flow.Flow

interface BillRepository {
    fun getBills(): Flow<List<BillTransaction>>
    suspend fun getBillById(billId: Long): BillTransaction?
    suspend fun processBill(imageUri: Uri): BillProcessingOutcome
    suspend fun saveBill(bill: BillTransaction): Long
    suspend fun updateBill(bill: BillTransaction)
    suspend fun deleteBill(billId: Long)
}
