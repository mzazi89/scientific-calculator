package com.mzazi.scientificcalculator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val expression: String, val result: String)

/**
 * Tiny persistence layer: calculation history plus the memory register and
 * angle mode, all kept in SharedPreferences (no database needed).
 */
class CalculatorStore(context: Context) {

    private val prefs = context.getSharedPreferences("calculator", Context.MODE_PRIVATE)

    var memory: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_MEMORY, 0L))
        set(value) = prefs.edit().putLong(KEY_MEMORY, java.lang.Double.doubleToRawLongBits(value)).apply()

    var angleMode: AngleMode
        get() = if (prefs.getString(KEY_ANGLE, AngleMode.DEG.name) == AngleMode.RAD.name) {
            AngleMode.RAD
        } else {
            AngleMode.DEG
        }
        set(value) = prefs.edit().putString(KEY_ANGLE, value.name).apply()

    var ans: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_ANS, 0L))
        set(value) = prefs.edit().putLong(KEY_ANS, java.lang.Double.doubleToRawLongBits(value)).apply()

    fun loadHistory(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val expression = item.optString("e")
                val result = item.optString("r")
                if (expression.isEmpty()) null else HistoryEntry(expression, result)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistory(expression: String, result: String) {
        val entries = ArrayList(loadHistory())
        entries.add(0, HistoryEntry(expression, result))
        while (entries.size > MAX_HISTORY) entries.removeAt(entries.size - 1)
        saveHistory(entries)
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(entries: List<HistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().put("e", entry.expression).put("r", entry.result))
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private companion object {
        const val KEY_HISTORY = "history"
        const val KEY_MEMORY = "memory"
        const val KEY_ANGLE = "angle"
        const val KEY_ANS = "ans"
        const val MAX_HISTORY = 100
    }
}
