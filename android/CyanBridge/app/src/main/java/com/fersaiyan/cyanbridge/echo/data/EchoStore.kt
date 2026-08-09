package com.fersaiyan.cyanbridge.echo.data

import android.content.Context
import androidx.core.content.edit
import com.fersaiyan.cyanbridge.echo.model.HistoryEntry
import org.json.JSONArray
import org.json.JSONObject

/** Voice + haptic preferences. Persisted; the ECHO UI is the only writer. */
data class EchoSettings(
    val rate: Float = 0.9f, // slightly slower than normal speech
    val volume: Float = 1.0f,
    val hapticScale: Float = 1f, // 0 off | 0.5 gentle | 1 normal | 1.5 strong
)

object EchoLimits {
    const val RATE_MIN = 0.5f
    const val RATE_MAX = 1.4f
    const val RATE_STEP = 0.1f

    const val VOLUME_MIN = 0.2f
    const val VOLUME_MAX = 1.0f
    const val VOLUME_STEP = 0.2f

    val hapticSteps = listOf(0f, 0.5f, 1f, 1.5f)
    val hapticLabels = listOf("Off", "Gentle", "Normal", "Strong")

    fun hapticLabel(scale: Float): String {
        val i = hapticSteps.indexOfFirst { it == scale }
        return if (i >= 0) hapticLabels[i] else "Normal"
    }
}

fun clamp(v: Float, min: Float, max: Float): Float = minOf(max, maxOf(min, v))

/**
 * SharedPreferences-backed persistence for the ECHO screens.
 *
 * Deliberately not Room: this is three scalars and a capped list, and adding
 * an entity here would drag the ECHO package into the app's KSP graph for no
 * benefit. Timestamps are stored as epoch milliseconds so a row survives the
 * JSON round trip — a serialised date comes back as a string and every
 * downstream format call throws.
 */
class EchoStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("echo.prefs.v1", Context.MODE_PRIVATE)

    /* ---------- settings ---------- */

    fun loadSettings(): EchoSettings {
        val d = EchoSettings()
        return EchoSettings(
            rate = prefs.getFloat(KEY_RATE, d.rate),
            volume = prefs.getFloat(KEY_VOLUME, d.volume),
            hapticScale = prefs.getFloat(KEY_HAPTIC, d.hapticScale),
        )
    }

    fun saveSettings(s: EchoSettings) {
        prefs.edit {
            putFloat(KEY_RATE, s.rate)
            putFloat(KEY_VOLUME, s.volume)
            putFloat(KEY_HAPTIC, s.hapticScale)
        }
    }

    /* ---------- history ---------- */

    fun loadHistory(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val expression = o.optString("expression").takeIf { it.isNotBlank() } ?: continue
                    val time = o.optLong("time", 0L).takeIf { it > 0L } ?: continue
                    add(HistoryEntry(expression, o.optInt("pct", 0), time))
                }
            }
        }.getOrDefault(emptyList()).take(MAX_HISTORY)
    }

    fun saveHistory(items: List<HistoryEntry>) {
        val arr = JSONArray()
        items.take(MAX_HISTORY).forEach { h ->
            arr.put(
                JSONObject().apply {
                    put("expression", h.expression)
                    put("pct", h.pct)
                    put("time", h.time)
                },
            )
        }
        prefs.edit { putString(KEY_HISTORY, arr.toString()) }
    }

    fun clearHistory() {
        prefs.edit { remove(KEY_HISTORY) }
    }

    companion object {
        const val MAX_HISTORY = 50
        private const val KEY_RATE = "rate"
        private const val KEY_VOLUME = "volume"
        private const val KEY_HAPTIC = "hapticScale"
        private const val KEY_HISTORY = "history"
    }
}
