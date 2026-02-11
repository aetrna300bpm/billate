package com.billate.app.model

sealed class BillProcessingOutcome {
    /** Bill was valid and auto-saved. */
    data class AutoSaved(val bill: BillTransaction) : BillProcessingOutcome()

    /** Bill requires manual review before saving. */
    data class RequiresReview(val bill: BillTransaction, val reason: String) : BillProcessingOutcome()

    /** Processing failed entirely. */
    data class Failed(val message: String) : BillProcessingOutcome()
}
