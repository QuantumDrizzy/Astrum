package com.astrum.app.astro

import java.util.*
import kotlin.math.*

object PlanetCalc {

    data class OrbitalElements(
        val name: String,
        val symbol: String,
        val a: Double,      // semi-major axis (AU)
        val e: Double,      // eccentricity
        val i: Double,      // inclination (deg)
        val O: Double,      // longitude of ascending node (deg)
        val w: Double,      // argument of perihelion (deg)
        val L0: Double,     // mean longitude at J2000 (deg)
        val dL: Double,     // daily motion (deg/day)
        val baseMag: Double // approximate magnitude at 1 AU
    )

    data class PlanetData(
        val elements: OrbitalElements,
        val raHours: Double,
        val decDeg: Double,
        val distAU: Double,
        val distKm: Long,
        val altitude: Double,
        val azimuth: Double,
        val riseTime: Date?,
        val setTime: Date?,
        val transitTime: Date?,
        val isVisible: Boolean,
        val isLow: Boolean,
        val magnitude: Double,      // computed apparent magnitude
        val elongation: Double      // angular distance from Sun (deg)
    )

    val PLANETS = listOf(
        OrbitalElements("Mercurio", "\u263F", 0.387, 0.206, 7.00, 48.33,  29.12, 252.25, 4.09234, -0.5),
        OrbitalElements("Venus",    "\u2640", 0.723, 0.007, 3.39, 76.68,  54.88, 181.98, 1.60217, -4.5),
        OrbitalElements("Marte",    "\u2642", 1.524, 0.093, 1.85, 49.56, 286.50, 355.43, 0.52402, -1.0),
        OrbitalElements("Júpiter",  "\u2643", 5.203, 0.049, 1.30, 100.5, 274.20,  34.40, 0.08308, -2.5),
        OrbitalElements("Saturno",  "\u2644", 9.537, 0.055, 2.49, 113.7, 338.70,  50.08, 0.03346,  0.7),
        OrbitalElements("Urano",    "\u26E2",19.19,  0.047, 0.77, 74.00,  96.54, 314.06, 0.01172,  5.7),
        OrbitalElements("Neptuno",  "\u2646",30.07,  0.009, 1.77, 131.8, 276.30, 304.35, 0.00598,  7.9)
    )

    private fun geocentricPosition(pd: OrbitalElements, date: Date): Triple<Double, Double, Double> {
        val d  = AstroEngine.daysSinceJ2000(date)
        val L  = ((pd.L0 + pd.dL * d) % 360.0 + 360.0) % 360.0
        val Mv = ((L - pd.w) % 360.0 + 360.0) % 360.0 * AstroEngine.RAD

        // Equation of center (3 terms)
        val v = Mv + (2.0 * pd.e - pd.e * pd.e * pd.e / 4.0) * sin(Mv) +
                (5.0 / 4.0) * pd.e * pd.e * sin(2 * Mv)

        // Heliocentric distance
        val r  = pd.a * (1 - pd.e * pd.e) / (1 + pd.e * cos(v))
        val w2 = (pd.w + v * AstroEngine.DEG) * AstroEngine.RAD

        // Heliocentric ecliptic coords
        val bigO = pd.O * AstroEngine.RAD
        val inc  = pd.i * AstroEngine.RAD
        val xe = r * (cos(bigO) * cos(w2) - sin(bigO) * sin(w2) * cos(inc))
        val ye = r * (sin(bigO) * cos(w2) + cos(bigO) * sin(w2) * cos(inc))
        val ze = r * sin(w2) * sin(inc)

        // Earth's heliocentric position
        val Le = (100.464 + 0.98564736 * d) * AstroEngine.RAD
        val dx = xe - cos(Le)
        val dy = ye - sin(Le)
        val dz = ze
        val dist = sqrt(dx * dx + dy * dy + dz * dz)

        // Convert to equatorial
        val ep   = AstroEngine.OBLIQUITY * AstroEngine.RAD
        val yeq  = dy * cos(ep) - dz * sin(ep)
        val zeq  = dy * sin(ep) + dz * cos(ep)
        val ra   = ((atan2(yeq, dx) * AstroEngine.DEG / 15.0) % 24.0 + 24.0) % 24.0
        val dec  = asin((zeq / dist).coerceIn(-1.0, 1.0)) * AstroEngine.DEG

        return Triple(ra, dec, dist)
    }

    /** Approximate apparent magnitude based on distance */
    private fun apparentMagnitude(pd: OrbitalElements, distAU: Double, helioDistAU: Double): Double {
        return pd.baseMag + 5.0 * log10(distAU * helioDistAU)
    }

    /** Elongation (angular distance from Sun) in degrees */
    private fun elongation(date: Date, ra: Double, dec: Double): Double {
        val (sunRa, sunDec) = SolarCalc.raDecDeg(date)
        val dRa  = (ra - sunRa) * 15.0 * AstroEngine.RAD
        val decR = dec * AstroEngine.RAD
        val sunDecR = sunDec * AstroEngine.RAD
        val cosElong = sin(sunDecR) * sin(decR) + cos(sunDecR) * cos(decR) * cos(dRa)
        return acos(cosElong.coerceIn(-1.0, 1.0)) * AstroEngine.DEG
    }

    fun compute(pd: OrbitalElements, date: Date, latDeg: Double, lngDeg: Double): PlanetData {
        val (ra, dec, dist) = geocentricPosition(pd, date)
        val horiz   = AstroEngine.equatorialToHorizontal(ra, dec, date, latDeg, lngDeg)
        val rs      = AstroEngine.riseSetTransit(ra, dec, date, latDeg, lngDeg)
        val isDark  = SolarCalc.isDarkSky(date, latDeg, lngDeg)

        // Heliocentric distance needed for magnitude (approx from orbital elements)
        val d = AstroEngine.daysSinceJ2000(date)
        val L = ((pd.L0 + pd.dL * d) % 360.0 + 360.0) % 360.0
        val Mv = ((L - pd.w) % 360.0 + 360.0) % 360.0 * AstroEngine.RAD
        val v  = Mv + (2.0 * pd.e - pd.e * pd.e * pd.e / 4.0) * sin(Mv)
        val helioR = pd.a * (1 - pd.e * pd.e) / (1 + pd.e * cos(v))

        val mag   = apparentMagnitude(pd, dist, helioR)
        val elong = try { elongation(date, ra, dec) } catch (_: Exception) { 0.0 }

        return PlanetData(
            elements   = pd,
            raHours    = ra,
            decDeg     = dec,
            distAU     = dist,
            distKm     = (dist * 149_597_870L).toLong(),
            altitude   = horiz.altitude,
            azimuth    = horiz.azimuth,
            riseTime   = rs.rise,
            setTime    = rs.set,
            transitTime = rs.transit,
            isVisible  = horiz.altitude > 10 && isDark,
            isLow      = horiz.altitude in 0.0..10.0 && isDark,
            magnitude  = mag,
            elongation = elong
        )
    }

    fun computeAll(date: Date, latDeg: Double, lngDeg: Double): List<PlanetData> =
        PLANETS.map { compute(it, date, latDeg, lngDeg) }
}
