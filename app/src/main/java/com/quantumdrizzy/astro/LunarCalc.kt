package com.quantumdrizzy.astro

import java.util.*
import kotlin.math.*

object LunarCalc {

    data class MoonIllumination(val fraction: Double, val phase: Double, val angle: Double)
    data class MoonPosition(
        val altitude: Double, val azimuth: Double,
        val distance: Double, val parallacticAngle: Double
    )
    data class MoonTimes(val rise: Date?, val set: Date?)

    private fun moonCoords(d: Double): Triple<Double, Double, Double> {
        val L = (218.316 + 13.176396 * d) * AstroEngine.RAD
        val M = (134.963 + 13.064993 * d) * AstroEngine.RAD
        val F = (93.272 + 13.229350 * d) * AstroEngine.RAD
        val l = L + 6.289 * AstroEngine.RAD * sin(M)
        val b = 5.128 * AstroEngine.RAD * sin(F)
        val dt = 385001.0 - 20905.0 * cos(M)
        val ep = AstroEngine.OBLIQUITY * AstroEngine.RAD
        val ra = atan2(cos(ep) * sin(l) - tan(b) * sin(ep), cos(l))
        val dec = asin(sin(b) * cos(ep) + cos(b) * sin(ep) * sin(l))
        return Triple(
            ((ra * AstroEngine.DEG / 15.0) % 24 + 24) % 24,
            dec * AstroEngine.DEG,
            dt
        )
    }

    fun illumination(date: Date): MoonIllumination {
        val d = AstroEngine.daysSinceJ2000(date)
        val (moonRa, moonDec, moonDist) = moonCoords(d)

        // Sun
        val sunD = d
        val sunL = ((280.46 + 0.9856474 * sunD) % 360 + 360) % 360 * AstroEngine.RAD
        val sunG = ((357.528 + 0.9856003 * sunD) % 360 + 360) % 360 * AstroEngine.RAD
        val sunLambda = sunL + 1.915 * AstroEngine.RAD * sin(sunG) + 0.020 * AstroEngine.RAD * sin(2 * sunG)
        val ep = AstroEngine.OBLIQUITY * AstroEngine.RAD
        val sunRa = atan2(cos(ep) * sin(sunLambda), cos(sunLambda))
        val sunDec = asin(sin(ep) * sin(sunLambda))
        val sunDist = 149598000.0

        val phi = acos(
            sin(sunDec) * sin(moonDec * AstroEngine.RAD) +
            cos(sunDec) * cos(moonDec * AstroEngine.RAD) *
            cos(sunRa - moonRa * 15.0 * AstroEngine.RAD)
        )
        val inc = atan2(sunDist * sin(phi), moonDist - sunDist * cos(phi))
        val angle = atan2(
            cos(sunDec) * sin(sunRa - moonRa * 15.0 * AstroEngine.RAD),
            sin(sunDec) * cos(moonDec * AstroEngine.RAD) -
            cos(sunDec) * sin(moonDec * AstroEngine.RAD) *
            cos(sunRa - moonRa * 15.0 * AstroEngine.RAD)
        )
        val fraction = (1 + cos(inc)) / 2.0
        val phase = 0.5 + 0.5 * inc * if (angle < 0) -1.0 else 1.0 / Math.PI

        return MoonIllumination(fraction, phase, angle * AstroEngine.DEG)
    }

    fun position(date: Date, latDeg: Double, lngDeg: Double): MoonPosition {
        val d = AstroEngine.daysSinceJ2000(date)
        val (ra, dec, dist) = moonCoords(d)
        val H = ((AstroEngine.lstDeg(date, lngDeg) - ra * 15.0) % 360 + 360) % 360 * AstroEngine.RAD
        val latR = latDeg * AstroEngine.RAD
        val decR = dec * AstroEngine.RAD
        val sinAlt = sin(latR) * sin(decR) + cos(latR) * cos(decR) * cos(H)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0)) * AstroEngine.DEG
        val cosAz = (sin(decR) - sin(latR) * sinAlt) / (cos(latR) * cos(alt * AstroEngine.RAD).coerceAtLeast(0.0001))
        var az = acos(cosAz.coerceIn(-1.0, 1.0)) * AstroEngine.DEG
        if (sin(H) > 0) az = 360.0 - az
        val pa = atan2(sin(H), tan(latR) * cos(decR) - sin(decR) * cos(H))
        return MoonPosition(alt, az, dist, pa * AstroEngine.DEG)
    }

    fun riseSet(date: Date, latDeg: Double, lngDeg: Double): MoonTimes {
        val d = AstroEngine.daysSinceJ2000(date)
        val (ra, dec, _) = moonCoords(d)
        val rs = AstroEngine.riseSetTransit(ra, dec, date, latDeg, lngDeg)
        return MoonTimes(rs.rise, rs.set)
    }

    /** English phase name (the library's neutral default). Apps that localize should use
     *  [phaseIndex] and map it to their own strings. */
    fun phaseName(phase: Double): String = when (phaseIndex(phase)) {
        0 -> "New Moon"
        1 -> "Waxing Crescent"
        2 -> "First Quarter"
        3 -> "Waxing Gibbous"
        4 -> "Full Moon"
        5 -> "Waning Gibbous"
        6 -> "Last Quarter"
        else -> "Waning Crescent"
    }

    /** Language-neutral moon phase, 0..7 (0 = New, 4 = Full), for callers that localize. */
    fun phaseIndex(phase: Double): Int {
        val p = ((phase % 1.0) + 1.0) % 1.0
        return when {
            p < 0.03 || p > 0.97 -> 0
            p < 0.22 -> 1
            p < 0.28 -> 2
            p < 0.47 -> 3
            p < 0.53 -> 4
            p < 0.72 -> 5
            p < 0.78 -> 6
            else -> 7
        }
    }
}
