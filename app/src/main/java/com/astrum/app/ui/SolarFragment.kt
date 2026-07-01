package com.astrum.app.ui
import com.astrum.app.AppClock

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.astrum.app.MainActivity
import com.astrum.app.NightModeManager
import com.astrum.app.R
import com.astrum.app.applyNightRecursive
import com.astrum.app.astro.*
import com.quantumdrizzy.astro.AstroEngine
import com.quantumdrizzy.astro.SolarCalc
import com.quantumdrizzy.astro.LunarCalc
import com.astrum.app.databinding.FragmentSolarBinding
import com.astrum.app.location.AstroLocation
import kotlinx.coroutines.*
import java.util.*

class SolarFragment : Fragment() {
    private var _b: FragmentSolarBinding? = null
    private val b get() = _b!!
    private var location: AstroLocation? = null
    private var job: Job? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSolarBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)

        val activity = requireActivity() as MainActivity
        location = activity.currentLocation
        activity.locationListeners["SolarFragment"] = { loc ->
            location = loc
            if (_b != null) render()
        }
        activity.nightModeListeners["SolarFragment"] = {
            applyNightMode(NightModeManager.isNightMode)
        }
        applyNightMode(NightModeManager.isNightMode)

        // viewLifecycleOwner scope — auto-cancelled on onDestroyView
        job = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) { render(); delay(10_000L) }
        }
    }

    private fun render() {
        val b   = _b ?: return
        val loc = location ?: return
        val now = AppClock.now()

        try {
            val sunPos   = SolarCalc.position(now, loc.latitude, loc.longitude)
            val sunTimes = SolarCalc.times(now, loc.latitude, loc.longitude)
            val moonIll  = LunarCalc.illumination(now)
            val moonPos  = LunarCalc.position(now, loc.latitude, loc.longitude)
            val moonTimes = LunarCalc.riseSet(now, loc.latitude, loc.longitude)
            val (sunRa, sunDec) = SolarCalc.raDecDeg(now)

            // Sun
            b.tvSunAlt.text    = "%.2f°".format(sunPos.altitude)
            b.tvSunAz.text     = "%.1f°".format(sunPos.azimuth)
            b.tvSunDayLen.text = SolarCalc.dayLengthString(sunTimes)
            b.tvSunRise.text   = AstroEngine.formatTime(sunTimes.sunrise)
            b.tvSunNoon.text   = AstroEngine.formatTime(sunTimes.solarNoon)
            b.tvSunSet.text    = AstroEngine.formatTime(sunTimes.sunset)
            b.tvAstroDawn.text = AstroEngine.formatTime(sunTimes.astronomicalDawn)
            b.tvAstroDusk.text = AstroEngine.formatTime(sunTimes.astronomicalDusk)
            b.tvNautDawn.text  = AstroEngine.formatTime(sunTimes.nauticalDawn)
            b.tvNautDusk.text  = AstroEngine.formatTime(sunTimes.nauticalDusk)
            b.tvGoldenHour.text = AstroEngine.formatTime(sunTimes.goldenHourStart)
            b.tvGoldenEnd.text  = AstroEngine.formatTime(sunTimes.goldenHourEnd)
            b.tvSunRa.text     = AstroEngine.raToString(sunRa)
            b.tvSunDec.text    = AstroEngine.decToString(sunDec)

            // Arc progress bar (day progress)
            val rise = sunTimes.sunrise?.time ?: 0L
            val set  = sunTimes.sunset?.time  ?: 1L
            val cur  = now.time
            val pct  = if (set > rise)
                ((cur - rise).toFloat() / (set - rise).toFloat()).coerceIn(0f, 1f)
            else 0f
            b.sunArcProgress.progress = (pct * 100).toInt()

            // Moon
            b.moonView.phase        = moonIll.phase
            b.moonView.illumination = moonIll.fraction
            b.tvMoonPhase.text  = resources.getStringArray(R.array.moon_phases)[LunarCalc.phaseIndex(moonIll.phase)]
            b.tvMoonIllum.text  = "${(moonIll.fraction * 100).toInt()}%"
            b.tvMoonAlt.text    = "%.1f°".format(moonPos.altitude)
            b.tvMoonDist.text   = "%,.0f km".format(moonPos.distance)
            b.tvMoonRise.text   = AstroEngine.formatTime(moonTimes.rise)
            b.tvMoonSet.text    = AstroEngine.formatTime(moonTimes.set)

            // LST + JD
            b.tvLst.text = AstroEngine.lstString(now, loc.longitude)
            b.tvJd.text  = "%.3f".format(AstroEngine.julianDay(now))
        } catch (_: Exception) {}
    }

    private fun applyNightMode(isNight: Boolean) {
        (_b?.root as? ViewGroup)?.applyNightRecursive(isNight)
        _b?.moonView?.nightMode = isNight
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.locationListeners?.remove("SolarFragment")
        (activity as? MainActivity)?.nightModeListeners?.remove("SolarFragment")
        job?.cancel()
        _b = null
    }
}
