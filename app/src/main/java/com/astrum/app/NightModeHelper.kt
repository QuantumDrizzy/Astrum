package com.astrum.app

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.astrum.app.views.StarFieldView

// Sentinel: distinguishes "original background was null" from "never saved"
private object NullBgSentinel

/**
 * Recursively applies / restores night-vision mode colours on a static view tree.
 * NOT for use inside RecyclerView items — adapters handle those directly.
 */
fun View.applyNightRecursive(isNight: Boolean) {

    // ── StarFieldView: just fade alpha ────────────────────────────────────
    if (this is StarFieldView) {
        animate().alpha(if (isNight) 0f else 1f).setDuration(250).start()
        return
    }

    // ── TextView: save/restore text colour ───────────────────────────────
    if (this is TextView) {
        if (isNight) {
            if (getTag(R.id.tag_night_color) == null)
                setTag(R.id.tag_night_color, currentTextColor)
            setTextColor(NightModeManager.RED)
        } else {
            val orig = getTag(R.id.tag_night_color)
            if (orig is Int) { setTextColor(orig) }
            setTag(R.id.tag_night_color, null)
        }
        return          // TextViews are leaves for our purposes
    }

    // ── RecyclerView: do NOT recurse — adapter handles item colours ───────
    if (this is RecyclerView) return

    // ── Generic ViewGroup: save/restore background, then recurse ─────────
    if (this is ViewGroup) {
        if (isNight) {
            if (getTag(R.id.tag_night_bg) == null)
                setTag(R.id.tag_night_bg, background ?: NullBgSentinel)
            setBackgroundColor(Color.BLACK)
        } else {
            when (val saved = getTag(R.id.tag_night_bg)) {
                null            -> { /* was never changed */ }
                NullBgSentinel  -> { background = null; setTag(R.id.tag_night_bg, null) }
                is Drawable     -> { background = saved; setTag(R.id.tag_night_bg, null) }
            }
        }
        for (i in 0 until childCount) getChildAt(i).applyNightRecursive(isNight)
    }
    // Other View types (ImageView, ProgressBar, etc.) — no action
}
