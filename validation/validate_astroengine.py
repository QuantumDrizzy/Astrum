"""Cross-language validation of Astrum's AstroEngine against astropy (gold ref).

Astrum's `AstroEngine.equatorialToHorizontal` (Kotlin) has no JVM tests. This
script ports its exact formulas (Julian Day, Local Sidereal Time, equatorial→
horizontal) to Python and checks them against **astropy** (a trusted astronomy
library) for real objects from real locations/times. It's the algorithm-level
correctness check the app's core was missing.

Run (needs astropy):  python validation/validate_astroengine.py
Exit code 0 = within tolerance, 1 = regression.

[KNOWN_LIMIT] AstroEngine treats catalog RA/Dec as-of-date directly — no
precession-to-date, nutation, aberration, or atmospheric refraction. The residual
vs astropy is therefore ~0.1–0.8° (dominated by precession over ~25 yr from J2000),
which is fine for visual push-to to a finderscope (FOV ~1–5°) but not arcsecond
astrometry. Adding precession-to-date is the single biggest accuracy win if a
future narrow-FOV GoTo mount needs it.
"""

from __future__ import annotations

import sys
from datetime import datetime, timezone

import numpy as np

try:
    import astropy.units as u
    from astropy.coordinates import AltAz, EarthLocation, SkyCoord
    from astropy.time import Time
except ImportError:
    print("astropy not installed — `pip install astropy` to run this validation.")
    sys.exit(0)

import warnings

warnings.filterwarnings("ignore")

RAD = np.pi / 180.0
DEG = 180.0 / np.pi
J2000 = 2451545.0
TOLERANCE_DEG = 1.0  # adequate-for-push-to bar; see [KNOWN_LIMIT] in the docstring


# ── exact port of AstroEngine.kt ────────────────────────────────────────────
def julian_day(dt: datetime) -> float:
    return dt.timestamp() * 1000.0 / 86400000.0 + 2440587.5


def lst_deg(dt: datetime, lng: float) -> float:
    jd = julian_day(dt)
    t = (jd - J2000) / 36525.0
    gmst = 280.46061837 + 360.98564736629 * (jd - J2000) + t * t * 0.000387933
    gmst = ((gmst % 360) + 360) % 360
    return ((gmst + lng) % 360 + 360) % 360


def equatorial_to_horizontal(ra_h, dec_d, dt, lat_d, lng_d):
    h = (((lst_deg(dt, lng_d) - ra_h * 15.0) % 360 + 360) % 360) * RAD
    lat, dec = lat_d * RAD, dec_d * RAD
    sin_alt = np.sin(lat) * np.sin(dec) + np.cos(lat) * np.cos(dec) * np.cos(h)
    alt = np.arcsin(np.clip(sin_alt, -1, 1)) * DEG
    cos_az = (np.sin(dec) - np.sin(lat) * sin_alt) / (np.cos(lat) * max(np.cos(alt * RAD), 1e-4))
    az = np.arccos(np.clip(cos_az, -1, 1)) * DEG
    if np.sin(h) > 0:
        az = 360.0 - az
    return alt, az


def astropy_altaz(ra_h, dec_d, dt, lat_d, lng_d):
    loc = EarthLocation(lat=lat_d * u.deg, lon=lng_d * u.deg, height=0 * u.m)
    c = SkyCoord(ra=ra_h * 15 * u.deg, dec=dec_d * u.deg, frame="icrs")
    aa = c.transform_to(AltAz(obstime=Time(dt), location=loc, pressure=0 * u.hPa))
    return aa.alt.deg, aa.az.deg


# (name, RA hours, Dec deg, lat, lng)
CASES = [
    ("Vega", 18.6156, 38.7837, 37.99, -1.13),
    ("Polaris", 2.5303, 89.2641, 37.99, -1.13),
    ("Betelgeuse", 5.9195, 7.407, 40.0, -3.0),
    ("Sirius", 6.7525, -16.716, 28.0, -16.0),
    ("Arcturus", 14.261, 19.182, 37.99, -1.13),
    ("Antares", 16.490, -26.432, 28.0, -16.0),
]
WHEN = datetime(2026, 6, 15, 22, 0, 0, tzinfo=timezone.utc)


def main() -> int:
    print(f"{'object':12}{'engine alt/az':>18}{'astropy alt/az':>18}{'dAlt':>8}{'dAz':>8}")
    worst = 0.0
    for name, ra, dec, lat, lng in CASES:
        ea, ez = equatorial_to_horizontal(ra, dec, WHEN, lat, lng)
        aa, az = astropy_altaz(ra, dec, WHEN, lat, lng)
        d_alt = abs(ea - aa)
        d_az = abs(((ez - az + 180) % 360) - 180)
        # azimuth is ill-defined near the zenith/horizon; only score it when up
        scored = max(d_alt, d_az if ea > 5 else 0.0)
        worst = max(worst, scored)
        print(f"{name:12}{ea:9.2f}/{ez:7.2f}{aa:9.2f}/{az:7.2f}{ea - aa:8.2f}{d_az:8.2f}")
    print(f"\nmax error (objects above horizon): {worst:.3f}°  (tolerance {TOLERANCE_DEG}°)")
    if worst <= TOLERANCE_DEG:
        print("PASS — AstroEngine matches astropy within the push-to tolerance.")
        return 0
    print("FAIL — AstroEngine deviates beyond tolerance; investigate.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
