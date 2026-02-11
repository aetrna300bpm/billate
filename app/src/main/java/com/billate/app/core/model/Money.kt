package com.billate.app.core.model

/**
 * Currency-agnostic monetary value.
 *
 * @param amountMinor Amount in the currency's minor unit (e.g. cents for USD, đồng for VND).
 * @param currency    ISO 4217 3-letter currency code (e.g. "VND", "USD").
 */
data class Money(
    val amountMinor: Long,
    val currency: String,
) {
    companion object {
        fun zero(currency: String) = Money(0, currency)
    }
}
