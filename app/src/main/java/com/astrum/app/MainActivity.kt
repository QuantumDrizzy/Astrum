package com.astrum.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.astrum.app.astro.AstroEngine
import com.astrum.app.astro.LunarCalc
import com.astrum.app.astro.SolarCalc
import com.astrum.app.databinding.ActivityMainBinding
import com.astrum.app.location.AstroLocation
import com.astrum.app.location.LocationHelper
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    private lateinit var locationHelper: LocationHelper
    private val clockHandler = Handler(Looper.getMainLooper())
    private val gpsTimeoutHandler = Handler(Looper.getMainLooper())
    private var gpsTimedOut = false

    // Shared state — keyed maps so fragments can register AND unregister
    var currentLocation: AstroLocation? = null
    val locationListeners  = mutableMapOf<String, (AstroLocation) -> Unit>()
    val nightModeListeners = mutableMapOf<String, () -> Unit>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) startLocation()
        else setGpsState(GpsState.ERROR, "Permiso denegado — ve a Ajustes")
    }

    enum class GpsState { SEARCHING, ACTIVE, ERROR }

    // ── Lifecycle ────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NightModeManager.init(this)
        makeEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNav.setupWithNavController(host.navController)

        binding.btnNightMode.setOnClickListener {
            val isNight = NightModeManager.toggle()
            applyNightModeToHeader(isNight)
            nightModeListeners.values.toList().forEach { it() }
        }

        applyNightModeToHeader(NightModeManager.isNightMode)
        startClock()

        locationHelper = LocationHelper(this)
        checkPermissionsAndStart()
    }

    override fun onResume() {
        super.onResume()
        applyNightModeToHeader(NightModeManager.isNightMode)
        if (locationHelper.hasPermission()) locationHelper.start()
    }

    override fun onPause() {
        super.onPause()
        locationHelper.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacksAndMessages(null)
        gpsTimeoutHandler.removeCallbacksAndMessages(null)
    }

    // ── Night mode ───────────────────────────────────────────────────────
    fun applyNightModeToHeader(isNight: Boolean) {
        val red    = NightModeManager.RED
        val redDim = NightModeManager.RED_DIM
        val bg     = if (isNight) Color.BLACK else ContextCompat.getColor(this, R.color.bg_deep)

        binding.rootLayout.setBackgroundColor(bg)
        binding.appHeader.setBackgroundColor(bg)
        binding.bottomNav.setBackgroundColor(
            if (isNight) Color.BLACK else ContextCompat.getColor(this, R.color.nav_bg)
        )

        val navTint = ColorStateList.valueOf(
            if (isNight) red else ContextCompat.getColor(this, R.color.text_dim)
        )
        binding.bottomNav.itemIconTintList = navTint
        binding.bottomNav.itemTextColor = navTint

        // Text colours
        fun tv(isNightV: Boolean, nightC: Int, normResId: Int) =
            if (isNightV) nightC else ContextCompat.getColor(this, normResId)

        binding.tvAppTitle.setTextColor(tv(isNight, red,    R.color.text_primary))
        binding.tvAppSubtitle.setTextColor(tv(isNight, redDim, R.color.text_dim))
        binding.clH.setTextColor(tv(isNight, red,    R.color.text_primary))
        binding.clSep1.setTextColor(tv(isNight, redDim, R.color.text_dim))
        binding.clM.setTextColor(tv(isNight, red,    R.color.text_primary))
        binding.clSep2.setTextColor(tv(isNight, red,    R.color.amber))
        binding.clS.setTextColor(tv(isNight, red,    R.color.amber))
        binding.clDate.setTextColor(tv(isNight, redDim, R.color.text_dim))
        binding.kpiSol.setTextColor(tv(isNight, red,    R.color.amber))
        binding.kpiLuna.setTextColor(tv(isNight, red,    R.color.amber))
        binding.kpiLst.setTextColor(tv(isNight, red,    R.color.amber))
        binding.tvKpiLabelSol.setTextColor(tv(isNight, redDim, R.color.text_dim))
        binding.tvKpiLabelLuna.setTextColor(tv(isNight, redDim, R.color.text_dim))
        binding.tvKpiLabelLst.setTextColor(tv(isNight, redDim, R.color.text_dim))
        binding.gpsText.setTextColor(tv(isNight, redDim, R.color.text_dim))
        binding.btnNightMode.text = if (isNight) "☀" else "🌙"
        binding.btnNightMode.setTextColor(tv(isNight, red, R.color.text_secondary))
    }

    // ── Edge-to-edge ─────────────────────────────────────────────────────
    private fun makeEdgeToEdge() {
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.parseColor("#0a0e18")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    // ── GPS ──────────────────────────────────────────────────────────────
    private fun checkPermissionsAndStart() {
        val hasFine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) startLocation()
        else permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun startLocation() {
        setGpsState(GpsState.SEARCHING, "buscando señal GPS...")
        locationHelper.onLocation = { loc ->
            runOnUiThread {
                gpsTimeoutHandler.removeCallbacksAndMessages(null)
                gpsTimedOut = false
                currentLocation = loc
                updateGpsDisplay(loc)
                locationListeners.values.toList().forEach { it(loc) }
            }
        }
        locationHelper.start()

        gpsTimeoutHandler.postDelayed({
            if (currentLocation == null) {
                gpsTimedOut = true
                val lastKnown = locationHelper.lastKnown()
                if (lastKnown != null) {
                    currentLocation = lastKnown
                    runOnUiThread {
                        updateGpsDisplay(lastKnown, stale = true)
                        locationListeners.values.toList().forEach { it(lastKnown) }
                    }
                } else {
                    val defaultLoc = AstroLocation(40.4168, -3.7038, null, "DEFAULT")
                    currentLocation = defaultLoc
                    runOnUiThread {
                        setGpsState(GpsState.ERROR, "Sin GPS — usando Madrid")
                        locationListeners.values.toList().forEach { it(defaultLoc) }
                    }
                }
            }
        }, 30_000L)
    }

    private fun updateGpsDisplay(loc: AstroLocation, stale: Boolean = false) {
        val acc    = if (loc.accuracy != null && loc.accuracy < 500f) " ±${loc.accuracy.toInt()}m" else ""
        val lat    = "${"%.4f".format(Math.abs(loc.latitude))}°${if (loc.latitude >= 0) "N" else "S"}"
        val lng    = "${"%.4f".format(Math.abs(loc.longitude))}°${if (loc.longitude < 0) "W" else "E"}"
        val prefix = if (stale) "★ " else ""
        setGpsState(if (stale) GpsState.ERROR else GpsState.ACTIVE, "$prefix$lat  $lng$acc")
    }

    fun setGpsState(state: GpsState, text: String) {
        binding.gpsText.text = text
        val drawableRes = when (state) {
            GpsState.SEARCHING -> R.drawable.led_searching
            GpsState.ACTIVE    -> R.drawable.led_active
            GpsState.ERROR     -> R.drawable.led_error
        }
        binding.gpsLed.setBackgroundResource(drawableRes)
        if (state == GpsState.SEARCHING) blinkLed()
        else { binding.gpsLed.animate().cancel(); binding.gpsLed.alpha = 1f }
    }

    private fun blinkLed() {
        binding.gpsLed.animate()
            .alpha(0.2f).setDuration(600)
            .withEndAction {
                if (currentLocation == null) {
                    binding.gpsLed.animate().alpha(1f).setDuration(600)
                        .withEndAction { blinkLed() }.start()
                }
            }.start()
    }

    // ── Clock ─────────────────────────────────────────────────────────────
    private val clockRunnable = object : Runnable {
        override fun run() { tick(); clockHandler.postDelayed(this, 1000L) }
    }

    private fun startClock() { clockHandler.post(clockRunnable) }

    private fun tick() {
        val now = Date()
        val cal = Calendar.getInstance().apply { time = now }
        binding.clH.text = "%02d".format(cal.get(Calendar.HOUR_OF_DAY))
        binding.clM.text = "%02d".format(cal.get(Calendar.MINUTE))
        binding.clS.text = "%02d".format(cal.get(Calendar.SECOND))

        val days   = arrayOf("Dom","Lun","Mar","Mié","Jue","Vie","Sáb")
        val months = arrayOf("Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic")
        binding.clDate.text = "${days[cal.get(Calendar.DAY_OF_WEEK) - 1]} " +
            "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"

        if (cal.get(Calendar.SECOND) % 15 == 0) updateKpis(now)
    }

    private fun updateKpis(now: Date) {
        val loc = currentLocation ?: return
        try {
            val sun  = SolarCalc.position(now, loc.latitude, loc.longitude)
            val moon = LunarCalc.illumination(now)
            binding.kpiSol.text  = "${"%.1f".format(sun.altitude)}°"
            binding.kpiLuna.text = "${(moon.fraction * 100).toInt()}%"
            binding.kpiLst.text  = AstroEngine.lstString(now, loc.longitude).take(5)
        } catch (_: Exception) {}
    }
}
