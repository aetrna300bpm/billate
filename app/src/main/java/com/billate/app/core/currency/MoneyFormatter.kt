package com.billate.app.core.currency

import com.billate.app.core.model.Money
import java.text.NumberFormat

/**
 * Formats [Money] values for display, respecting currency-specific rules.
 */
object MoneyFormatter {

    /**
     * Format a [Money] value into a human-readable string.
     *
     * Examples:
     * - VND 150000 → "150.000 ₫"
     * - USD 1299   → "$12.99"
     */
    fun format(money: Money): String {
        val config = CurrencyConfig.forCode(money.currency)
        val formatter = NumberFormat.getNumberInstance(config.displayLocale).apply {
            minimumFractionDigits = config.decimalPlaces
            maximumFractionDigits = config.decimalPlaces
        }
        val displayValue = if (config.minorPerMajor > 1) {
            money.amountMinor.toDouble() / config.minorPerMajor
        } else {
            money.amountMinor.toDouble()
        }
        val formatted = formatter.format(displayValue)
        return "$formatted ${config.symbol}"
    }

    /**
     * Parse a numeric string into minor units for the given currency.
     * Assumes the input is in major units (e.g. "12.99" for USD → 1299 cents).
     */
    fun parseToMinor(value: String, currencyCode: String): Long? {
        val config = CurrencyConfig.forCode(currencyCode)
        val cleaned = value.replace(Regex("[^\\d.]"), "")
        val number = cleaned.toDoubleOrNull() ?: return null
        return (number * config.minorPerMajor).toLong()
    }
}
