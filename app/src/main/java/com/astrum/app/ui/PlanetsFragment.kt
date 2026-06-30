package com.astrum.app.ui

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.astrum.app.MainActivity
import com.astrum.app.NightModeManager
import com.astrum.app.R
import com.astrum.app.applyNightRecursive
import com.astrum.app.astro.*
import com.quantumdrizzy.astro.AstroEngine
import com.astrum.app.databinding.FragmentPlanetsBinding
import com.astrum.app.location.AstroLocation
import com.astrum.app.views.AzimuthView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import java.util.*

enum class SortMode { ALTITUDE_DESC, MAGNITUDE_ASC, SET_TIME_ASC }

class PlanetsFragment : Fragment() {
    private var _b: FragmentPlanetsBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: PlanetAdapter
    private var location: AstroLocation? = null
    private var job: Job? = null
    private var currentSort = SortMode.ALTITUDE_DESC

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentPlanetsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        adapter = PlanetAdapter()
        b.recyclerPlanets.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerPlanets.adapter = adapter

        b.btnSort.setOnClickListener { showSortDialog() }

        val activity = requireActivity() as MainActivity
        location = activity.currentLocation
        activity.locationListeners["PlanetsFragment"] = { loc ->
            location = loc
            if (_b != null) render()
        }
        activity.nightModeListeners["PlanetsFragment"] = {
            applyNightMode(NightModeManager.isNightMode)
            adapter.notifyDataSetChanged()
        }
        applyNightMode(NightModeManager.isNightMode)

        job = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) { render(); delay(15_000L) }
        }
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_altitude),
            getString(R.string.sort_magnitude),
            getString(R.string.sort_set_time)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_title)
            .setSingleChoiceItems(options, currentSort.ordinal) { dialog, which ->
                currentSort = SortMode.values()[which]
                render()
                dialog.dismiss()
            }
            .show()
    }

    private fun applyNightMode(isNight: Boolean) {
        val b = _b ?: return
        (b.root as? ViewGroup)?.applyNightRecursive(isNight)
        val tint = if (isNight) NightModeManager.RED else ContextCompat.getColor(requireContext(), R.color.text_secondary)
        b.btnSort.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
    }

    private fun render() {
        val b   = _b ?: return
        val loc = location ?: return
        val now = Date()
        try {
            var planets = PlanetCalc.computeAll(now, loc.latitude, loc.longitude)
            planets = when (currentSort) {
                SortMode.ALTITUDE_DESC -> planets.sortedByDescending { it.altitude }
                SortMode.MAGNITUDE_ASC -> planets.sortedBy { it.magnitude }
                SortMode.SET_TIME_ASC  -> planets.sortedBy { it.setTime?.time ?: Long.MAX_VALUE }
            }
            adapter.submitList(planets)
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.locationListeners?.remove("PlanetsFragment")
        (activity as? MainActivity)?.nightModeListeners?.remove("PlanetsFragment")
        job?.cancel()
        _b = null
    }
}

class PlanetAdapter : ListAdapter<PlanetCalc.PlanetData, PlanetAdapter.VH>(DIFF) {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView    = v.findViewById(R.id.tvPlanetName)
        val tvVis: TextView     = v.findViewById(R.id.tvPlanetVis)
        val tvAlt: TextView     = v.findViewById(R.id.tvPlanetAlt)
        val tvAz: TextView      = v.findViewById(R.id.tvPlanetAz)
        val tvDist: TextView    = v.findViewById(R.id.tvPlanetDist)
        val tvMag: TextView     = v.findViewById(R.id.tvPlanetMag)
        val tvElong: TextView   = v.findViewById(R.id.tvPlanetElong)
        val tvCoords: TextView  = v.findViewById(R.id.tvPlanetCoords)
        val tvTimes: TextView   = v.findViewById(R.id.tvPlanetTimes)
        val azimuthView: AzimuthView = v.findViewById(R.id.azimuthView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_planet, parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val p      = getItem(pos)
        val ctx    = h.itemView.context
        val isNight = NightModeManager.isNightMode
        val red    = NightModeManager.RED
        val redDim = NightModeManager.RED_DIM

        // Item background
        if (isNight) h.itemView.setBackgroundColor(Color.rgb(10, 0, 0))
        else         h.itemView.setBackgroundResource(R.drawable.card_bg)

        h.tvName.text = "${p.elements.symbol} ${p.elements.name}"
        h.tvName.setTextColor(if (isNight) red else ContextCompat.getColor(ctx, R.color.text_primary))

        val (visText, visBg) = when {
            p.isVisible -> Pair("VISIBLE",    R.drawable.badge_vis_yes)
            p.isLow     -> Pair("HORIZONTE",  R.drawable.badge_vis_low)
            else        -> Pair("NO VISIBLE", R.drawable.badge_vis_no)
        }
        h.tvVis.text = visText
        h.tvVis.setBackgroundResource(visBg)

        val altColor = when {
            p.altitude > 30 -> ContextCompat.getColor(ctx, R.color.green_vis)
            p.altitude > 10 -> ContextCompat.getColor(ctx, R.color.amber)
            else            -> ContextCompat.getColor(ctx, R.color.text_dim)
        }
        h.tvAlt.text = "%.1f°".format(p.altitude)
        h.tvAlt.setTextColor(if (isNight) red else altColor)

        h.tvAz.text = "%.1f°".format(p.azimuth)
        h.tvAz.setTextColor(if (isNight) red else ContextCompat.getColor(ctx, R.color.text_primary))

        h.tvDist.text = "%.3f UA".format(p.distAU)
        h.tvDist.setTextColor(if (isNight) red else ContextCompat.getColor(ctx, R.color.text_primary))

        h.tvMag.text = if (p.magnitude < 99) "%+.1f".format(p.magnitude) else "--"
        h.tvMag.setTextColor(if (isNight) red else ContextCompat.getColor(ctx, R.color.teal_accent))

        h.tvElong.text = "Elongación %.0f°  %s".format(
            p.elongation,
            when {
                p.elongation < 20  -> "cerca del Sol"
                p.elongation < 90  -> "cuadratura este/oeste"
                p.elongation > 170 -> "en oposición"
                else               -> ""
            }
        )
        h.tvElong.setTextColor(if (isNight) redDim else ContextCompat.getColor(ctx, R.color.blue_accent))

        h.tvCoords.text = "AR ${AstroEngine.raToString(p.raHours)}  Dec ${AstroEngine.decToString(p.decDeg)}"
        h.tvCoords.setTextColor(if (isNight) redDim else ContextCompat.getColor(ctx, R.color.text_dim))

        h.tvTimes.text = "↑ ${AstroEngine.formatTime(p.riseTime)}  " +
                         "⊙ ${AstroEngine.formatTime(p.transitTime)}  " +
                         "↓ ${AstroEngine.formatTime(p.setTime)}"
        h.tvTimes.setTextColor(if (isNight) redDim else ContextCompat.getColor(ctx, R.color.text_dim))

        h.azimuthView.setPosition(p.azimuth.toFloat(), p.altitude.toFloat())
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<PlanetCalc.PlanetData>() {
            override fun areItemsTheSame(a: PlanetCalc.PlanetData, b: PlanetCalc.PlanetData) =
                a.elements.name == b.elements.name
            override fun areContentsTheSame(a: PlanetCalc.PlanetData, b: PlanetCalc.PlanetData) =
                a.altitude == b.altitude && a.distAU == b.distAU
        }
    }
}
