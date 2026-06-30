package com.astrum.app

import android.content.Context

/**
 * Persistent user settings (same SharedPreferences file as night mode):
 *  - location source: automatic GPS, or a manually entered fixed point (for indoor use, no-GPS
 *    devices like GrapheneOS without network location, or planning from another site);
 *  - an optional frozen instant, to compute the sky for a different night.
 * Doubles are stored via their raw bits since SharedPreferences has no double type.
 */
object AppPrefs {
    private const val PREFS = "astrum_prefs"
    private var appCtx: Context? = null

    fun init(ctx: Context) { appCtx = ctx.applicationContext }
    private fun sp() = appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Location ──────────────────────────────────────────────────────────
    var manualLocation: Boolean
        get() = sp()?.getBoolean("loc_manual", false) ?: false
        set(v) { sp()?.edit()?.putBoolean("loc_manual", v)?.apply() }

    var manualLat: Double
        get() = Double.fromBits(sp()?.getLong("loc_lat", 0L) ?: 0L)
        set(v) { sp()?.edit()?.putLong("loc_lat", v.toRawBits())?.apply() }

    var manualLng: Double
        get() = Double.fromBits(sp()?.getLong("loc_lng", 0L) ?: 0L)
        set(v) { sp()?.edit()?.putLong("loc_lng", v.toRawBits())?.apply() }

    var manualName: String
        get() = sp()?.getString("loc_name", "") ?: ""
        set(v) { sp()?.edit()?.putString("loc_name", v)?.apply() }

    // ── Time ──────────────────────────────────────────────────────────────
    var frozenTime: Boolean
        get() = sp()?.getBoolean("time_frozen", false) ?: false
        set(v) { sp()?.edit()?.putBoolean("time_frozen", v)?.apply() }

    var frozenEpochMillis: Long
        get() = sp()?.getLong("time_epoch", System.currentTimeMillis()) ?: System.currentTimeMillis()
        set(v) { sp()?.edit()?.putLong("time_epoch", v)?.apply() }
}
