package com.mzazi.scientificcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalculatorEngineTest {

    private fun eval(expression: String, mode: AngleMode = AngleMode.DEG): Double =
        CalculatorEngine.evaluate(expression, mode)

    // ------------------------------------------------------------- arithmetic

    @Test
    fun respectsOperatorPrecedence() {
        assertEquals(14.0, eval("2+3*4"), 1e-9)
        assertEquals(20.0, eval("(2+3)*4"), 1e-9)
        assertEquals(1.0, eval("2+3-4"), 1e-9)
    }

    @Test
    fun handlesUnaryMinus() {
        assertEquals(-5.0, eval("-5"), 1e-9)
        assertEquals(-4.0, eval("-2^2"), 1e-9)
        assertEquals(-8.0, eval("(-2)^3"), 1e-9)
        assertEquals(0.125, eval("2^-3"), 1e-9)
    }

    @Test
    fun powerIsRightAssociative() {
        assertEquals(512.0, eval("2^3^2"), 1e-9)
    }

    @Test
    fun supportsImplicitMultiplication() {
        assertEquals(2 * Math.PI, eval("2π"), 1e-9)
        assertEquals(12.0, eval("3(4)"), 1e-9)
        assertEquals(1.0, eval("2sin(30)"), 1e-9)
    }

    @Test
    fun rejectsDivisionByZero() {
        val error = assertThrows(CalculatorException::class.java) { eval("1/0") }
        assertEquals("Cannot divide by zero", error.message)
    }

    // -------------------------------------------------------------- functions

    @Test
    fun trigonometricFunctionsUseDegrees() {
        assertEquals(0.5, eval("sin(30)"), 1e-9)
        assertEquals(0.5, eval("cos(60)"), 1e-9)
        assertEquals(1.0, eval("tan(45)"), 1e-9)
        assertEquals(30.0, eval("asin(0.5)"), 1e-9)
    }

    @Test
    fun trigonometricFunctionsUseRadians() {
        assertEquals(1.0, eval("sin(π/2)", AngleMode.RAD), 1e-9)
        assertEquals(0.0, eval("cos(π/2)", AngleMode.RAD), 1e-9)
    }

    @Test
    fun tangentOfNinetyDegreesIsUndefined() {
        assertThrows(CalculatorException::class.java) { eval("tan(90)") }
    }

    @Test
    fun logarithmsAndRoots() {
        assertEquals(2.0, eval("log(100)"), 1e-9)
        assertEquals(1.0, eval("ln(e)"), 1e-9)
        assertEquals(4.0, eval("sqrt(16)"), 1e-9)
        assertEquals(3.0, eval("√(9)"), 1e-9)
        assertEquals(2.0, eval("cbrt(8)"), 1e-9)
    }

    @Test
    fun domainErrorsAreReported() {
        assertThrows(CalculatorException::class.java) { eval("sqrt(-1)") }
        assertThrows(CalculatorException::class.java) { eval("ln(0)") }
        assertThrows(CalculatorException::class.java) { eval("log(-5)") }
        assertThrows(CalculatorException::class.java) { eval("asin(2)") }
    }

    // ------------------------------------------------------ factorial, percent

    @Test
    fun factorialWorksForIntegers() {
        assertEquals(120.0, eval("5!"), 1e-6)
        assertEquals(1.0, eval("0!"), 1e-6)
    }

    @Test
    fun factorialWorksForFractions() {
        assertEquals(0.8862269254527580, eval("0.5!"), 1e-9)
    }

    @Test
    fun percentDividesByHundred() {
        assertEquals(1.0, eval("100%"), 1e-9)
        assertEquals(25.0, eval("100*25%"), 1e-9)
    }

    // ------------------------------------------------------------------ ans

    @Test
    fun answerTokenIsSubstituted() {
        assertEquals(47.0, CalculatorEngine.evaluate("ans+5", AngleMode.DEG, 42.0), 1e-9)
    }

    // --------------------------------------------------------------- parsing

    @Test
    fun rejectsMalformedInput() {
        assertThrows(CalculatorException::class.java) { eval("2+") }
        assertThrows(CalculatorException::class.java) { eval("(2+3") }
        assertThrows(CalculatorException::class.java) { eval("2++") }
        assertThrows(CalculatorException::class.java) { eval("") }
        assertThrows(CalculatorException::class.java) { eval("hello") }
    }

    // -------------------------------------------------------------- formatting

    @Test
    fun formatsWithoutFloatingPointNoise() {
        assertEquals("0.3", CalculatorEngine.format(0.1 + 0.2))
        assertEquals("0.333333333333", CalculatorEngine.format(1.0 / 3.0))
        assertEquals("4", CalculatorEngine.format(4.0))
        assertEquals("0", CalculatorEngine.format(-0.0))
        assertEquals("-7.5", CalculatorEngine.format(-7.5))
    }

    @Test
    fun formatsLargeNumbersInScientificNotation() {
        assertEquals("1e15", CalculatorEngine.format(1e15))
        assertEquals("1.5e-10", CalculatorEngine.format(1.5e-10))
    }

    @Test
    fun historyFormatIsParseable() {
        assertEquals("(1.5*10^-10)", HistoryFormat.toExpression("1.5e-10"))
        assertEquals("42", HistoryFormat.toExpression("42"))
    }
}
