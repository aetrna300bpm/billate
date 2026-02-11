package com.billate.app.domain

import android.net.Uri
import com.billate.app.data.BillRepository
import com.billate.app.model.BillProcessingOutcome
import javax.inject.Inject

class ProcessBillUseCase @Inject constructor(
    private val repository: BillRepository,
) {
    suspend operator fun invoke(imageUri: Uri): BillProcessingOutcome =
        repository.processBill(imageUri)
}
