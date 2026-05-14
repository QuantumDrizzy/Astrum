package com.astrum.app.ui

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.astrum.app.MainActivity
import com.astrum.app.NightModeManager
import com.astrum.app.R
import com.astrum.app.applyNightRecursive
import com.astrum.app.astro.AstroEngine
import com.astrum.app.astro.Catalog
import com.astrum.app.databinding.FragmentStarsBinding
import com.astrum.app.location.AstroLocation
import com.astrum.app.views.AzimuthView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import java.util.*

class StarsFragment : Fragment() {
    private var _b: FragmentStarsBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: StarsAdapter
    private var location: AstroLocation? = null
    private var visibleOnly = false
    private var brightOnly  = false
    private var query       = ""
    private var currentSort = SortMode.ALTITUDE_DESC
    private var job: Job?   = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentStarsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        adapter = StarsAdapter()
        b.recyclerStars.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerStars.adapter = adapter

        b.btnSort.setOnClickListener { showSortDialog() }
        b.etSearch.addTextChangedListener { query = it.toString(); render() }
        b.chipAllStars.setOnClickListener     { visibleOnly = false; brightOnly = false; syncChips(); render() }
        b.chipVisibleStars.setOnClickListener { visibleOnly = !visibleOnly; syncChips(); render() }
        b.chipBright.setOnClickListener       { brightOnly  = !brightOnly;  syncChips(); render() }
        b.chipAllStars.isSelected = true

        val activity = requireActivity() as MainActivity
        location = activity.currentLocation
        activity.locationListeners["StarsFragment"] = { loc ->
            location = loc
            if (_b != null) render()
        }
        activity.nightModeListeners["StarsFragment"] = {
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
        val tint = if (isNight) NightModeManager.RED
                   else ContextCompat.getColor(requireContext(), R.color.text_secondary)
        b.btnSort.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
    }

    private fun syncChips() {
        val b = _b ?: return
        b.chipAllStars.isSelected     = !visibleOnly && !brightOnly
        b.chipVisibleStars.isSelected = visibleOnly
        b.chipBright.isSelected       = brightOnly
    }

    private fun render() {
        val b   = _b ?: return
        val loc = location
        val now = Date()
        var items = Catalog.STARS.map { star ->
            if (loc != null) {
                try {
                    val pos = AstroEngine.equatorialToHorizontal(
                        star.raHours, star.decDeg, now, loc.latitude, loc.longitude)
                    val rs = AstroEngine.riseSetTransit(
                        star.raHours, star.decDeg, now, loc.latitude, loc.longitude)
                    val setMins = when {
                        rs.isCircumpolar || rs.neverRises -> Int.MAX_VALUE
                        else -> rs.set?.let {
                            val cal = Calendar.getInstance().apply { time = it }
                            cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                        } ?: Int.MAX_VALUE
                    }
                    star.copy(altitude = pos.altitude, azimuth = pos.azimuth, setMinutes = setMins)
                } catch (_: Exception) { star }
            } else star
        }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            items = items.filter {
                it.name.lowercase().contains(q) || it.constellation.lowercase().contains(q)
            }
        }
        if (visibleOnly) items = items.filter { it.altitude > 5 }
        if (brightOnly)  items = items.filter { it.magnitude < 1.5 }
        items = when (currentSort) {
            SortMode.ALTITUDE_DESC -> items.sortedByDescending { it.altitude }
            SortMode.MAGNITUDE_ASC -> items.sortedBy { it.magnitude }
            SortMode.SET_TIME_ASC  -> items.sortedBy { it.setMinutes }
        }
        adapter.submitList(items)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.locationListeners?.remove("StarsFragment")
        (activity as? MainActivity)?.nightModeListeners?.remove("StarsFragment")
        job?.cancel()
        _b = null
    }
}

class StarsAdapter : ListAdapter<Catalog.BrightStar, StarsAdapter.VH>(DIFF) {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView         = v.findViewById(R.id.tvStarName)
        val tvSub: TextView          = v.findViewById(R.id.tvStarSub)
        val tvAlt: TextView          = v.findViewById(R.id.tvStarAlt)
        val tvAz: TextView           = v.findViewById(R.id.tvStarAz)
        val visBar: View             = v.findViewById(R.id.starVisBar)
        val azimuthView: AzimuthView = v.findViewById(R.id.azimuthView)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_star, p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val s       = getItem(pos)
        val ctx     = h.itemView.context
        val isNight = NightModeManager.isNightMode
        val red     = NightModeManager.RED
        val redDim  = NightModeManager.RED_DIM

        if (isNight) h.itemView.setBackgroundColor(Color.rgb(10, 0, 0))
        else         h.itemView.setBackgroundResource(R.drawable.card_bg)

        h.tvName.text = s.name
        h.tvName.setTextColor(if (isNight) red else ContextCompat.getColor(ctx, R.color.text_primary))

        h.tvSub.text = "${s.constellation} · ${s.spectralType} · mag ${"%.2f".format(s.magnitude)}"
        h.tvSub.setTextColor(if (isNight) redDim else ContextCompat.getColor(ctx, R.color.text_dim))

        h.tvAz.text = "Az ${"%.0f".format(s.azimuth)}°"
        h.tvAz.setTextColor(if (isNight) redDim else ContextCompat.getColor(ctx, R.color.amber))

        val altColor = when {
            s.altitude > 30 -> ContextCompat.getColor(ctx, R.color.green_vis)
            s.altitude > 10 -> ContextCompat.getColor(ctx, R.color.amber)
            else            -> ContextCompat.getColor(ctx, R.color.text_dim)
        }
        h.tvAlt.text = when {
            s.altitude > 0 -> "↑ ${"%.1f".format(s.altitude)}°"
            else           -> "Bajo horizonte"
        }
        h.tvAlt.setTextColor(if (isNight) red else altColor)
        h.visBar.setBackgroundColor(if (isNight) red else altColor)

        h.azimuthView.setPosition(s.azimuth.toFloat(), s.altitude.toFloat())
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Catalog.BrightStar>() {
            override fun areItemsTheSame(a: Catalog.BrightStar, b: Catalog.BrightStar)    = a.name == b.name
            override fun areContentsTheSame(a: Catalog.BrightStar, b: Catalog.BrightStar) =
                a.altitude == b.altitude
        }
    }
}
