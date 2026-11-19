package com.iosuite.core

/** Same person => same key, regardless of "+966", spaces and dashes. Shared by every app and the contact cache. */
object PhoneKey {
    fun of(address: String): String {
        val d = address.filter { it.isDigit() }
        return if (d.length >= 7) d.takeLast(9) else address.trim().lowercase()
    }
}
