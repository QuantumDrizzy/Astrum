# ASTRUM — Observatorio Personal 🔭

App Android nativa en Kotlin. GPS real, canvas animado, mecánica orbital, catálogo Messier completo.

## Abrir en Android Studio

1. Extrae el ZIP
2. Android Studio → **File → Open** → selecciona la carpeta `astrum-native`
3. Espera a que Gradle sincronice (~2 min primera vez)
4. Si pide descargar el SDK: acepta
5. **Build → Make Project** para verificar que compila
6. **Run** en tu Pixel o **Build → Generate Signed APK**

> ⚠️ Si Android Studio dice "Gradle wrapper not found": ve a  
> **File → Project Structure → Project → Gradle Version** → escribe `8.4` → OK

## Estructura

```
astro/
  AstroEngine.kt    — Tiempo sidéreo, altitud/azimut, rise/set
  SolarCalc.kt      — Sol: posición, amanecer/ocaso, crepúsculos, hora dorada
  LunarCalc.kt      — Luna: fase, iluminación, posición, rise/set
  PlanetCalc.kt     — Mecánica orbital real para los 7 planetas
  Catalog.kt        — 110 objetos Messier + 35 estrellas brillantes

ui/
  NowFragment       — Qué hay visible ahora, con brújula y filtros
  PlanetsFragment   — Planetas en tiempo real
  CatalogFragment   — Messier completo con búsqueda y filtros
  SolarFragment     — Sol y luna con todos los datos

views/
  StarFieldView     — Fondo animado: estrellas parpadeantes + meteoros
  MoonView          — Fase lunar dibujada en Canvas

location/
  LocationHelper    — FusedLocationProviderClient con Kotlin Flow
```

## Permisos

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — GPS en tiempo real

## Requisitos

- Android 8.0+ (API 26)
- Android Studio Hedgehog o superior
- Kotlin 1.9+
