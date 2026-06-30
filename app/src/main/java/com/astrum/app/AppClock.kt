package com.astrum.app

import java.util.Date

/**
 * Single source of "now" for the whole app: real time, or a frozen instant chosen in Settings to
 * plan a different night ("where will Saturn be at 02:00 next Friday?"). Every fragment computes
 * the sky from AppClock.now() instead of Date(), so the override applies everywhere at once.
 */
object AppClock {
    fun now(): Date = if (AppPrefs.frozenTime) Date(AppPrefs.frozenEpochMillis) else Date()
    val isFrozen: Boolean get() = AppPrefs.frozenTime
}
