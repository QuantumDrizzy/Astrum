package com.astrum.app.ui
import com.astrum.app.AppClock

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.astrum.app.MainActivity
import com.astrum.app.NightModeManager
import com.astrum.app.applyNightRecursive
import com.astrum.app.astro.*
import com.quantumdrizzy.astro.AstroEngine
import com.quantumdrizzy.astro.SolarCalc
import com.quantumdrizzy.astro.LunarCalc
import com.astrum.app.databinding.FragmentNowBinding
import com.astrum.app.location.AstroLocation
import kotlinx.coroutines.*
import java.util.*

class NowFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentNowBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NowAdapter
    private var currentLocation: AstroLocation? = null
    private var updateJob: Job? = null
    private var sensorManager: SensorManager? = null
    private var compassDeg: Float = 0f
    private var filterType: String = "ALL"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NowAdapter()
        binding.recyclerNow.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerNow.adapter = adapter

        // Filter chips
        binding.chipAll.setOnClickListener    { setFilter("ALL",     it) }
        binding.chipGalaxy.setOnClickListener { setFilter("Galaxia", it) }
        binding.chipNebula.setOnClickListener { setFilter("Nebulosa",it) }
        binding.chipCluster.setOnClickListener{ setFilter("Cumulo",  it) }
        binding.chipPlanet.setOnClickListener { setFilter("Planeta", it) }
        binding.chipAll.isSelected = true

        // Register location listener with unique key — will be removed in onDestroyView
        val activity = requireActivity() as MainActivity
        currentLocation = activity.currentLocation
        activity.locationListeners["NowFragment"] = { loc ->
            currentLocation = loc
            if (_binding != null) updateHeader()
        }
        activity.nightModeListeners["NowFragment"] = {
            applyNightMode(NightModeManager.isNightMode)
        }
        applyNightMode(NightModeManager.isNightMode)

        // Compass sensor
        sensorManager = requireContext().getSystemService(SensorManager::class.java) as? SensorManager

        startUpdates()
    }

    private fun setFilter(type: String, view: View) {
        filterType = type
        listOf(binding.chipAll, binding.chipGalaxy, binding.chipNebula,
               binding.chipCluster, binding.chipPlanet).forEach { it.isSelected = false }
        view.isSelected = true
        renderList()
    }

    private fun startUpdates() {
        // viewLifecycleOwner.lifecycleScope is cancelled automatically when onDestroyView fires
        updateJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                updateHeader()
                renderList()
                delay(10_000L)
            }
        }
    }

    private fun updateHeader() {
        val b = _binding ?: return
        val loc = currentLocation ?: return
        val now = AppClock.now()
        try {
            val sunPos  = SolarCalc.position(now, loc.latitude, loc.longitude)
            val moonIll = LunarCalc.illumination(now)

            val acc = if (loc.accuracy != null) " ±${loc.accuracy.toInt()}m" else ""
            b.tvCoords.text = "%.4f°%s  %.4f°%s%s".format(
                Math.abs(loc.latitude),  if (loc.latitude  >= 0) "N" else "S",
                Math.abs(loc.longitude), if (loc.longitude <  0) "W" else "E",
                acc
            )
            b.tvLst.text = "LST ${AstroEngine.lstString(now, loc.longitude)}"
            b.tvSkyInfo.text = buildString {
                append(when {
                    sunPos.altitude < -18 -> "Astronomical night"
                    sunPos.altitude < -12 -> "Nautical night"
                    sunPos.altitude < -6  -> "Twilight"
                    sunPos.altitude < 0   -> "Civil twilight"
                    else                  -> "Day"
                })
                append("  ·  Moon ${(moonIll.fraction * 100).toInt()}%")
            }

            val isDark = sunPos.altitude < -6
            val quality = if (isDark)
                maxOf(0.2, 1.0 - moonIll.fraction * 0.8)
            else
                maxOf(0.0, 0.4 - (maxOf(0.0, sunPos.altitude) / 90.0) * 0.4)
            b.skyQualityBar.progress = (quality * 100).toInt()

            if (compassDeg != 0f) b.compassNeedle.rotation = compassDeg
        } catch (_: Exception) {}
    }

    private fun renderList() {
        val b = _binding ?: return
        val loc = currentLocation ?: run {
            b.tvNoLocation.visibility = View.VISIBLE
            b.recyclerNow.visibility  = View.GONE
            return
        }
        b.tvNoLocation.visibility = View.GONE
        b.recyclerNow.visibility  = View.VISIBLE

        val now    = Date()
        val isDark = try { SolarCalc.isDarkSky(now, loc.latitude, loc.longitude) } catch (_: Exception) { false }
        val items  = mutableListOf<NowItem>()

        // Planets above 10°
        try {
            PlanetCalc.computeAll(now, loc.latitude, loc.longitude).forEach { pd ->
                if (pd.altitude > 10) items += NowItem.PlanetItem(pd)
            }
        } catch (_: Exception) {}

        // Messier (mag ≤ 11.5) above 15°
        try {
            Catalog.MESSIER.filter { it.magnitude <= 11.5 }.forEach { obj ->
                val pos = AstroEngine.equatorialToHorizontal(
                    obj.raHours, obj.decDeg, now, loc.latitude, loc.longitude)
                if (pos.altitude > 15) {
                    items += NowItem.CatalogItem(obj.copy(altitude = pos.altitude, azimuth = pos.azimuth))
                }
            }
        } catch (_: Exception) {}

        // Bright stars (mag ≤ 2.0) above 25°
        try {
            Catalog.STARS.filter { it.magnitude <= 2.0 }.forEach { star ->
                val pos = AstroEngine.equatorialToHorizontal(
                    star.raHours, star.decDeg, now, loc.latitude, loc.longitude)
                if (pos.altitude > 25) {
                    items += NowItem.StarItem(star.copy(altitude = pos.altitude, azimuth = pos.azimuth))
                }
            }
        } catch (_: Exception) {}

        val filtered = when (filterType) {
            "Galaxia" -> items.filterIsInstance<NowItem.CatalogItem>()
                .filter { it.obj.type == ObjectType.GALAXY }
            "Nebulosa" -> items.filterIsInstance<NowItem.CatalogItem>()
                .filter { it.obj.type == ObjectType.NEBULA || it.obj.type == ObjectType.PLANETARY_NEBULA }
            "Cumulo" -> items.filterIsInstance<NowItem.CatalogItem>()
                .filter { it.obj.type == ObjectType.GLOBULAR || it.obj.type == ObjectType.OPEN_CLUSTER }
            "Planeta" -> items.filterIsInstance<NowItem.PlanetItem>()
            else -> items
        }

        val sorted = filtered.sortedByDescending {
            when (it) {
                is NowItem.PlanetItem  -> it.data.altitude
                is NowItem.CatalogItem -> it.obj.altitude
                is NowItem.StarItem    -> it.star.altitude
            }
        }

        b.tvDayWarning.visibility = if (!isDark) View.VISIBLE else View.GONE

        adapter.submitList(sorted)
    }

    override fun onResume() {
        super.onResume()
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    private fun applyNightMode(isNight: Boolean) {
        (_binding?.root as? ViewGroup)?.applyNightRecursive(isNight)
        // The compass drawables are amber by default; tint them red at night so they don't leak.
        _binding?.let { b ->
            if (isNight) {
                b.compassRing.setColorFilter(NightModeManager.RED)
                b.compassNeedle.setColorFilter(NightModeManager.RED)
            } else {
                b.compassRing.clearColorFilter()
                b.compassNeedle.clearColorFilter()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Unregister listener to prevent callbacks into a destroyed view
        (activity as? MainActivity)?.locationListeners?.remove("NowFragment")
        (activity as? MainActivity)?.nightModeListeners?.remove("NowFragment")
        updateJob?.cancel()
        _binding = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotMatrix, orientation)
            compassDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
