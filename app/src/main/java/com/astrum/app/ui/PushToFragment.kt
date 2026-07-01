package com.astrum.app.ui
import com.astrum.app.AppClock

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.astrum.app.MainActivity
import com.astrum.app.NightModeManager
import com.astrum.app.applyNightRecursive
import com.astrum.app.astro.*
import com.quantumdrizzy.astro.AstroEngine
import com.astrum.app.databinding.FragmentPushtoBinding
import com.astrum.app.location.AstroLocation
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Push-to ("Localizar"): pick a target, hold the phone up, and get live
 * "gira X° / sube Y°" guidance until you're on it.
 *
 * Two timescales:
 *  - the TARGET's alt/az is recomputed on a slow timer (the sky moves slowly);
 *  - the phone's CURRENT pointing comes from the rotation-vector sensor and is
 *    fast — each event only recomputes the cheap [PushTo.guide].
 *
 * Math is validated (PushTo, DevicePointing); this fragment only wires sensors
 * + UI to it. The AR camera overlay is a later step.
 */
class PushToFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentPushtoBinding? = null
    private val binding get() = _binding!!

    private var sensorManager: SensorManager? = null
    private var currentLocation: AstroLocation? = null

    private val targets = mutableListOf<Target>()
    private var selectedIndex = 0
    private var targetAltAz: AstroEngine.HorizCoords? = null

    private var refreshJob: Job? = null
    private val rotationMatrix = FloatArray(9)
    private var lastUiUpdateMs = 0L

    // ── Target model ─────────────────────────────────────────────────────────
    private sealed class Target {
        abstract val label: String
        abstract fun altAz(now: Date, lat: Double, lng: Double): AstroEngine.HorizCoords?

        class Star(val name: String, val ra: Double, val dec: Double) : Target() {
            override val label get() = "★ $name"
            override fun altAz(now: Date, lat: Double, lng: Double) =
                AstroEngine.equatorialToHorizontal(ra, dec, now, lat, lng)
        }

        class Planet(val name: String) : Target() {
            override val label get() = "● $name"
            override fun altAz(now: Date, lat: Double, lng: Double): AstroEngine.HorizCoords? {
                val pd = PlanetCalc.computeAll(now, lat, lng).find { it.elements.name == name } ?: return null
                return AstroEngine.HorizCoords(pd.altitude, pd.azimuth)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPushtoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sensorManager = requireContext().getSystemService(SensorManager::class.java) as? SensorManager

        buildTargets()
        setupSpinner()

        val activity = requireActivity() as MainActivity
        currentLocation = activity.currentLocation
        activity.locationListeners["PushToFragment"] = { loc ->
            currentLocation = loc
            if (_binding != null) refreshTarget()
        }
        activity.nightModeListeners["PushToFragment"] = {
            applyNightMode(NightModeManager.isNightMode)
        }
        applyNightMode(NightModeManager.isNightMode)

        startRefreshLoop()
    }

    private fun buildTargets() {
        targets.clear()
        // Planets first (PlanetCalc resolves them to alt/az directly).
        listOf("Mercury", "Venus", "Mars", "Jupiter", "Saturn").forEach {
            targets += Target.Planet(it)
        }
        // Bright named stars (mag <= 2.0); computed live from RA/Dec.
        Catalog.STARS.filter { it.magnitude <= 2.0 }.sortedBy { it.magnitude }.forEach {
            targets += Target.Star(it.name, it.raHours, it.decDeg)
        }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            targets.map { it.label }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spinnerTarget.adapter = adapter
        binding.spinnerTarget.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedIndex = position
                refreshTarget()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun startRefreshLoop() {
        refreshJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                refreshTarget()
                delay(1_000L)
            }
        }
    }

    /** Recompute the selected target's alt/az and update the radar + info text. */
    private fun refreshTarget() {
        val b = _binding ?: return
        val loc = currentLocation
        if (loc == null) {
            b.tvNoLoc.visibility = View.VISIBLE
            targetAltAz = null
            return
        }
        b.tvNoLoc.visibility = View.GONE
        val target = targets.getOrNull(selectedIndex) ?: return
        val pos = try {
            target.altAz(AppClock.now(), loc.latitude, loc.longitude)
        } catch (_: Exception) { null } ?: return

        targetAltAz = pos
        b.azimuthView.setPosition(pos.azimuth.toFloat(), pos.altitude.toFloat())
        val updown = if (pos.altitude < 0) "below the horizon" else "alt %.0f°".format(pos.altitude)
        b.tvTargetInfo.text = "target · %s · az %03.0f°".format(updown, pos.azimuth)
    }

    // ── Sensor → live guidance ────────────────────────────────────────────────
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        // Throttle UI to ~20 Hz; the sensor can fire much faster.
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastUiUpdateMs < 50L) return
        lastUiUpdateMs = nowMs

        val target = targetAltAz ?: return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val current = DevicePointing.fromRotationMatrix(rotationMatrix)
        val g = PushTo.guide(current, target)
        renderGuidance(g)
    }

    private fun renderGuidance(g: PushTo.Guidance) {
        val b = _binding ?: return
        b.tvSeparation.text = "separation %.1f°".format(g.separation)

        if (g.onTarget) {
            b.tvOnTarget.visibility = View.VISIBLE
            b.tvCue.text = "Centered"
            return
        }
        b.tvOnTarget.visibility = View.GONE

        val turn = abs(g.turnDeg).roundToInt()
        val tilt = abs(g.tiltDeg).roundToInt()
        val turnTxt = if (g.turnDeg >= 0) "Turn $turn° →" else "← Turn $turn°"
        val tiltTxt = if (g.tiltDeg >= 0) "Up $tilt° ↑" else "Down $tilt° ↓"
        b.tvCue.text = "$turnTxt    $tiltTxt"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    private fun applyNightMode(isNight: Boolean) {
        (_binding?.root as? ViewGroup)?.applyNightRecursive(isNight)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.locationListeners?.remove("PushToFragment")
        (activity as? MainActivity)?.nightModeListeners?.remove("PushToFragment")
        refreshJob?.cancel()
        _binding = null
    }
}
