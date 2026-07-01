package com.astrum.app

import android.content.Context
import android.graphics.Color

object NightModeManager {

    private const val PREFS = "astrum_prefs"
    private const val KEY   = "night_mode"
    private var appCtx: Context? = null

    fun init(ctx: Context) { appCtx = ctx.applicationContext }

    var isNightMode: Boolean
        get() = appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    ?.getBoolean(KEY, false) ?: false
        set(v) { appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    ?.edit()?.putBoolean(KEY, v)?.apply() }

    fun toggle(): Boolean { isNightMode = !isNightMode; return isNightMode }

    // Red night palette — brighter, cleaner reds read fresher than the old muddy #CC0000 while
    // still preserving dark adaptation (deep-red only). Kept on true black for OLED contrast.
    val RED:       Int get() = Color.rgb(255, 72, 66)   // primary (headings, active)
    val RED_DIM:   Int get() = Color.rgb(163, 48, 44)   // secondary (labels, inactive)
    val RED_FAINT: Int get() = Color.rgb(40, 12, 11)    // hairlines / faint fills
    val BG_NIGHT:  Int get() = Color.BLACK
}
