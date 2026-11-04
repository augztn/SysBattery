package com.nlab.sysbattery.data

import android.content.SharedPreferences

/**
 * Type-safe SharedPreferences readers that coerce numeric and boolean values safely
 * rather than throwing ClassCastException when legacy keys change representation.
 */
fun SharedPreferences.safeInt(key: String, default: Int): Int {
    val raw = all[key] ?: return default
    return when (raw) {
        is Int -> raw
        is Number -> raw.toInt()
        is String -> raw.toIntOrNull() ?: default
        else -> default
    }
}

fun SharedPreferences.safeLong(key: String, default: Long): Long {
    val raw = all[key] ?: return default
    return when (raw) {
        is Long -> raw
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull() ?: default
        else -> default
    }
}

fun SharedPreferences.safeFloat(key: String, default: Float): Float {
    val raw = all[key] ?: return default
    return when (raw) {
        is Float -> raw
        is Number -> raw.toFloat()
        is String -> raw.toFloatOrNull() ?: default
        else -> default
    }
}

fun SharedPreferences.safeDouble(key: String, default: Double): Double {
    val raw = all[key] ?: return default
    return when (raw) {
        is Double -> raw
        is Number -> raw.toDouble()
        is String -> raw.toDoubleOrNull() ?: default
        else -> default
    }
}

fun SharedPreferences.safeBoolean(key: String, default: Boolean): Boolean {
    val raw = all[key] ?: return default
    return when (raw) {
        is Boolean -> raw
        is String -> raw.toBooleanStrictOrNull() ?: default
        is Number -> raw.toInt() != 0
        else -> default
    }
}

fun SharedPreferences.safeString(key: String, default: String): String {
    val raw = all[key] ?: return default
    return raw as? String ?: raw.toString()
}
