package com.astrum.app.astro

import java.util.Date
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Planet positions from JPL's Keplerian "approximate positions" elements (Standish; valid
 * 1800–2050) — accurate to arcminutes, plenty to know which planet is up, where, and how far.
 *
 * This replaces the position math in the old PlanetCalc, which was wrong: it used the longitude
 * of perihelion as if it were the argument of perihelion (they differ by Ω), modelled the Earth
 * as a 1 AU circle, and used single-epoch elements with no secular rates (so positions drifted
 * with the years). The implementation here was cross-checked against published planet visibility
 * for June 2026 (Venus evening/west, Jupiter low evening, Saturn morning/east, Mars low pre-dawn).
 *
 * https://ssd.jpl.nasa.gov/planets/approx_pos.html
 */
object PlanetEphem {

    private const val RAD = PI / 180.0
    private const val DEG = 180.0 / PI

    // a(AU)+rate, e+rate, I(deg)+rate, L(deg)+rate, ϖ(deg)+rate, Ω(deg)+rate  (rates per century)
    private class El(
        val a0: Double, val aR: Double, val e0: Double, val eR: Double,
        val i0: Double, val iR: Double, val l0: Double, val lR: Double,
        val w0: Double, val wR: Double, val o0: Double, val oR: Double,
    )

    private val EARTH = El(1.00000261, 0.00000562, 0.01671123, -0.00004392, -0.00001531, -0.01294668,
        100.46457166, 35999.37244981, 102.93768193, 0.32327364, 0.0, 0.0)

    private val E = linkedMapOf(
        "Mercurio" to El(0.38709927, 0.00000037, 0.20563593, 0.00001906, 7.00497902, -0.00594749,
            252.25032350, 149472.67411175, 77.45779628, 0.16047689, 48.33076593, -0.12534081),
        "Venus" to El(0.72333566, 0.00000390, 0.00677672, -0.00004107, 3.39467605, -0.00078890,
            181.97909950, 58517.81538729, 131.60246718, 0.00268329, 76.67984255, -0.27769418),
        "Marte" to El(1.52371034, 0.00001847, 0.09339410, 0.00007882, 1.84969142, -0.00813131,
            -4.55343205, 19140.30268499, -23.94362959, 0.44441088, 49.55953891, -0.29257343),
        "Júpiter" to El(5.20288700, -0.00011607, 0.04838624, -0.00013253, 1.30439695, -0.00183714,
            34.39644051, 3034.74612775, 14.72847983, 0.21252668, 100.47390909, 0.20469106),
        "Saturno" to El(9.53667594, -0.00125060, 0.05386179, -0.00050991, 2.48599187, 0.00193609,
            49.95424423, 1222.49362201, 92.59887831, -0.41897216, 113.66242448, -0.28867794),
        "Urano" to El(19.18916464, -0.00196176, 0.04725744, -0.00004397, 0.77263783, -0.00242939,
            313.23810451, 428.48202785, 170.95427630, 0.40805281, 74.01692503, 0.04240589),
        "Neptuno" to El(30.06992276, 0.00026291, 0.00859048, 0.00005105, 1.77004347, 0.00035372,
            -55.12002969, 218.45945325, 44.96476227, -0.32241464, 131.78422574, -0.00508664),
    )

    private fun helio(p: El, T: Double): DoubleArray {
        val a = p.a0 + p.aR * T
        val e = p.e0 + p.eR * T
        val i = (p.i0 + p.iR * T) * RAD
        val l = p.l0 + p.lR * T
        val w = p.w0 + p.wR * T
        val oDeg = p.o0 + p.oR * T
        val o = oDeg * RAD
        val arg = (w - oDeg) * RAD                       // ω = ϖ − Ω
        var m = (l - w) % 360.0
        if (m < -180) m += 360.0
        if (m > 180) m -= 360.0
        m *= RAD
        var ecc = m + e * sin(m)
        repeat(7) { ecc -= (ecc - e * sin(ecc) - m) / (1 - e * cos(ecc)) }
        val xp = a * (cos(ecc) - e)
        val yp = a * sqrt(1 - e * e) * sin(ecc)
        val cw = cos(arg); val sw = sin(arg); val co = cos(o); val so = sin(o); val ci = cos(i); val si = sin(i)
        val x = (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp
        val y = (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp
        val z = (sw * si) * xp + (cw * si) * yp
        return doubleArrayOf(x, y, z)
    }

    // [geoX, geoY, geoZ, earthX, earthY, earthZ] in J2000 ecliptic coords (AU).
    private fun geo(name: String, date: Date): DoubleArray? {
        val el = E[name] ?: return null
        val t = (AstroEngine.julianDay(date) - AstroEngine.J2000) / 36525.0
        val p = helio(el, t)
        val ea = helio(EARTH, t)
        return doubleArrayOf(p[0] - ea[0], p[1] - ea[1], p[2] - ea[2], ea[0], ea[1], ea[2])
    }

    /** Equatorial position: (raHours, decDeg, geocentricDistanceAU). Null if name unknown. */
    fun equatorial(name: String, date: Date): Triple<Double, Double, Double>? {
        val g = geo(name, date) ?: return null
        val eps = AstroEngine.OBLIQUITY * RAD
        val xq = g[0]
        val yq = g[1] * cos(eps) - g[2] * sin(eps)
        val zq = g[1] * sin(eps) + g[2] * cos(eps)
        val raH = ((atan2(yq, xq) * DEG / 15.0) % 24 + 24) % 24
        val dec = atan2(zq, sqrt(xq * xq + yq * yq)) * DEG
        val dist = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
        return Triple(raH, dec, dist)
    }

    /** Heliocentric distance of the planet (AU) — used for apparent magnitude. */
    fun helioDist(name: String, date: Date): Double {
        val g = geo(name, date) ?: return 0.0
        val px = g[0] + g[3]; val py = g[1] + g[4]; val pz = g[2] + g[5]
        return sqrt(px * px + py * py + pz * pz)
    }
}
