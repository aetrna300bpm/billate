package com.billate.app.domain

import com.billate.app.data.BillRepository
import com.billate.app.model.BillTransaction
import javax.inject.Inject

class SaveBillUseCase @Inject constructor(
    private val repository: BillRepository,
) {
    suspend operator fun invoke(bill: BillTransaction): Long =
        repository.saveBill(bill)
}
