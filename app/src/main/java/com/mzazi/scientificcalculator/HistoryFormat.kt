package com.mzazi.scientificcalculator

/**
 * Converts stored result text back into something the expression engine can
 * parse. Results such as `1.234e15` would otherwise be read as the constant
 * `e` followed by a number.
 */
object HistoryFormat {

    fun toExpression(text: String): String {
        if (!text.contains('e')) return text
        val parts = text.split("e")
        if (parts.size != 2) return text
        return "(${parts[0]}*10^${parts[1]})"
    }
}
