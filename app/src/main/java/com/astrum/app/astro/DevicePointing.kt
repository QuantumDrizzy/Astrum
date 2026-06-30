package com.astrum.app.astro
import com.quantumdrizzy.astro.AstroEngine

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Converts the phone's orientation into the sky direction its **back camera**
 * points at — the input push-to needs (feeds [PushTo.guide] as "current").
 *
 * Why project an axis instead of using SensorManager.getOrientation():
 * getOrientation returns yaw/pitch/roll, which hits **gimbal lock when the
 * phone points up at the sky** (pitch ≈ ±90°) — exactly the push-to pose. So
 * we take the device→world rotation matrix R (from
 * `SensorManager.getRotationMatrixFromVector`) and rotate the back-camera axis
 * (device −Z) into the world ENU frame, then read (alt, az) off that vector.
 * This is singularity-free for any pose.
 *
 * Coordinate frames:
 *  - World (Android `getRotationMatrix` convention): X = East, Y = North, Z = Up.
 *  - Device: X = right, Y = top edge, Z = out of the screen toward the user, so
 *    the back camera points along device −Z = (0, 0, −1).
 *  - `worldVec = R · deviceVec`, R row-major length-9: [r00 r01 r02 r10 r11 r12 r20 r21 r22].
 *
 * The (alt, az) returned matches [AstroEngine.HorizCoords]: azimuth from North,
 * clockwise (N=0, E=90); altitude above the horizon.
 *
 * NOTE: which physical axis the user "aims" (back camera −Z vs. the top edge) is
 * a UX choice that must be confirmed on a real device; −Z is the camera-AR
 * convention. The math below is axis-correct for −Z; if aiming feels rotated on
 * the phone, that's the axis/remap choice, not this projection.
 */
object DevicePointing {

    /** Where the back camera points, in horizontal coords. */
    fun fromRotationMatrix(r: FloatArray): AstroEngine.HorizCoords {
        require(r.size >= 9) { "rotation matrix must have at least 9 elements" }
        // back camera = device −Z = (0,0,−1); worldVec = R·(0,0,−1) = −(3rd column of R)
        val east = -r[2].toDouble()
        val north = -r[5].toDouble()
        val up = -r[8].toDouble()
        return horizFromEnu(east, north, up)
    }

    /** (alt, az) from an East/North/Up pointing vector (need not be unit length). */
    fun horizFromEnu(east: Double, north: Double, up: Double): AstroEngine.HorizCoords {
        val azimuth = ((Math.toDegrees(atan2(east, north)) % 360.0) + 360.0) % 360.0
        val altitude = Math.toDegrees(atan2(up, hypot(east, north)))
        return AstroEngine.HorizCoords(altitude, azimuth)
    }
}
