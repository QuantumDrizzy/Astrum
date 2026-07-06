package com.quantumdrizzy.astro

import java.util.*
import kotlin.math.*

object AstroEngine {

    // ── Core constants ─────────────────────────────────────────────────
    const val RAD = Math.PI / 180.0
    const val DEG = 180.0 / Math.PI
    const val J2000 = 2451545.0
    const val OBLIQUITY = 23.439291111

    // ── Time ───────────────────────────────────────────────────────────
    fun julianDay(date: Date): Double =
        date.time / 86400000.0 + 2440587.5

    fun daysSinceJ2000(date: Date): Double =
        julianDay(date) - J2000

    /** Local Sidereal Time in degrees */
    fun lstDeg(date: Date, lngDeg: Double): Double {
        val JD = julianDay(date)
        val T = (JD - J2000) / 36525.0
        var GMST = 280.46061837 + 360.98564736629 * (JD - J2000) + T * T * 0.000387933
        GMST = ((GMST % 360) + 360) % 360
        return ((GMST + lngDeg) % 360 + 360) % 360
    }

    fun lstHours(date: Date, lngDeg: Double): Double = lstDeg(date, lngDeg) / 15.0

    fun lstString(date: Date, lngDeg: Double): String {
        val s = lstDeg(date, lngDeg) / 15.0
        val h = s.toInt()
        val m = ((s - h) * 60).toInt()
        val sec = (((s - h) * 60 - m) * 60).toInt()
        return "%02d:%02d:%02d".format(h, m, sec)
    }

    // ── Coordinate transforms ──────────────────────────────────────────
    data class HorizCoords(val altitude: Double, val azimuth: Double)

    fun equatorialToHorizontal(
        raHours: Double, decDeg: Double,
        date: Date, latDeg: Double, lngDeg: Double
    ): HorizCoords {
        val H = ((lstDeg(date, lngDeg) - raHours * 15.0) % 360 + 360) % 360 * RAD
        val lat = latDeg * RAD
        val dec = decDeg * RAD

        val sinAlt = sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(H)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0)) * DEG

        val cosAz = (sin(dec) - sin(lat) * sinAlt) /
                (cos(lat) * cos(alt * RAD).coerceAtLeast(0.0001))
        var az = acos(cosAz.coerceIn(-1.0, 1.0)) * DEG
        if (sin(H) > 0) az = 360.0 - az

        return HorizCoords(alt, az)
    }

    // ── Rise / Set / Transit ───────────────────────────────────────────
    data class RiseSetResult(
        val rise: Date?,
        val set: Date?,
        val transit: Date?,
        val isCircumpolar: Boolean,
        val neverRises: Boolean
    )

    fun riseSetTransit(
        raHours: Double, decDeg: Double,
        date: Date, latDeg: Double, lngDeg: Double
    ): RiseSetResult {
        val cosH0 = -tan(latDeg * RAD) * tan(decDeg * RAD)
        if (cosH0 < -1.0) return RiseSetResult(null, null, null, true, false)
        if (cosH0 > 1.0) return RiseSetResult(null, null, null, false, true)

        val H0 = acos(cosH0) * DEG
        val lst = lstHours(date, lngDeg)
        var dh = raHours - lst
        while (dh < 0) dh += 24.0
        while (dh > 24) dh -= 24.0
        if (dh > 12) dh -= 24.0

        val transit = Date(date.time + (dh * 3600000).toLong())
        val rise = Date(transit.time - (H0 / 15.0 * 3600000).toLong())
        val set = Date(transit.time + (H0 / 15.0 * 3600000).toLong())
        return RiseSetResult(rise, set, transit, false, false)
    }

    // ── RA / Dec formatting ────────────────────────────────────────────
    fun raToString(raH: Double): String {
        val h = raH.toInt()
        val m = ((raH - h) * 60).toInt()
        val s = (((raH - h) * 60 - m) * 60).toInt()
        return "%02dh %02dm %02ds".format(h, m, s)
    }

    fun decToString(dec: Double): String {
        val sign = if (dec >= 0) "+" else "-"
        val a = abs(dec)
        val d = a.toInt()
        val m = ((a - d) * 60).toInt()
        return "%s%02d° %02d'".format(sign, d, m)
    }

    fun formatTime(date: Date?): String {
        if (date == null) return "--:--"
        val cal = Calendar.getInstance().apply { time = date }
        return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    // ── Visibility label ───────────────────────────────────────────────
    enum class VisibilityLevel { HIGH, MEDIUM, LOW, HIDDEN }

    fun visibilityLevel(alt: Double): VisibilityLevel = when {
        alt > 30 -> VisibilityLevel.HIGH
        alt > 10 -> VisibilityLevel.MEDIUM
        alt > 0  -> VisibilityLevel.LOW
        else     -> VisibilityLevel.HIDDEN
    }
}
