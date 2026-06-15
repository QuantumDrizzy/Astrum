# AstroEngine — validation against astropy

`AstroEngine.equatorialToHorizontal` (the alt-az transform that powers "where is
this object in my sky") had **no automated tests**. `validation/validate_astroengine.py`
ports its exact formulas and checks them against **astropy** (gold reference) for
real objects from real locations/times.

## Verdict (2026-06-15): correct, adequate for push-to.

Max error vs astropy across 6 objects (above the horizon): **0.733°** (typical
0.1–0.3°). Polaris altitude ≈ observer latitude ✓; azimuth convention (from North,
clockwise) matches ✓. This is why Astrum reliably finds planets in the field.

```
object        engine alt/az   astropy alt/az    dAlt    dAz
Vega           54.20/ 73.99    54.03/ 73.88     0.17   0.11
Polaris        37.28/  0.24    37.37/  0.10    -0.09   0.15
Arcturus       64.88/226.87    64.96/226.14    -0.08   0.73
Antares        28.78/150.15    28.55/149.82     0.23   0.33
...            max error (above horizon): 0.733°  → PASS (< 1.0°)
```

## [KNOWN_LIMIT] and the one improvement

AstroEngine uses catalog RA/Dec **as-of-date directly** — no precession-to-date,
nutation, aberration, or atmospheric refraction. The ~0.1–0.8° residual is
dominated by **precession** over ~25 years from J2000. That is **fine for visual
push-to** to a finderscope (FOV ~1–5°), but not arcsecond astrometry.

**The single biggest accuracy win**, if a future **narrow-FOV motorized/GoTo mount**
ever needs it: apply **precession-to-date** to the catalog coordinates before the
alt-az transform. (Refraction near the horizon is the next term.) Not needed for
the current Celestron 114EQ / phone-assist use.

## Next: the push-to UX (device-side)

The engine already knows *where* every object is (validated above). The remaining
"Stellarium-killer" is the **point-and-find UX**: fuse the phone's rotation-vector
sensor → current (alt, az) it's pointing at, then guide the user ("turn right N°,
tilt up M°") to the target's (alt, az), with an optional AR overlay. That code is
device-dependent (compass/gyro/camera) and must be validated on a phone, not in CI.
