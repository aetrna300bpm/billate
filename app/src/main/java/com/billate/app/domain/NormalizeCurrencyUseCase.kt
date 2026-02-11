package com.billate.app.domain

import com.billate.app.model.BillTransaction
import javax.inject.Inject

/**
 * Applies VND normalization heuristic:
 * If sum(line_items.amount_vnd) * 1000 == total_amount_vnd, auto-correct line items.
 */
class NormalizeCurrencyUseCase @Inject constructor() {

    operator fun invoke(bill: BillTransaction): BillTransaction {
        val lineItemSum = bill.lineItems.sumOf { it.amountVnd * it.qty }

        // Already matches — no correction needed
        if (lineItemSum == bill.totalAmountVnd) return bill

        // Check x1000 heuristic
        if (lineItemSum * 1000 == bill.totalAmountVnd) {
            val correctedItems = bill.lineItems.map {
                it.copy(amountVnd = it.amountVnd * 1000)
            }
            return bill.copy(lineItems = correctedItems)
        }

        // Check if total is 1000x too small
        if (lineItemSum == bill.totalAmountVnd * 1000) {
            return bill.copy(totalAmountVnd = bill.totalAmountVnd * 1000)
        }

        // Cannot auto-correct — return as-is for manual review
        return bill
    }
}
