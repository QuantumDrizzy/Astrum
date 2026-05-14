package com.astrum.app.astro

enum class ObjectType(val label: String) {
    GALAXY("Galaxia"),
    NEBULA("Nebulosa"),
    PLANETARY_NEBULA("Neb. Planetaria"),
    GLOBULAR("Cúmulo Globular"),
    OPEN_CLUSTER("Cúmulo Abierto"),
    STAR_CLOUD("Nube Estelar"),
    DOUBLE_STAR("Doble Estrella"),
    ASTERISM("Asterismo"),
    STAR("Estrella"),
    OTHER("Otro")
}

data class CatalogObject(
    val id: String,
    val raHours: Double,
    val decDeg: Double,
    val type: ObjectType,
    val magnitude: Double,
    val sizeArcmin: Double,
    val constellation: String,
    val commonName: String = "",
    // computed at runtime
    var altitude: Double = 0.0,
    var azimuth: Double = 0.0,
    var riseTime: String = "--:--",
    var setTime: String = "--:--",
    var transitTime: String = "--:--",
    var isCircumpolar: Boolean = false,
    var neverRises: Boolean = false
) {
    val displayName: String get() = if (commonName.isNotBlank()) "$id — $commonName" else id
    val subtitle: String get() = buildString {
        append(constellation)
        if (sizeArcmin > 0) append(" · ${sizeArcmin.toInt()}'")
        append(" · mag $magnitude")
    }

    /** Drawable resource ID for the type icon (vector). */
    val typeIconRes: Int get() = when (type) {
        ObjectType.GALAXY                    -> com.astrum.app.R.drawable.ic_type_galaxy
        ObjectType.GLOBULAR                  -> com.astrum.app.R.drawable.ic_type_globular
        ObjectType.OPEN_CLUSTER,
        ObjectType.STAR_CLOUD, ObjectType.ASTERISM -> com.astrum.app.R.drawable.ic_type_open_cluster
        ObjectType.NEBULA,
        ObjectType.PLANETARY_NEBULA         -> com.astrum.app.R.drawable.ic_type_nebula
        else                                 -> com.astrum.app.R.drawable.ic_type_other
    }

    /** Pre-computed set time in minutes from midnight (Int.MAX_VALUE = circumpolar / not computed). */
    var setMinutes: Int = Int.MAX_VALUE
}

object Catalog {

    // Complete Messier catalogue — 110 objects, no duplicates
    val MESSIER: List<CatalogObject> = listOf(
        // ── Nebulae & Remnants ───────────────────────────────────────────────
        CatalogObject("M1",   5.575,  22.017, ObjectType.NEBULA,            8.4,   7.0, "Tauro",         "Cangrejo"),
        CatalogObject("M8",  18.063, -24.383, ObjectType.NEBULA,            5.8,  60.0, "Sagitario",     "Laguna"),
        CatalogObject("M16", 18.313, -13.800, ObjectType.NEBULA,            6.4,   7.0, "Serpens",       "Águila"),
        CatalogObject("M17", 18.347, -16.183, ObjectType.NEBULA,            6.0,  11.0, "Sagitario",     "Omega"),
        CatalogObject("M20", 18.033, -23.033, ObjectType.NEBULA,            5.2,  28.0, "Sagitario",     "Trífida"),
        CatalogObject("M42",  5.588,  -5.391, ObjectType.NEBULA,            4.0,  65.0, "Orión",         "Gran Nebulosa"),
        CatalogObject("M43",  5.592,  -5.267, ObjectType.NEBULA,            9.0,  20.0, "Orión",         "Mairan"),
        CatalogObject("M78",  5.779,   0.067, ObjectType.NEBULA,            8.3,   8.0, "Orión"),
        // ── Planetary Nebulae ────────────────────────────────────────────────
        CatalogObject("M27", 19.994,  22.717, ObjectType.PLANETARY_NEBULA,  7.5,   8.0, "Vulpécula",     "Mancuerna"),
        CatalogObject("M57", 18.893,  33.033, ObjectType.PLANETARY_NEBULA,  8.8,   1.4, "Lyra",          "Anillo"),
        CatalogObject("M76",  1.706,  51.567, ObjectType.PLANETARY_NEBULA, 10.1,   2.7, "Perseo",        "Mariposa"),
        CatalogObject("M97", 11.247,  55.017, ObjectType.PLANETARY_NEBULA,  9.9,   3.4, "Osa Mayor",     "Búho"),
        // ── Globular Clusters ────────────────────────────────────────────────
        CatalogObject("M2",  21.558,  -0.823, ObjectType.GLOBULAR,          6.5,  16.0, "Acuario"),
        CatalogObject("M3",  13.703,  28.377, ObjectType.GLOBULAR,          6.2,  18.0, "Can Venatici"),
        CatalogObject("M4",  16.393, -26.532, ObjectType.GLOBULAR,          5.9,  36.0, "Escorpio"),
        CatalogObject("M5",  15.310,   2.082, ObjectType.GLOBULAR,          5.7,  23.0, "Serpens"),
        CatalogObject("M9",  17.320, -18.517, ObjectType.GLOBULAR,          7.7,   9.0, "Ofiuco"),
        CatalogObject("M10", 16.952,  -4.100, ObjectType.GLOBULAR,          6.6,  20.0, "Ofiuco"),
        CatalogObject("M12", 16.787,  -1.950, ObjectType.GLOBULAR,          6.7,  16.0, "Ofiuco"),
        CatalogObject("M13", 16.695,  36.461, ObjectType.GLOBULAR,          5.8,  20.0, "Hércules",      "Gran Cúmulo"),
        CatalogObject("M14", 17.627,  -3.250, ObjectType.GLOBULAR,          7.6,  11.0, "Ofiuco"),
        CatalogObject("M15", 21.500,  12.167, ObjectType.GLOBULAR,          6.2,  18.0, "Pegaso"),
        CatalogObject("M19", 17.044, -26.267, ObjectType.GLOBULAR,          6.8,  17.0, "Ofiuco"),
        CatalogObject("M22", 18.605, -23.900, ObjectType.GLOBULAR,          5.1,  32.0, "Sagitario"),
        CatalogObject("M28", 18.410, -24.867, ObjectType.GLOBULAR,          6.8,  11.0, "Sagitario"),
        CatalogObject("M30", 21.674, -23.183, ObjectType.GLOBULAR,          7.5,  12.0, "Capricornio"),
        CatalogObject("M53", 13.215,  18.168, ObjectType.GLOBULAR,          7.6,  13.0, "Cab. Berenice"),
        CatalogObject("M54", 18.918, -30.483, ObjectType.GLOBULAR,          7.6,   9.0, "Sagitario"),
        CatalogObject("M55", 19.667, -30.967, ObjectType.GLOBULAR,          6.3,  19.0, "Sagitario"),
        CatalogObject("M56", 19.277,  30.183, ObjectType.GLOBULAR,          8.3,   7.0, "Lyra"),
        CatalogObject("M62", 17.020, -30.117, ObjectType.GLOBULAR,          6.4,  15.0, "Ofiuco"),
        CatalogObject("M68", 12.657, -26.750, ObjectType.GLOBULAR,          7.8,  12.0, "Hidra"),
        CatalogObject("M69", 18.523, -32.350, ObjectType.GLOBULAR,          7.6,   7.0, "Sagitario"),
        CatalogObject("M70", 18.720, -32.300, ObjectType.GLOBULAR,          7.8,   8.0, "Sagitario"),
        CatalogObject("M71", 19.896,  18.783, ObjectType.GLOBULAR,          6.1,   7.0, "Sagitta"),
        CatalogObject("M72", 20.891, -12.533, ObjectType.GLOBULAR,          9.3,   6.0, "Acuario"),
        CatalogObject("M75", 20.101, -21.917, ObjectType.GLOBULAR,          8.5,   6.0, "Sagitario"),
        CatalogObject("M79",  5.402, -24.517, ObjectType.GLOBULAR,          7.7,   9.0, "Lepus"),
        CatalogObject("M80", 16.283, -22.967, ObjectType.GLOBULAR,          7.3,   9.0, "Escorpio"),
        CatalogObject("M92", 17.285,  43.133, ObjectType.GLOBULAR,          6.5,  14.0, "Hércules"),
        CatalogObject("M107",16.542, -13.050, ObjectType.GLOBULAR,          7.8,  13.0, "Ofiuco"),
        // ── Open Clusters ────────────────────────────────────────────────────
        CatalogObject("M6",  17.670, -32.217, ObjectType.OPEN_CLUSTER,      4.2,  25.0, "Escorpio",      "Mariposa"),
        CatalogObject("M7",  17.898, -34.817, ObjectType.OPEN_CLUSTER,      3.3,  80.0, "Escorpio",      "Ptolomeo"),
        CatalogObject("M11", 18.852,  -6.267, ObjectType.OPEN_CLUSTER,      5.8,  14.0, "Scutum",        "Pato Salvaje"),
        CatalogObject("M18", 18.333, -17.133, ObjectType.OPEN_CLUSTER,      6.9,   9.0, "Sagitario"),
        CatalogObject("M21", 18.078, -22.483, ObjectType.OPEN_CLUSTER,      5.9,  13.0, "Sagitario"),
        CatalogObject("M23", 17.947, -18.983, ObjectType.OPEN_CLUSTER,      5.5,  27.0, "Sagitario"),
        CatalogObject("M25", 18.527, -19.233, ObjectType.OPEN_CLUSTER,      4.6,  40.0, "Sagitario"),
        CatalogObject("M26", 18.755,  -9.400, ObjectType.OPEN_CLUSTER,      8.0,  15.0, "Scutum"),
        CatalogObject("M29", 20.399,  38.533, ObjectType.OPEN_CLUSTER,      7.1,   7.0, "Cisne"),
        CatalogObject("M34",  2.700,  42.783, ObjectType.OPEN_CLUSTER,      5.2,  35.0, "Perseo"),
        CatalogObject("M35",  6.148,  24.333, ObjectType.OPEN_CLUSTER,      5.1,  28.0, "Géminis"),
        CatalogObject("M36",  5.605,  34.133, ObjectType.OPEN_CLUSTER,      6.0,  12.0, "Auriga"),
        CatalogObject("M37",  5.873,  32.550, ObjectType.OPEN_CLUSTER,      5.6,  24.0, "Auriga"),
        CatalogObject("M38",  5.478,  35.833, ObjectType.OPEN_CLUSTER,      6.4,  21.0, "Auriga"),
        CatalogObject("M39", 21.532,  48.433, ObjectType.OPEN_CLUSTER,      4.6,  32.0, "Cisne"),
        CatalogObject("M41",  6.767, -20.733, ObjectType.OPEN_CLUSTER,      4.5,  38.0, "Can Mayor"),
        CatalogObject("M44",  8.673,  19.983, ObjectType.OPEN_CLUSTER,      3.1,  95.0, "Cáncer",        "Colmena"),
        CatalogObject("M45",  3.793,  24.117, ObjectType.OPEN_CLUSTER,      1.6, 110.0, "Tauro",         "Pléyades"),
        CatalogObject("M46",  7.697, -14.817, ObjectType.OPEN_CLUSTER,      6.1,  27.0, "Popa"),
        CatalogObject("M47",  7.610, -14.483, ObjectType.OPEN_CLUSTER,      4.4,  30.0, "Popa"),
        CatalogObject("M48",  8.233,  -5.717, ObjectType.OPEN_CLUSTER,      5.8,  54.0, "Hidra"),
        CatalogObject("M50",  7.053,  -8.333, ObjectType.OPEN_CLUSTER,      5.9,  16.0, "Unicornio"),
        CatalogObject("M52", 23.405,  61.600, ObjectType.OPEN_CLUSTER,      6.9,  13.0, "Casiopea"),
        CatalogObject("M67",  8.858,  11.817, ObjectType.OPEN_CLUSTER,      6.1,  30.0, "Cáncer"),
        CatalogObject("M93",  7.743, -23.850, ObjectType.OPEN_CLUSTER,      6.2,  22.0, "Popa"),
        CatalogObject("M103", 1.557,  60.650, ObjectType.OPEN_CLUSTER,      7.4,   6.0, "Casiopea"),
        // ── Galaxies ─────────────────────────────────────────────────────────
        CatalogObject("M31",  0.712,  41.269, ObjectType.GALAXY,            3.4, 178.0, "Andrómeda",     "Andrómeda"),
        CatalogObject("M32",  0.711,  40.867, ObjectType.GALAXY,            8.7,   8.0, "Andrómeda"),
        CatalogObject("M33",  1.564,  30.660, ObjectType.GALAXY,            5.7,  70.0, "Triángulo",     "Triángulo"),
        CatalogObject("M49", 12.497,   8.000, ObjectType.GALAXY,            8.4,   9.0, "Virgo"),
        CatalogObject("M51", 13.497,  47.195, ObjectType.GALAXY,            8.4,  11.0, "Can Venatici",  "Remolino"),
        CatalogObject("M58", 12.629,  11.817, ObjectType.GALAXY,            9.7,   5.5, "Virgo"),
        CatalogObject("M59", 12.700,  11.650, ObjectType.GALAXY,           10.6,   5.0, "Virgo"),
        CatalogObject("M60", 12.727,  11.550, ObjectType.GALAXY,            8.8,   7.0, "Virgo"),
        CatalogObject("M61", 12.366,   4.467, ObjectType.GALAXY,            9.7,   6.0, "Virgo"),
        CatalogObject("M63", 13.263,  42.033, ObjectType.GALAXY,            8.6,  13.0, "Can Venatici",  "Girasol"),
        CatalogObject("M64", 12.942,  21.683, ObjectType.GALAXY,            8.5,  10.0, "Cab. Berenice", "Ojo Negro"),
        CatalogObject("M65", 11.315,  13.100, ObjectType.GALAXY,            9.3,  10.0, "Leo"),
        CatalogObject("M66", 11.337,  12.983, ObjectType.GALAXY,            8.9,   9.0, "Leo"),
        CatalogObject("M74",  1.612,  15.783, ObjectType.GALAXY,            9.4,  11.0, "Piscis"),
        CatalogObject("M77",  2.712,  -0.017, ObjectType.GALAXY,            8.9,   8.0, "Cetus"),
        CatalogObject("M81",  9.926,  69.067, ObjectType.GALAXY,            6.9,  26.0, "Osa Mayor",     "Bode"),
        CatalogObject("M82",  9.926,  69.683, ObjectType.GALAXY,            8.4,  11.0, "Osa Mayor",     "Cigarro"),
        CatalogObject("M83", 13.617, -29.867, ObjectType.GALAXY,            7.6,  13.0, "Hidra",         "Molinillo Sur"),
        CatalogObject("M84", 12.422,  12.883, ObjectType.GALAXY,            9.1,   5.0, "Virgo"),
        CatalogObject("M85", 12.422,  18.183, ObjectType.GALAXY,            9.2,   7.0, "Cab. Berenice"),
        CatalogObject("M86", 12.437,  12.950, ObjectType.GALAXY,            8.9,   7.0, "Virgo"),
        CatalogObject("M87", 12.514,  12.400, ObjectType.GALAXY,            8.6,   8.0, "Virgo",         "Virgo A"),
        CatalogObject("M88", 12.533,  14.417, ObjectType.GALAXY,            9.6,   7.0, "Cab. Berenice"),
        CatalogObject("M89", 12.592,  12.550, ObjectType.GALAXY,           10.7,   4.0, "Virgo"),
        CatalogObject("M90", 12.614,  13.150, ObjectType.GALAXY,            9.5,   9.0, "Virgo"),
        CatalogObject("M91", 12.591,  14.500, ObjectType.GALAXY,           10.2,   5.0, "Cab. Berenice"),
        CatalogObject("M94", 12.851,  41.117, ObjectType.GALAXY,            8.2,  11.0, "Can Venatici"),
        CatalogObject("M95", 10.730,  11.700, ObjectType.GALAXY,            9.7,   7.0, "Leo"),
        CatalogObject("M96", 10.747,  11.817, ObjectType.GALAXY,            9.2,   7.0, "Leo"),
        CatalogObject("M98", 12.231,  14.900, ObjectType.GALAXY,           10.1,   9.0, "Cab. Berenice"),
        CatalogObject("M99", 12.318,  14.417, ObjectType.GALAXY,            9.9,   5.0, "Cab. Berenice"),
        CatalogObject("M100",12.382,  15.817, ObjectType.GALAXY,           10.1,   7.0, "Cab. Berenice"),
        CatalogObject("M101",14.053,  54.349, ObjectType.GALAXY,            7.9,  28.0, "Osa Mayor",     "Molinillo"),
        CatalogObject("M102",15.100,  55.767, ObjectType.GALAXY,           10.7,   5.0, "Dragón"),
        CatalogObject("M104",12.666, -11.617, ObjectType.GALAXY,            8.0,   9.0, "Virgo",         "Sombrero"),
        CatalogObject("M105",10.797,  12.583, ObjectType.GALAXY,            9.8,   4.0, "Leo"),
        CatalogObject("M106",12.316,  47.300, ObjectType.GALAXY,            8.4,  19.0, "Can Venatici"),
        CatalogObject("M108",11.192,  55.683, ObjectType.GALAXY,           10.7,   8.0, "Osa Mayor"),
        CatalogObject("M109",11.958,  53.383, ObjectType.GALAXY,           10.6,   7.0, "Osa Mayor"),
        CatalogObject("M110",  0.673,  41.683, ObjectType.GALAXY,            8.5,  17.0, "Andrómeda"),
        // ── Star Clouds ─────────────────────────────────────────────────────
        CatalogObject("M24", 18.282, -18.550, ObjectType.STAR_CLOUD,        4.6,  90.0, "Sagitario",     "Nube Sagitario"),
    )

    data class BrightStar(
        val name: String,
        val raHours: Double,
        val decDeg: Double,
        val magnitude: Double,
        val spectralType: String,
        val constellation: String,
        var altitude: Double  = 0.0,
        var azimuth: Double   = 0.0,
        var setMinutes: Int   = Int.MAX_VALUE
    )

    val STARS: List<BrightStar> = listOf(
        BrightStar("Sirius",        6.752, -16.713, -1.46, "A1V",    "Can Mayor"),
        BrightStar("Canopus",       6.400, -52.695, -0.74, "F0II",   "Carina"),
        BrightStar("Arturo",       14.261,  19.182, -0.05, "K0III",  "Boyero"),
        BrightStar("Vega",         18.615,  38.784,  0.03, "A0V",    "Lyra"),
        BrightStar("Capella",       5.278,  45.998,  0.08, "G8III",  "Auriga"),
        BrightStar("Rigel",         5.242,  -8.202,  0.13, "B8Ia",   "Orión"),
        BrightStar("Proción",       7.655,   5.225,  0.38, "F5IV",   "Can Menor"),
        BrightStar("Betelgeuse",    5.919,   7.407,  0.50, "M2Ib",   "Orión"),
        BrightStar("Altair",       19.846,   8.868,  0.77, "A7V",    "Águila"),
        BrightStar("Aldebaran",     4.598,  16.509,  0.87, "K5III",  "Tauro"),
        BrightStar("Acrux",        12.443, -63.099,  0.77, "B0.5IV", "Cruz del Sur"),
        BrightStar("Antares",      16.490, -26.432,  1.06, "M1Ib",   "Escorpio"),
        BrightStar("Espiga",       13.420, -11.161,  1.04, "B1III",  "Virgo"),
        BrightStar("Pollux",        7.755,  28.026,  1.15, "K0III",  "Géminis"),
        BrightStar("Fomalhaut",    22.961, -29.622,  1.16, "A3V",    "Piscis Austral"),
        BrightStar("Deneb",        20.691,  45.280,  1.25, "A2Ia",   "Cisne"),
        BrightStar("Mimosa",       12.795, -59.689,  1.25, "B0.5III","Cruz del Sur"),
        BrightStar("Regulus",      10.139,  11.967,  1.36, "B7V",    "Leo"),
        BrightStar("Adhara",        6.977, -28.972,  1.50, "B2Ia",   "Can Mayor"),
        BrightStar("Castor",        7.577,  31.888,  1.58, "A2V",    "Géminis"),
        BrightStar("Shaula",       17.560, -37.103,  1.62, "B1IV",   "Escorpio"),
        BrightStar("Bellatrix",     5.419,   6.349,  1.64, "B2III",  "Orión"),
        BrightStar("Elnath",        5.438,  28.608,  1.65, "B7III",  "Tauro"),
        BrightStar("Miaplacidus",   9.220, -69.717,  1.67, "A1III",  "Carina"),
        BrightStar("Alnilam",       5.604,  -1.202,  1.70, "B0Ia",   "Orión"),
        BrightStar("Alnitak",       5.679,  -1.943,  1.77, "O9Ib",   "Orión"),
        BrightStar("Kaus Austr.",  18.403, -34.385,  1.79, "B9III",  "Sagitario"),
        BrightStar("Dubhe",        11.062,  61.751,  1.81, "K0III",  "Osa Mayor"),
        BrightStar("Mirfak",        3.405,  49.861,  1.79, "F5Ib",   "Perseo"),
        BrightStar("Alkaid",       13.792,  49.314,  1.85, "B3V",    "Osa Mayor"),
        BrightStar("Avior",         8.375, -59.510,  1.86, "K3III",  "Carina"),
        BrightStar("Sargas",       17.622, -42.998,  1.87, "F0II",   "Escorpio"),
        BrightStar("Menkent",      14.111, -36.370,  2.06, "K0III",  "Centauro"),
        BrightStar("Mirzam",        6.378, -17.956,  1.98, "B1II",   "Can Mayor"),
        BrightStar("Hamal",         2.120,  23.463,  2.00, "K2III",  "Aries"),
        BrightStar("Alpheratz",     0.139,  29.090,  2.06, "B9p",    "Andrómeda"),
        BrightStar("Schedar",       0.675,  56.537,  2.24, "K0III",  "Casiopea"),
        BrightStar("Mirach",        1.162,  35.621,  2.06, "M0III",  "Andrómeda"),
        BrightStar("Almach",        2.065,  42.330,  2.10, "K3II",   "Andrómeda"),
        BrightStar("Rasalhague",   17.583,  12.560,  2.08, "A5III",  "Ofiuco"),
        BrightStar("Enif",         21.736,   9.875,  2.39, "K2Ib",   "Pegaso"),
        BrightStar("Alphecca",     15.578,  26.715,  2.23, "A0V",    "Corona Bor."),
        BrightStar("Denebola",     11.817,  14.572,  2.14, "A3V",    "Leo"),
        BrightStar("Dschubba",     16.005, -22.622,  2.32, "B0III",  "Escorpio"),
        BrightStar("Alderamin",    21.310,  62.585,  2.44, "A7IV",   "Cefeo"),
        BrightStar("Etamin",       17.943,  51.489,  2.23, "K5III",  "Dragón"),
        BrightStar("Caph",          0.153,  59.150,  2.27, "F2III",  "Casiopea"),
        BrightStar("Sabik",        17.173,  -15.724, 2.43, "A2V",    "Ofiuco"),
        BrightStar("Phecda",       11.897,  53.695,  2.44, "A0Ve",   "Osa Mayor"),
        BrightStar("Merak",        11.031,  56.383,  2.37, "A1V",    "Osa Mayor"),
    )
}
