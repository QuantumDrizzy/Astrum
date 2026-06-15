package com.astrum.app.astro

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Push-to guidance: given where the phone is currently pointing on the sky
 * (alt, az) and a target's (alt, az), tell the user how to move the phone to
 * land on the target — "turn N° right / tilt M° up" — and when they're on it.
 *
 * This is the math layer only. It is pure (kotlin.math, no Android deps) so it
 * can be unit-tested on the JVM and is cross-validated against the identical
 * algorithm in `validation/validate_pushto.py` (vs great-circle references).
 *
 * The piece that FEEDS it — fusing the phone's rotation-vector sensor into a
 * live (alt, az) the device is aimed at, plus the AR overlay — is device-side
 * (compass/gyro/camera) and must be validated on a phone, not here.
 *
 * Angle conventions match [AstroEngine.HorizCoords]:
 *  - azimuth in degrees, from North, clockwise (N=0, E=90, S=180, W=270)
 *  - altitude in degrees above the horizon (0 = horizon, 90 = zenith)
 */
object PushTo {

    private const val RAD = Math.PI / 180.0
    private const val DEG = 180.0 / Math.PI

    /** Default "you're on it" radius (deg) — a typical finderscope FOV. */
    const val DEFAULT_FOV_DEG = 2.0

    /**
     * @param turnDeg    signed azimuth turn in (-180, 180]; + = turn right (CW), - = left
     * @param tiltDeg    altitude change needed; + = tilt up, - = tilt down
     * @param separation true great-circle angular distance to the target (deg, >= 0)
     * @param onTarget   separation <= fov
     */
    data class Guidance(
        val turnDeg: Double,
        val tiltDeg: Double,
        val separation: Double,
        val onTarget: Boolean,
    )

    /** Shortest signed azimuth turn in (-180, 180]; + = right (clockwise), - = left. */
    fun signedTurn(currentAz: Double, targetAz: Double): Double =
        ((targetAz - currentAz + 540.0) % 360.0) - 180.0

    /** Great-circle angular separation (deg) between two horizontal directions. */
    fun separation(currentAlt: Double, currentAz: Double, targetAlt: Double, targetAz: Double): Double {
        val a1 = currentAlt * RAD
        val a2 = targetAlt * RAD
        val dAz = (targetAz - currentAz) * RAD
        val c = sin(a1) * sin(a2) + cos(a1) * cos(a2) * cos(dAz)
        return acos(c.coerceIn(-1.0, 1.0)) * DEG
    }

    /** Full guidance from the phone's current pointing to the target. */
    fun guide(
        current: AstroEngine.HorizCoords,
        target: AstroEngine.HorizCoords,
        fovDeg: Double = DEFAULT_FOV_DEG,
    ): Guidance {
        val sep = separation(current.altitude, current.azimuth, target.altitude, target.azimuth)
        return Guidance(
            turnDeg = signedTurn(current.azimuth, target.azimuth),
            tiltDeg = target.altitude - current.altitude,
            separation = sep,
            onTarget = sep <= fovDeg,
        )
    }

    /** Human-readable cue, e.g. "On target" or "Turn 12° right, tilt 8° up". */
    fun cue(g: Guidance): String {
        if (g.onTarget) return "On target"
        val turn = if (g.turnDeg >= 0) "${abs(g.turnDeg).roundToInt()}° right" else "${abs(g.turnDeg).roundToInt()}° left"
        val tilt = if (g.tiltDeg >= 0) "${abs(g.tiltDeg).roundToInt()}° up" else "${abs(g.tiltDeg).roundToInt()}° down"
        return "Turn $turn, tilt $tilt"
    }
}
