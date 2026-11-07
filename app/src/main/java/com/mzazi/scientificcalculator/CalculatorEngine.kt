package com.mzazi.scientificcalculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Angle interpretation used by the trigonometric functions. */
enum class AngleMode { DEG, RAD }

/** Raised for any user-visible calculation problem (domain, syntax, overflow). */
class CalculatorException(message: String) : Exception(message)

/**
 * A self-contained expression evaluator for the calculator.
 *
 * Grammar (recursive descent):
 *
 *   expression := term (('+' | '-') term)*
 *   term       := unary (('*' | '/') unary)*
 *   unary      := ('-' | '+') unary | power
 *   power      := postfix ('^' unary)?          // right associative
 *   postfix    := primary ('!' | '%')*
 *   primary    := number | constant | '(' expression ')' | function primary
 *
 * Implicit multiplication is supported, so `2π`, `3(4+1)` and `2sin(30)` all work.
 */
object CalculatorEngine {

    private const val MAX_EXPONENT = 1e308
    private const val FACTORIAL_LIMIT = 170.0

    /** Evaluates [expression] and returns the numeric result. */
    fun evaluate(
        expression: String,
        angleMode: AngleMode = AngleMode.DEG,
        ans: Double = 0.0
    ): Double {
        val tokens = tokenize(expression)
        if (tokens.isEmpty()) throw CalculatorException("Empty expression")
        val parser = Parser(tokens, angleMode, ans)
        val value = parser.parse()
        if (value.isNaN()) throw CalculatorException("Undefined result")
        if (value.isInfinite()) throw CalculatorException("Overflow")
        return value
    }

    /** Formats a value the way a hand calculator would: at most 12 significant digits. */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Error"
        var v = value
        if (abs(v) < 1e-13) v = 0.0

        val magnitude = abs(v)
        if (magnitude != 0.0 && (magnitude >= 1e12 || magnitude < 1e-9)) {
            val raw = String.format(Locale.US, "%.6e", v) // e.g. 1.234500e+15
            val parts = raw.split("e")
            val mantissa = BigDecimal(parts[0]).stripTrailingZeros().toPlainString()
            val exponent = parts[1].toInt()
            return "${mantissa}e$exponent"
        }

        val rounded = BigDecimal(v)
            .round(MathContext(12, RoundingMode.HALF_UP))
            .stripTrailingZeros()
        var text = rounded.toPlainString()
        if (text.contains('.')) text = text.trimEnd('0').trimEnd('.')
        if (text == "-0") text = "0"
        return text
    }

    // ---------------------------------------------------------------- tokens

    private sealed class Token {
        data class Num(val value: Double) : Token()
        data class Op(val symbol: Char) : Token()
        data class Func(val name: String) : Token()
        object LParen : Token()
        object RParen : Token()
        object Factorial : Token()
        object Percent : Token()
        object Ans : Token()
    }

    private val functionNames = setOf(
        "sin", "cos", "tan", "asin", "acos", "atan",
        "ln", "log", "sqrt", "cbrt", "abs", "exp"
    )

    private fun tokenize(raw: String): List<Token> {
        val s = raw.replace(" ", "").replace("\u00A0", "")
        val tokens = ArrayList<Token>()
        var i = 0

        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() || c == '.' -> {
                    val start = i
                    var seenDot = false
                    while (i < s.length && (s[i].isDigit() || (s[i] == '.' && !seenDot))) {
                        if (s[i] == '.') seenDot = true
                        i++
                    }
                    val text = s.substring(start, i)
                    if (text == ".") throw CalculatorException("Syntax error")
                    tokens.add(Token.Num(text.toDoubleOrNull() ?: throw CalculatorException("Syntax error")))
                }

                c == 'π' -> {
                    tokens.add(Token.Num(PI))
                    i++
                }

                c.isLetter() -> {
                    val start = i
                    while (i < s.length && s[i].isLetter()) i++
                    val name = s.substring(start, i).lowercase(Locale.US)
                    when (name) {
                        "pi" -> tokens.add(Token.Num(PI))
                        "e" -> tokens.add(Token.Num(Math.E))
                        "ans" -> tokens.add(Token.Ans)
                        in functionNames -> tokens.add(Token.Func(name))
                        else -> throw CalculatorException("Unknown symbol")
                    }
                }

                c == '+' || c == '-' || c == '*' || c == '/' || c == '^' -> {
                    tokens.add(Token.Op(c))
                    i++
                }

                c == '(' -> {
                    tokens.add(Token.LParen)
                    i++
                }

                c == ')' -> {
                    tokens.add(Token.RParen)
                    i++
                }

                c == '!' -> {
                    tokens.add(Token.Factorial)
                    i++
                }

                c == '%' -> {
                    tokens.add(Token.Percent)
                    i++
                }

                c == '×' -> {
                    tokens.add(Token.Op('*'))
                    i++
                }

                c == '÷' || c == ':' -> {
                    tokens.add(Token.Op('/'))
                    i++
                }

                c == '−' || c == '–' || c == '—' -> {
                    tokens.add(Token.Op('-'))
                    i++
                }

                c == '√' -> {
                    tokens.add(Token.Func("sqrt"))
                    i++
                }

                else -> throw CalculatorException("Syntax error")
            }
        }
        return withImplicitMultiplication(tokens)
    }

    /** Inserts a `*` wherever two values sit side by side: 2π, 3(4), 2sin(30). */
    private fun withImplicitMultiplication(tokens: List<Token>): List<Token> {
        if (tokens.isEmpty()) return tokens
        val out = ArrayList<Token>(tokens.size + 8)
        for (token in tokens) {
            val previous = out.lastOrNull()
            val previousIsValue = previous is Token.Num ||
                previous is Token.RParen ||
                previous is Token.Factorial ||
                previous is Token.Percent ||
                previous is Token.Ans
            val currentStartsValue = token is Token.Num ||
                token is Token.LParen ||
                token is Token.Func ||
                token is Token.Ans
            if (previousIsValue && currentStartsValue) out.add(Token.Op('*'))
            out.add(token)
        }
        return out
    }

    // ---------------------------------------------------------------- parser

    private class Parser(
        private val tokens: List<Token>,
        private val angleMode: AngleMode,
        private val ans: Double
    ) {
        private var position = 0

        fun parse(): Double {
            val value = expression()
            if (position != tokens.size) throw CalculatorException("Syntax error")
            return value
        }

        private fun peek(): Token? = if (position < tokens.size) tokens[position] else null

        private fun expression(): Double {
            var value = term()
            while (true) {
                val token = peek()
                if (token is Token.Op && (token.symbol == '+' || token.symbol == '-')) {
                    position++
                    val rhs = term()
                    value = if (token.symbol == '+') value + rhs else value - rhs
                } else {
                    return value
                }
            }
        }

        private fun term(): Double {
            var value = unary()
            while (true) {
                val token = peek()
                if (token is Token.Op && (token.symbol == '*' || token.symbol == '/')) {
                    position++
                    val rhs = unary()
                    if (token.symbol == '*') {
                        value *= rhs
                    } else {
                        if (rhs == 0.0) throw CalculatorException("Cannot divide by zero")
                        value /= rhs
                    }
                } else {
                    return value
                }
            }
        }

        private fun unary(): Double {
            val token = peek()
            if (token is Token.Op && (token.symbol == '-' || token.symbol == '+')) {
                position++
                val value = unary()
                return if (token.symbol == '-') -value else value
            }
            return power()
        }

        private fun power(): Double {
            val base = postfix()
            val token = peek()
            if (token is Token.Op && token.symbol == '^') {
                position++
                val exponent = unary()
                if (abs(exponent) > MAX_EXPONENT) throw CalculatorException("Overflow")
                val result = base.pow(exponent)
                if (result.isNaN()) throw CalculatorException("Undefined result")
                return result
            }
            return base
        }

        private fun postfix(): Double {
            var value = primary()
            while (true) {
                when (peek()) {
                    is Token.Factorial -> {
                        position++
                        value = factorial(value)
                    }
                    is Token.Percent -> {
                        position++
                        value /= 100.0
                    }
                    else -> return value
                }
            }
        }

        private fun primary(): Double {
            val token = peek() ?: throw CalculatorException("Incomplete expression")
            return when (token) {
                is Token.Num -> {
                    position++
                    token.value
                }
                is Token.Ans -> {
                    position++
                    ans
                }
                is Token.LParen -> {
                    position++
                    val value = expression()
                    if (peek() !is Token.RParen) throw CalculatorException("Missing )")
                    position++
                    value
                }
                is Token.Func -> {
                    position++
                    val argument = if (peek() is Token.LParen) {
                        position++
                        val value = expression()
                        if (peek() !is Token.RParen) throw CalculatorException("Missing )")
                        position++
                        value
                    } else {
                        unary()
                    }
                    apply(token.name, argument)
                }
                else -> throw CalculatorException("Syntax error")
            }
        }

        // ------------------------------------------------------- functions

        private fun toRadians(v: Double) = if (angleMode == AngleMode.DEG) Math.toRadians(v) else v
        private fun fromRadians(v: Double) = if (angleMode == AngleMode.DEG) Math.toDegrees(v) else v

        private fun apply(name: String, x: Double): Double = when (name) {
            "sin" -> sin(toRadians(x))
            "cos" -> cos(toRadians(x))
            "tan" -> {
                if (angleMode == AngleMode.DEG && abs(x % 180.0) == 90.0) {
                    throw CalculatorException("Undefined result")
                }
                tan(toRadians(x))
            }
            "asin" -> {
                if (x < -1.0 || x > 1.0) throw CalculatorException("Domain error")
                fromRadians(asin(x))
            }
            "acos" -> {
                if (x < -1.0 || x > 1.0) throw CalculatorException("Domain error")
                fromRadians(acos(x))
            }
            "atan" -> fromRadians(atan(x))
            "ln" -> {
                if (x <= 0.0) throw CalculatorException("Domain error")
                ln(x)
            }
            "log" -> {
                if (x <= 0.0) throw CalculatorException("Domain error")
                log10(x)
            }
            "sqrt" -> {
                if (x < 0.0) throw CalculatorException("Domain error")
                sqrt(x)
            }
            "cbrt" -> Math.cbrt(x)
            "abs" -> abs(x)
            "exp" -> exp(x)
            else -> throw CalculatorException("Unknown symbol")
        }
    }

    // ------------------------------------------------------------- factorials

    private val lanczos = doubleArrayOf(
        0.99999999999980993,
        676.5203681218851,
        -1259.1392167224028,
        771.32342877765313,
        -176.61502916214059,
        12.507343278686905,
        -0.13857109526572012,
        9.9843695780195716e-6,
        1.5056327351493116e-7
    )

    private fun factorial(x: Double): Double {
        if (x < 0.0 && x == Math.floor(x)) throw CalculatorException("Undefined result")
        if (x > FACTORIAL_LIMIT) throw CalculatorException("Overflow")
        return gamma(x + 1.0)
    }

    /** Lanczos approximation, so non-integer factorials (0.5!) work too. */
    private fun gamma(input: Double): Double {
        if (input < 0.5) {
            // Reflection formula.
            return PI / (sin(PI * input) * gamma(1.0 - input))
        }
        val z = input - 1.0
        var x = lanczos[0]
        for (i in 1 until lanczos.size) x += lanczos[i] / (z + i)
        val t = z + 7.5
        return sqrt(2 * PI) * t.pow(z + 0.5) * exp(-t) * x
    }
}
