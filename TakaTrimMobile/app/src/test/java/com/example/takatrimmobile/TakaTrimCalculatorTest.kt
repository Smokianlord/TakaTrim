package com.example.takatrimmobile

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Same known cases as the desktop app's test suite. */
class TakaTrimCalculatorTest {
    private fun bd(value: String) = BigDecimal(value)

    @Test
    fun npsb_940_39_isExact() {
        val r = TakaTrimCalculator.findTransferAmount("940.39", TransferMethod.NPSB)
        assertEquals(bd("932.46"), r.transfer)
        assertEquals(bd("7.93"), r.fee)
        assertEquals(bd("940.39"), r.total)
        assertTrue(r.exact)
    }

    @Test
    fun npsb_507_37_cutDown() {
        val r = TakaTrimCalculator.findTransferAmount(
            "507.37", TransferMethod.NPSB, FeeSettings(rounding = FeeRounding.CUT_DOWN),
        )
        assertEquals(bd("503.10"), r.transfer)
        assertEquals(bd("4.27"), r.fee)
        assertTrue(r.exact)
    }

    @Test
    fun bank_6637_70_isExact() {
        val r = TakaTrimCalculator.findTransferAmount("6637.70", TransferMethod.BANK)
        assertEquals(bd("6555.75"), r.transfer)
        assertEquals(bd("81.95"), r.fee)
        assertTrue(r.exact)
    }

    @Test
    fun bank_minimumFee() {
        val r = TakaTrimCalculator.findTransferAmount("31.93", TransferMethod.BANK)
        assertEquals(bd("21.93"), r.transfer)
        assertEquals(bd("10.00"), r.fee)
        assertTrue(r.exact)
    }

    @Test
    fun bank_balanceBelowMinimumFee() {
        val r = TakaTrimCalculator.findTransferAmount("5.00", TransferMethod.BANK)
        assertEquals(bd("0.00"), r.transfer)
        assertEquals(bd("5.00"), r.remaining)
        assertFalse(r.exact)
    }

    @Test
    fun binarySearchMatchesBruteForce() {
        val balances = listOf("0.37", "9.99", "10.13", "118", "507.37", "940.39", "1234.56", "6637.70")
        for (balanceText in balances) {
            for (method in TransferMethod.entries) {
                for (rounding in FeeRounding.entries) {
                    val settings = FeeSettings(rounding = rounding)
                    val balance = bd(balanceText).setScale(2)
                    var expected = bd("0.00")
                    var cents = balance.movePointRight(2).toLong()
                    while (cents >= 0) {
                        val t = BigDecimal.valueOf(cents, 2)
                        if (t + TakaTrimCalculator.calculateFee(t, method, settings) <= balance) {
                            expected = t
                            break
                        }
                        cents--
                    }
                    val r = TakaTrimCalculator.findTransferAmount(balanceText, method, settings)
                    assertEquals("$balanceText $method $rounding", expected, r.transfer)
                }
            }
        }
    }

    @Test
    fun acceptsCommasAndCurrency() {
        assertEquals(bd("1000.50"), TakaTrimCalculator.parseMoney("1,000.50", "Balance"))
        assertEquals(bd("940.39"), TakaTrimCalculator.parseMoney("৳ 940.39", "Balance"))
        assertEquals(bd("940.39"), TakaTrimCalculator.parseMoney("940.39 Tk", "Balance"))
    }

    @Test
    fun rejectsBadInput() {
        for (bad in listOf("", "  ", "abc", "NaN", "1e30", "0", "-5")) {
            try {
                TakaTrimCalculator.findTransferAmount(bad, TransferMethod.NPSB)
                fail("Expected rejection for '$bad'")
            } catch (expected: CalculatorException) {
                // rejected as intended
            }
        }
    }

    @Test
    fun rejectsNegativeFees() {
        try {
            TakaTrimCalculator.findTransferAmount("100", TransferMethod.NPSB, FeeSettings(npsbPer1000 = "-5"))
            fail("Expected rejection")
        } catch (e: CalculatorException) {
            assertEquals("NPSB fee cannot be negative.", e.message)
        }
    }

    @Test
    fun feeRateKeepsFullPrecision() {
        val fee = TakaTrimCalculator.calculateFee(bd("1000"), TransferMethod.BANK, FeeSettings(bankPercent = "1.255"))
        assertEquals(bd("12.55"), fee)
    }
}
