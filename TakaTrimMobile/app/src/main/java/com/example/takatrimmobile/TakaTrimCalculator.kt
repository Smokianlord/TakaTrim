package com.example.takatrimmobile

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure calculation logic, kept free of Android/Compose so it can be unit tested on the JVM.
 * Mirrors the desktop app (bkash_zero_balance_desktop_app.py).
 */
enum class TransferMethod(val label: String) {
    NPSB("NPSB (bKash to Bank)"),
    BANK("Bank Transfer (bKash to Bank)"),
}

enum class FeeRounding(val label: String, val mode: RoundingMode) {
    NORMAL("Round normally", RoundingMode.HALF_UP),
    CUT_DOWN("Cut extra decimals", RoundingMode.DOWN),
}

data class FeeSettings(
    val npsbPer1000: String = "8.50",
    val bankPercent: String = "1.25",
    val minFee: String = "10.00",
    val rounding: FeeRounding = FeeRounding.NORMAL,
)

data class CalculationResult(
    val transfer: BigDecimal,
    val fee: BigDecimal,
    val total: BigDecimal,
    val remaining: BigDecimal,
    val exact: Boolean,
)

class CalculatorException(message: String) : IllegalArgumentException(message)

object TakaTrimCalculator {
    private val MAX_AMOUNT = BigDecimal("100000000")
    private val HUNDRED = BigDecimal("100")
    private val THOUSAND = BigDecimal("1000")

    /** Parses user text such as "1,000.50", "৳ 940.39" or "940 Tk". */
    fun parseNumber(text: String, fieldName: String): BigDecimal {
        var cleaned = text.trim()
        for (junk in listOf(",", "৳", "Tk", "tk", "TK", " ")) {
            cleaned = cleaned.replace(junk, "")
        }
        if (cleaned.isEmpty()) throw CalculatorException("$fieldName cannot be empty.")

        val number = cleaned.toBigDecimalOrNull()
            ?: throw CalculatorException("$fieldName is not a valid number.")
        if (number.abs() > MAX_AMOUNT) throw CalculatorException("$fieldName is too large.")
        return number
    }

    fun parseMoney(text: String, fieldName: String): BigDecimal =
        parseNumber(text, fieldName).setScale(2, RoundingMode.HALF_UP)

    private fun parseFeeSetting(text: String, fieldName: String): BigDecimal {
        val number = parseNumber(text, fieldName)
        if (number.signum() < 0) throw CalculatorException("$fieldName cannot be negative.")
        return number
    }

    private data class ParsedSettings(
        val npsbPer1000: BigDecimal,
        val bankPercent: BigDecimal,
        val minFee: BigDecimal,
        val rounding: RoundingMode,
    )

    private fun parse(settings: FeeSettings) = ParsedSettings(
        npsbPer1000 = parseFeeSetting(settings.npsbPer1000, "NPSB fee"),
        bankPercent = parseFeeSetting(settings.bankPercent, "Bank transfer fee %"),
        minFee = parseFeeSetting(settings.minFee, "Minimum fee").setScale(2, RoundingMode.HALF_UP),
        rounding = settings.rounding.mode,
    )

    private fun fee(amount: BigDecimal, method: TransferMethod, s: ParsedSettings): BigDecimal =
        when (method) {
            TransferMethod.NPSB ->
                amount.multiply(s.npsbPer1000).divide(THOUSAND).setScale(2, s.rounding)

            TransferMethod.BANK -> {
                val fee = amount.multiply(s.bankPercent).divide(HUNDRED).setScale(2, s.rounding)
                if (fee < s.minFee) s.minFee else fee
            }
        }

    fun calculateFee(amount: BigDecimal, method: TransferMethod, settings: FeeSettings = FeeSettings()): BigDecimal =
        fee(amount.setScale(2, RoundingMode.HALF_UP), method, parse(settings))

    /**
     * Highest transfer whose transfer + fee does not exceed the balance.
     * Fees are non-negative and never decrease as the transfer grows, so transfer + fee is
     * strictly increasing per paisa and a binary search gives the same answer as checking every paisa.
     */
    fun findTransferAmount(
        balanceText: String,
        method: TransferMethod,
        settings: FeeSettings = FeeSettings(),
    ): CalculationResult {
        val balance = parseMoney(balanceText, "Balance")
        if (balance.signum() <= 0) throw CalculatorException("Balance must be greater than 0.")
        val parsed = parse(settings)

        var low = 0L
        var high = balance.movePointRight(2).toLong()
        var best: Triple<BigDecimal, BigDecimal, BigDecimal>? = null

        while (low <= high) {
            val mid = (low + high) / 2L
            val transfer = BigDecimal.valueOf(mid, 2)
            val feeValue = fee(transfer, method, parsed)
            val total = transfer.add(feeValue)
            if (total <= balance) {
                best = Triple(transfer, feeValue, total)
                low = mid + 1L
            } else {
                high = mid - 1L
            }
        }

        val zero = BigDecimal("0.00")
        val (transfer, feeValue, total) = best ?: Triple(zero, zero, zero)
        return CalculationResult(
            transfer = transfer.setScale(2, RoundingMode.HALF_UP),
            fee = feeValue.setScale(2, RoundingMode.HALF_UP),
            total = total.setScale(2, RoundingMode.HALF_UP),
            remaining = balance.subtract(total).setScale(2, RoundingMode.HALF_UP),
            exact = best != null && total.compareTo(balance) == 0,
        )
    }
}
