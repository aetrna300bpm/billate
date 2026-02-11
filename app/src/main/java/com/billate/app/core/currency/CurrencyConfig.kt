package com.billate.app.core.currency

import java.util.Locale

/**
 * Currency-specific configuration for formatting and rounding.
 *
 * Each supported currency defines how amounts are displayed and how
 * minor-unit values map to human-readable strings.
 *
 * @param code           ISO 4217 currency code.
 * @param symbol         Short display symbol (e.g. "₫", "$").
 * @param decimalPlaces  Number of decimal places for display (VND=0, USD=2).
 * @param minorPerMajor  How many minor units per 1 major unit (VND=1, USD=100).
 * @param displayLocale  Locale hint for number formatting.
 */
data class CurrencyConfig(
    val code: String,
    val symbol: String,
    val decimalPlaces: Int,
    val minorPerMajor: Int,
    val displayLocale: Locale,
) {
    companion object {
        private val configs = mapOf(
            "VND" to CurrencyConfig(
                code = "VND",
                symbol = "₫",
                decimalPlaces = 0,
                minorPerMajor = 1, // 1 VND = 1 minor unit (đồng)
                displayLocale = Locale("vi", "VN"),
            ),
            "USD" to CurrencyConfig(
                code = "USD",
                symbol = "$",
                decimalPlaces = 2,
                minorPerMajor = 100, // 1 USD = 100 cents
                displayLocale = Locale.US,
            ),
            "EUR" to CurrencyConfig(
                code = "EUR",
                symbol = "€",
                decimalPlaces = 2,
                minorPerMajor = 100,
                displayLocale = Locale.GERMANY,
            ),
            "JPY" to CurrencyConfig(
                code = "JPY",
                symbol = "¥",
                decimalPlaces = 0,
                minorPerMajor = 1,
                displayLocale = Locale.JAPAN,
            ),
        )

        fun forCode(code: String): CurrencyConfig =
            configs[code.uppercase()] ?: CurrencyConfig(
                code = code.uppercase(),
                symbol = code.uppercase(),
                decimalPlaces = 2,
                minorPerMajor = 100,
                displayLocale = Locale.US,
            )

        val supportedCodes: List<String> = configs.keys.toList()
    }
}
