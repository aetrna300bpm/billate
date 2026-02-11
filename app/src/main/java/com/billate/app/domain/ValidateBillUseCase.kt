package com.billate.app.domain

import com.billate.app.model.BillTransaction
import com.billate.app.model.Category
import javax.inject.Inject

/**
 * Returns null if the bill is valid, or a reason string if it requires review.
 */
class ValidateBillUseCase @Inject constructor() {

    operator fun invoke(bill: BillTransaction): String? {
        // 1. Required fields
        if (bill.merchantName.isBlank()) return "Merchant name is missing"
        if (bill.transactionDate.isBlank()) return "Transaction date is missing"
        if (bill.totalAmountVnd <= 0) return "Total amount must be positive"

        // 2. Category must be in the fixed list
        if (Category.fromString(bill.category.displayName) == null) {
            return "Invalid category: ${bill.category.displayName}"
        }

        // 3. Line item sum must equal total
        val lineItemSum = bill.lineItems.sumOf { it.amountVnd * it.qty }
        if (lineItemSum != bill.totalAmountVnd) {
            return "Line items sum ($lineItemSum) does not match total (${bill.totalAmountVnd})"
        }

        return null
    }
}
