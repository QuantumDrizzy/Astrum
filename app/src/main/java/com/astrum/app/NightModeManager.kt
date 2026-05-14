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

    // Pre-parsed color values for performance
    val RED:       Int get() = Color.rgb(204, 0, 0)
    val RED_DIM:   Int get() = Color.rgb(100, 0, 0)
    val RED_FAINT: Int get() = Color.rgb(26, 0, 0)
    val BG_NIGHT:  Int get() = Color.BLACK
}
