# Astrum — a planetarium that points your manual mount 🔭

A native Android planetarium for the observer with a **manual mount and no GoTo**. It computes
where every object is right now — and then, with **push-to guidance**, tells you exactly how far
to turn and tilt your scope to land on it. The sky math is validated against **astropy to within
0.7°**, not asserted.

Astrum is part of an astronomy pillar — **Astrum** (point) · **Skytime** (plan when/where to go) ·
**PhotoSharp** (process the capture) — over one shared, validated ephemeris engine.

## What it does

- **Push-to (the "Localizar" tab).** Hold the phone against the tube; Astrum reads the device
  orientation, projects it to alt/az, and guides you — *turn 12° right, tilt 8° up* — onto the
  target. The math that GoTo mounts do in hardware, on a manual 114EQ.
- **Where things are, now.** Real-time **altitude/azimuth, rise/set and transit** for any object,
  from local sidereal time and your GPS position (`AstroEngine`).
- **Sun & Moon.** Position, **phase**, illumination, and the full twilight ladder — civil,
  nautical, astronomical dawn/dusk (`SolarCalc`, `LunarCalc`).
- **Planets.** Orbital-mechanics positions for the seven major planets (`PlanetCalc`).
- **Catalog.** The **110 Messier objects + 35 brightest stars**, searchable and filterable, each
  with live altitude and a visibility readout.
- **Made for the dark.** A **night mode** (deep red on black) preserves your dark adaptation at the
  eyepiece; custom canvas views render a twinkling star field and the Moon's current phase.

## Validated, not asserted

The ephemeris is cross-checked against reference implementations — every claim maps to a check in
[`validation/`](validation/) (run with Python):

| Check | Result |
|-------|--------|
| `validate_astroengine.py` — alt/az vs **astropy** | **max error 0.73°** (tolerance 1.0°) ✓ |
| `validate_pointing.py` — device-orientation → alt/az projection | all cases ✓ |
| `validate_pushto.py` — turn / tilt / angular-separation math | all cases ✓ |

So the push-to guidance rests on math that has been checked against the gold standard, to better
than a degree — comfortably inside an eyepiece's true field of view.

## Build & run

- **Android 8.0+ (API 26)**, Android Studio, Kotlin.
- Open the project, connect a phone, Run. Grant location (for your sky) the first time.

## Stack

Kotlin · Android Canvas API + Fragments · `FusedLocationProviderClient` with Kotlin Flow ·
dedicated calculation modules (`astro/`) with a Python validation harness.

## License

MIT © QuantumDrizzy — see [LICENSE](LICENSE).
