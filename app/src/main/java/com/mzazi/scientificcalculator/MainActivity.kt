package com.mzazi.scientificcalculator

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvExpression: TextView
    private lateinit var tvResult: TextView
    private lateinit var btnAngle: TextView
    private lateinit var tvMemory: TextView
    private lateinit var keypad: GridLayout

    private lateinit var store: CalculatorStore

    /** Expression as the engine understands it (ASCII operators). */
    private var expression: String = ""

    /** True right after "=" so the next digit starts a fresh calculation. */
    private var evaluated = false

    private var inverse = false

    private var angleMode: AngleMode = AngleMode.DEG

    private var memory: Double = 0.0
    private var ans: Double = 0.0

    private val vibrator: Vibrator? by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = CalculatorStore(this)
        memory = store.memory
        ans = store.ans
        angleMode = store.angleMode
        expression = savedInstanceState?.getString(STATE_EXPRESSION) ?: ""
        evaluated = savedInstanceState?.getBoolean(STATE_EVALUATED) ?: false

        tvExpression = findViewById(R.id.tvExpression)
        tvResult = findViewById(R.id.tvResult)
        btnAngle = findViewById(R.id.btnAngle)
        tvMemory = findViewById(R.id.tvMemory)
        keypad = findViewById(R.id.keypad)

        bindKeys()
        findViewById<View>(R.id.btnHistory).setOnClickListener { showHistory() }
        btnAngle.setOnClickListener { toggleAngleMode() }

        findViewById<View>(R.id.btnMc).setOnClickListener { onMemory("mc") }
        findViewById<View>(R.id.btnMr).setOnClickListener { onMemory("mr") }
        findViewById<View>(R.id.btnMPlus).setOnClickListener { onMemory("m+") }
        findViewById<View>(R.id.btnMMinus).setOnClickListener { onMemory("m-") }

        renderAngleMode()
        renderMemoryIndicator()
        updateDisplay(showPreview = true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_EXPRESSION, expression)
        outState.putBoolean(STATE_EVALUATED, evaluated)
    }

    override fun onPause() {
        super.onPause()
        store.memory = memory
        store.ans = ans
        store.angleMode = angleMode
    }

    // ------------------------------------------------------------- key wiring

    private fun bindKeys() {
        for (index in 0 until keypad.childCount) {
            val key = keypad.getChildAt(index)
            val action = key.tag?.toString() ?: continue
            key.setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                buzz()
                press(key, action)
            }
        }
    }

    private fun press(key: View, action: String) {
        key.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).withEndAction {
            key.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
        }.start()

        when (action) {
            "ac" -> {
                expression = ""
                evaluated = false
                updateDisplay(showPreview = false)
            }
            "back" -> {
                if (evaluated) {
                    expression = ""
                    evaluated = false
                } else {
                    deleteToken()
                }
                updateDisplay(showPreview = true)
            }
            "eq" -> evaluate()
            "inv" -> toggleInverse()
            "ans" -> insert(chainable(ans), isNumber = true)
            "pi" -> insert("π", isNumber = true)
            "e" -> insert("e", isNumber = true)
            "sin" -> insertFunction(if (inverse) "asin(" else "sin(")
            "cos" -> insertFunction(if (inverse) "acos(" else "cos(")
            "tan" -> insertFunction(if (inverse) "atan(" else "tan(")
            "ln" -> insertFunction(if (inverse) "exp(" else "ln(")
            "log" -> insertFunction(if (inverse) "10^" else "log(")
            "sqrt" -> insertFunction(if (inverse) "cbrt(" else "sqrt(")
            "sqr" -> insert("^2")
            "pow" -> insert("^")
            "fact" -> insert("!")
            "pct" -> insert("%")
            "add" -> insert("+")
            "sub" -> insert("-")
            "mul" -> insert("×", operator = true)
            "div" -> insert("÷", operator = true)
            "lparen" -> insert("(")
            "rparen" -> insert(")", operator = true)
            else -> insert(action, isNumber = true)
        }
    }

    // ---------------------------------------------------------------- editing

    private fun insert(text: String, isNumber: Boolean = false, operator: Boolean = false) {
        val startsFresh = evaluated && (isNumber || text == "(")
        if (startsFresh) expression = ""
        evaluated = false

        if (operator && expression.isEmpty()) return
        if (text == "." ) {
            // Avoid "1.2.3" and add a leading zero for ".5".
            val trailing = expression.takeLastWhile { it.isDigit() || it == '.' }
            if (trailing.contains('.')) return
            if (expression.isEmpty() || !expression.last().isDigit()) {
                expression += "0"
            }
        }
        expression += text
        updateDisplay(showPreview = true)
    }

    private fun insertFunction(text: String) {
        if (evaluated) {
            expression = ""
            evaluated = false
        }
        // Implicit multiplication when a value is already there: 2sin(30).
        if (expression.isNotEmpty()) {
            val last = expression.last()
            if (last.isDigit() || last == ')' || last == 'π' || last == 'e' || last == '!') {
                expression += "×"
            }
        }
        expression += text
        if (inverse) setInverse(false)
        updateDisplay(showPreview = true)
    }

    private val multiCharacterTokens = listOf(
        "asin(", "acos(", "atan(", "sqrt(", "cbrt(", "sin(", "cos(", "tan(",
        "ln(", "log(", "abs(", "exp(", "ans"
    )

    private fun deleteToken() {
        if (expression.isEmpty()) return
        val token = multiCharacterTokens.firstOrNull { expression.endsWith(it) }
        expression = if (token != null) expression.dropLast(token.length) else expression.dropLast(1)
    }

    // ------------------------------------------------------------- evaluation

    private fun evaluate() {
        if (expression.isBlank()) return
        try {
            val value = CalculatorEngine.evaluate(expression, angleMode, ans)
            ans = value
            store.ans = value
            store.addHistory(pretty(expression), CalculatorEngine.format(value))
            expression = chainable(value)
            evaluated = true
            updateDisplay(showPreview = false)
            tvResult.announceForAccessibility(getString(R.string.result_is, CalculatorEngine.format(value)))
        } catch (e: CalculatorException) {
            showError(e.message ?: getString(R.string.error))
        }
    }

    /** Current numeric value of the entry field, or the last answer. */
    private fun currentValue(): Double {
        if (expression.isBlank()) return ans
        return try {
            CalculatorEngine.evaluate(expression, angleMode, ans)
        } catch (e: CalculatorException) {
            ans
        }
    }

    private fun showError(message: String) {
        tvResult.text = message
        tvResult.setTextColor(ContextCompat.getColor(this, R.color.danger))
        tvResult.animate().translationX(10f).setDuration(50).withEndAction {
            tvResult.animate().translationX(-10f).setDuration(50).withEndAction {
                tvResult.animate().translationX(0f).setDuration(60).start()
            }.start()
        }.start()
    }

    // ---------------------------------------------------------------- display

    private fun updateDisplay(showPreview: Boolean) {
        if (angleMode == AngleMode.DEG) btnAngle.text = getString(R.string.deg) else btnAngle.text = getString(R.string.rad)

        val prettyExpression = pretty(expression)
        tvExpression.text = when {
            prettyExpression.isEmpty() -> ""
            else -> prettyExpression
        }

        if (expression.isBlank()) {
            tvResult.text = "0"
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            return
        }

        if (!showPreview) {
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            return
        }

        // Live preview of whatever is currently typed.
        try {
            val value = CalculatorEngine.evaluate(expression, angleMode, ans)
            tvResult.text = CalculatorEngine.format(value)
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        } catch (e: CalculatorException) {
            // Incomplete input is normal while typing; dim the stale result.
            if (tvResult.text.isNullOrEmpty()) tvResult.text = "0"
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        }
    }

    /** Engine text → display text (√, ×, ÷, π, superscripts). */
    private fun pretty(raw: String): String {
        var text = raw
        text = text.replace("asin(", "sin⁻¹(")
        text = text.replace("acos(", "cos⁻¹(")
        text = text.replace("atan(", "tan⁻¹(")
        text = text.replace("sqrt(", "√(")
        text = text.replace("cbrt(", "∛(")
        text = text.replace("pi", "π")
        text = text.replace("*", "×")
        text = text.replace("/", "÷")
        text = text.replace("-", "−")
        return text
    }

    /** Result text that can safely be chained into a new expression. */
    private fun chainable(value: Double): String {
        val text = CalculatorEngine.format(value)
        if (text.contains('e')) {
            val parts = text.split("e")
            return "(${parts[0]}*10^${parts[1]})"
        }
        return text
    }

    // ------------------------------------------------------------------ modes

    private fun toggleAngleMode() {
        angleMode = if (angleMode == AngleMode.DEG) AngleMode.RAD else AngleMode.DEG
        store.angleMode = angleMode
        renderAngleMode()
        updateDisplay(showPreview = true)
    }

    private fun renderAngleMode() {
        btnAngle.text = if (angleMode == AngleMode.DEG) getString(R.string.deg) else getString(R.string.rad)
    }

    private fun toggleInverse() {
        setInverse(!inverse)
    }

    private fun setInverse(on: Boolean) {
        inverse = on
        setKeyLabel(R.id.keySin, if (on) "sin⁻¹" else "sin")
        setKeyLabel(R.id.keyCos, if (on) "cos⁻¹" else "cos")
        setKeyLabel(R.id.keyTan, if (on) "tan⁻¹" else "tan")
        setKeyLabel(R.id.keyLn, if (on) "eˣ" else "ln")
        setKeyLabel(R.id.keyLog, if (on) "10ˣ" else "log")
        setKeyLabel(R.id.keySqrt, if (on) "∛" else "√")
        findViewById<TextView>(R.id.keyInv).setTextColor(
            ContextCompat.getColor(this, if (on) R.color.accent else R.color.accent_soft)
        )
    }

    private fun setKeyLabel(id: Int, label: String) {
        findViewById<TextView>(id).text = label
    }

    private fun renderMemoryIndicator() {
        tvMemory.visibility = if (memory == 0.0) View.GONE else View.VISIBLE
        tvMemory.text = getString(R.string.memory_value, CalculatorEngine.format(memory))
    }

    private fun onMemory(action: String) {
        when (action) {
            "mc" -> memory = 0.0
            "mr" -> {
                insert(chainable(memory), isNumber = true)
                return
            }
            "m+" -> memory += currentValue()
            "m-" -> memory -= currentValue()
        }
        store.memory = memory
        renderMemoryIndicator()
    }

    // ---------------------------------------------------------------- history

    private fun showHistory() {
        val entries = store.loadHistory()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        var dialog: AlertDialog? = null

        if (entries.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.history_empty)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_muted))
                textSize = 15f
                setPadding(0, dp(12), 0, dp(12))
            })
        } else {
            entries.forEach { entry ->
                container.addView(makeHistoryRow(entry) { dialog?.dismiss() })
            }
        }

        val scroll = ScrollView(this).apply { addView(container) }

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.history))
            .setView(scroll)
            .setPositiveButton(getString(R.string.close), null)

        if (entries.isNotEmpty()) {
            builder.setNeutralButton(getString(R.string.clear)) { _, _ -> confirmClearHistory() }
        }

        dialog = builder.create()
        dialog.show()
    }

    private fun makeHistoryRow(entry: HistoryEntry, onPicked: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(10), dp(6), dp(10))
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.history_row)
            setOnClickListener {
                insert(HistoryFormat.toExpression(entry.result), isNumber = true)
                updateDisplay(showPreview = true)
                onPicked()
            }
        }
        row.addView(TextView(this).apply {
            text = entry.expression
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
        })
        row.addView(TextView(this).apply {
            text = "= ${entry.result}"
            textSize = 19f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
        })
        return row
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_history))
            .setMessage(getString(R.string.clear_history_message))
            .setPositiveButton(getString(R.string.clear)) { _, _ ->
                store.clearHistory()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ------------------------------------------------------------------ misc

    @Suppress("DEPRECATION")
    private fun buzz() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v.vibrate(10)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val STATE_EXPRESSION = "state_expression"
        const val STATE_EVALUATED = "state_evaluated"
    }
}
