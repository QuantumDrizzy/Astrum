"""Validation of DevicePointing's matrix -> (alt, az) projection.

DevicePointing.kt turns the phone's device->world rotation matrix into the sky
direction the back camera (device -Z) points at, by projecting that axis into the
world ENU frame (X=East, Y=North, Z=Up) and reading off (alt, az). That projection
is pure linear algebra (no Android), so we validate it here against hand-computed
poses. Acquiring the matrix on the phone is the only device-side part.

Run:  python validation/validate_pointing.py   (exit 0 = pass)
"""

from __future__ import annotations

import math
import sys

import numpy as np


# ── exact port of DevicePointing.kt ───────────────────────────────────────────
def horiz_from_enu(east, north, up):
    az = (math.degrees(math.atan2(east, north)) % 360.0 + 360.0) % 360.0
    alt = math.degrees(math.atan2(up, math.hypot(east, north)))
    return alt, az


def from_rotation_matrix(r):  # r: length-9 row-major device->world matrix
    # worldVec = R * (0,0,-1) = -(third column)
    return horiz_from_enu(-r[2], -r[5], -r[8])


# ── helpers to build valid device->world rotation matrices ────────────────────
def rot_with_camera_axis(target_enu):
    """Build a valid rotation matrix whose back-camera axis (-Z) points at target_enu.

    We only constrain the 3rd column (= -target_enu so that -col = target_enu);
    the other two columns are any orthonormal completion (alt/az don't depend on
    them — the projection reads only the 3rd column).
    """
    t = np.array(target_enu, dtype=float)
    t /= np.linalg.norm(t)
    col2 = -t  # 3rd column
    # complete to an orthonormal basis
    helper = np.array([1.0, 0.0, 0.0]) if abs(col2[0]) < 0.9 else np.array([0.0, 1.0, 0.0])
    col0 = np.cross(helper, col2)
    col0 /= np.linalg.norm(col0)
    col1 = np.cross(col2, col0)
    R = np.column_stack([col0, col1, col2])
    assert np.allclose(R @ R.T, np.eye(3), atol=1e-9), "not orthonormal"
    return R.flatten().tolist()  # row-major


def main() -> int:
    ok = True

    def check(name, cond):
        nonlocal ok
        print(f"  {'PASS' if cond else 'FAIL'}  {name}")
        ok = ok and cond

    # 1. ENU projection basics
    alt, az = horiz_from_enu(0, 1, 0)   # due North, horizontal
    check("North horizontal -> alt 0, az 0", abs(alt) < 1e-9 and abs(az) < 1e-9)
    alt, az = horiz_from_enu(1, 0, 0)   # due East, horizontal
    check("East horizontal -> az 90", abs(alt) < 1e-9 and abs(az - 90) < 1e-9)
    alt, az = horiz_from_enu(0, 0, 1)   # straight up
    check("Up -> alt 90 (zenith)", abs(alt - 90) < 1e-9)
    alt, az = horiz_from_enu(-1, 0, 0)  # due West
    check("West horizontal -> az 270", abs(az - 270) < 1e-9)

    # 2. matrix path: back camera pointing North at horizon
    alt, az = from_rotation_matrix(rot_with_camera_axis([0, 1, 0]))
    check("R: camera->North -> alt 0, az 0", abs(alt) < 1e-9 and abs(az) < 1e-9)

    # 3. matrix path: camera at zenith
    alt, az = from_rotation_matrix(rot_with_camera_axis([0, 0, 1]))
    check("R: camera->zenith -> alt 90", abs(alt - 90) < 1e-9)

    # 4. matrix path: camera 45 deg up toward East-North
    alt, az = from_rotation_matrix(rot_with_camera_axis([1, 1, math.sqrt(2)]))
    check("R: camera 45deg up, NE -> alt 45, az 45",
          abs(alt - 45) < 1e-6 and abs(az - 45) < 1e-6)

    # 5. identity matrix: phone flat on table, screen up -> camera points down
    alt, az = from_rotation_matrix([1, 0, 0, 0, 1, 0, 0, 0, 1])
    check("R=I (phone flat) -> camera points down (alt -90)", abs(alt + 90) < 1e-9)

    print("PASS — pointing projection correct." if ok else "FAIL — pointing regression.")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
