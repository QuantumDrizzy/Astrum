package com.astrum.app.astro

import java.util.*
import kotlin.math.*

object SolarCalc {

    data class SunPosition(val altitude: Double, val azimuth: Double)
    data class SunTimes(
        val sunrise: Date?, val sunset: Date?, val solarNoon: Date?,
        val civilDawn: Date?, val civilDusk: Date?,
        val nauticalDawn: Date?, val nauticalDusk: Date?,
        val astronomicalDawn: Date?, val astronomicalDusk: Date?,
        val goldenHourStart: Date?, val goldenHourEnd: Date?
    )

    fun position(date: Date, latDeg: Double, lngDeg: Double): SunPosition {
        val d = AstroEngine.daysSinceJ2000(date)
        val L = ((280.46 + 0.9856474 * d) % 360 + 360) % 360
        val g = ((357.528 + 0.9856003 * d) % 360 + 360) % 360 * AstroEngine.RAD
        val lambda = (L + 1.915 * sin(g) + 0.020 * sin(2 * g)) * AstroEngine.RAD
        val epsilon = AstroEngine.OBLIQUITY * AstroEngine.RAD

        val ra = atan2(cos(epsilon) * sin(lambda), cos(lambda))
        val dec = asin(sin(epsilon) * sin(lambda))
        val raH = ((ra * AstroEngine.DEG / 15.0) % 24 + 24) % 24
        val decD = dec * AstroEngine.DEG

        val coords = AstroEngine.equatorialToHorizontal(raH, decD, date, latDeg, lngDeg)
        return SunPosition(coords.altitude, coords.azimuth)
    }

    fun raDecDeg(date: Date): Pair<Double, Double> {
        val d = AstroEngine.daysSinceJ2000(date)
        val L = ((280.46 + 0.9856474 * d) % 360 + 360) % 360
        val g = ((357.528 + 0.9856003 * d) % 360 + 360) % 360 * AstroEngine.RAD
        val lambda = (L + 1.915 * sin(g) + 0.020 * sin(2 * g)) * AstroEngine.RAD
        val epsilon = AstroEngine.OBLIQUITY * AstroEngine.RAD
        val ra = atan2(cos(epsilon) * sin(lambda), cos(lambda)) * AstroEngine.DEG
        val dec = asin(sin(epsilon) * sin(lambda)) * AstroEngine.DEG
        return Pair(((ra / 15.0) % 24 + 24) % 24, dec)
    }

    fun times(date: Date, latDeg: Double, lngDeg: Double): SunTimes {
        val sunRaDec = raDecDeg(date)
        fun timeForAlt(targetAlt: Double): Pair<Date?, Date?> {
            val decR = sunRaDec.second * AstroEngine.RAD
            val latR = latDeg * AstroEngine.RAD
            val cosH = (sin(targetAlt * AstroEngine.RAD) - sin(latR) * sin(decR)) /
                    (cos(latR) * cos(decR))
            if (cosH < -1.0 || cosH > 1.0) return Pair(null, null)
            val H0 = acos(cosH) * AstroEngine.DEG
            val lst = AstroEngine.lstHours(date, lngDeg)
            var dh = sunRaDec.first - lst
            while (dh < 0) dh += 24.0
            while (dh > 24) dh -= 24.0
            if (dh > 12) dh -= 24.0
            val noon = Date(date.time + (dh * 3600000).toLong())
            val rise = Date(noon.time - (H0 / 15.0 * 3600000).toLong())
            val set = Date(noon.time + (H0 / 15.0 * 3600000).toLong())
            return Pair(rise, set)
        }

        val (sunrise, sunset) = timeForAlt(-0.833)
        val noon = if (sunrise != null && sunset != null)
            Date((sunrise.time + sunset.time) / 2) else null
        val (cd, cds) = timeForAlt(-6.0)
        val (nd, nds) = timeForAlt(-12.0)
        val (ad, ads) = timeForAlt(-18.0)
        val (ghs, ghe) = timeForAlt(6.0)

        return SunTimes(sunrise, sunset, noon, cd, cds, nd, nds, ad, ads, ghe, ghs)
    }

    fun isDarkSky(date: Date, latDeg: Double, lngDeg: Double): Boolean =
        position(date, latDeg, lngDeg).altitude < -6.0

    fun dayLengthString(times: SunTimes): String {
        val rise = times.sunrise ?: return "--h --m"
        val set = times.sunset ?: return "--h --m"
        val ms = set.time - rise.time
        val h = (ms / 3600000).toInt()
        val m = ((ms % 3600000) / 60000).toInt()
        return "%dh %02dm".format(h, m)
    }
}
