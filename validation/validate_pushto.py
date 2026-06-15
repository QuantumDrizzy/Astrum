"""Validation of the push-to guidance math (mirrors astro/PushTo.kt).

Given where the phone is currently pointing (az, alt) and a target (az, alt),
push-to computes: the signed turn (left/right, with azimuth wraparound), the tilt
(up/down), and the true great-circle angular separation on the sky — plus an
"on target" test against a field-of-view. This is the algorithm behind the
"turn N° / tilt M°" guidance; the AR/sensor UI that feeds it lives on-device.

Pure math, validated here; PushTo.kt is the identical algorithm in Kotlin.

Run:  python validation/validate_pushto.py   (exit 0 = pass)
"""

from __future__ import annotations

import math
import sys

RAD = math.pi / 180.0
DEG = 180.0 / math.pi


def signed_turn(cur_az: float, tgt_az: float) -> float:
    """Shortest signed azimuth turn in (-180, 180]; + = right (CW), - = left."""
    return ((tgt_az - cur_az + 540.0) % 360.0) - 180.0


def separation(cur_az, cur_alt, tgt_az, tgt_alt) -> float:
    """Great-circle angular separation (deg) on the celestial sphere."""
    a1, a2 = cur_alt * RAD, tgt_alt * RAD
    daz = (tgt_az - cur_az) * RAD
    c = math.sin(a1) * math.sin(a2) + math.cos(a1) * math.cos(a2) * math.cos(daz)
    return math.acos(max(-1.0, min(1.0, c))) * DEG


def guidance(cur_az, cur_alt, tgt_az, tgt_alt, fov_deg=2.0):
    return {
        "turn": signed_turn(cur_az, tgt_az),       # + right / - left
        "tilt": tgt_alt - cur_alt,                 # + up / - down
        "separation": separation(cur_az, cur_alt, tgt_az, tgt_alt),
        "on_target": separation(cur_az, cur_alt, tgt_az, tgt_alt) <= fov_deg,
    }


def main() -> int:
    ok = True

    def check(name, cond):
        nonlocal ok
        print(f"  {'PASS' if cond else 'FAIL'}  {name}")
        ok = ok and cond

    # 1. identical pointing -> zero everything, on target
    g = guidance(120, 40, 120, 40)
    # acos near 1 amplifies float error: sin^2+cos^2 rounds to ~1, acos(~1)~1e-8 deg.
    # Degrees-level guidance is unaffected; 1e-6 deg is the right bar, not 1e-9.
    check("identical -> sep~0, on_target", abs(g["separation"]) < 1e-6 and g["on_target"] and abs(g["turn"]) < 1e-9)

    # 2. azimuth wraparound: 350 -> 10 is +20 (right), not -340
    check("wraparound 350->10 = +20", abs(signed_turn(350, 10) - 20) < 1e-9)
    check("wraparound 10->350 = -20", abs(signed_turn(10, 350) + 20) < 1e-9)

    # 3. great-circle separation sanity
    check("sep (az0,alt0)->(az90,alt0) = 90", abs(separation(0, 0, 90, 0) - 90) < 1e-6)
    check("sep (alt0)->(alt90) = 90 any az", abs(separation(33, 0, 200, 90) - 90) < 1e-6)
    # along the same azimuth, separation equals the altitude difference
    check("sep along meridian = dalt", abs(separation(120, 20, 120, 55) - 35) < 1e-6)

    # 4. tilt sign
    check("tilt up positive", guidance(0, 10, 0, 25)["tilt"] == 15)
    check("tilt down negative", guidance(0, 40, 0, 30)["tilt"] == -10)

    # 5. on-target threshold (just inside / outside a 2° FOV, along the meridian)
    check("within FOV on_target", guidance(120, 40, 120, 41.5)["on_target"])
    check("outside FOV not on_target", not guidance(120, 40, 120, 43)["on_target"])

    print("PASS — push-to math correct." if ok else "FAIL — push-to math regression.")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
