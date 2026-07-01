package com.astrum.app.ui
import com.astrum.app.AppClock

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.*
import android.widget.ImageView
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
import com.astrum.app.astro.*
import com.quantumdrizzy.astro.AstroEngine
import com.astrum.app.databinding.FragmentCatalogBinding
import com.astrum.app.location.AstroLocation
import com.astrum.app.views.AzimuthView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import java.util.*

class CatalogFragment : Fragment() {
    private var _b: FragmentCatalogBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: CatalogAdapter
    private var location: AstroLocation? = null
    private var typeFilter  = "ALL"
    private var visibleOnly = false
    private var query       = ""
    private var currentSort = SortMode.ALTITUDE_DESC
    private var job: Job?   = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentCatalogBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        adapter = CatalogAdapter()
        b.recyclerCatalog.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerCatalog.adapter = adapter

        b.btnSort.setOnClickListener { showSortDialog() }
        b.etSearch.addTextChangedListener { query = it.toString(); render() }
        b.chipAll.setOnClickListener      { setFilter("ALL",      it) }
        b.chipGalaxy.setOnClickListener   { setFilter("Galaxia",  it) }
        b.chipNebula.setOnClickListener   { setFilter("Nebulosa", it) }
        b.chipGlobular.setOnClickListener { setFilter("Globular", it) }
        b.chipOpen.setOnClickListener     { setFilter("Abierto",  it) }
        b.chipVisible.setOnClickListener  { visibleOnly = !visibleOnly; it.isSelected = visibleOnly; render() }
        b.chipAll.isSelected = true

        val activity = requireActivity() as MainActivity
        location = activity.currentLocation
        activity.locationListeners["CatalogFragment"] = { loc ->
            location = loc
            if (_b != null) render()
        }
        activity.nightModeListeners["CatalogFragment"] = {
            applyNightMode(NightModeManager.isNightMode)
            adapter.notifyDataSetChanged()
        }
        applyNightMode(NightModeManager.isNightMode)

        job = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) { render(); delay(30_000L) }
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

    private fun setFilter(f: String, view: View) {
        typeFilter = f
        listOf(b.chipAll, b.chipGalaxy, b.chipNebula, b.chipGlobular, b.chipOpen)
            .forEach { it.isSelected = false }
        view.isSelected = true
        render()
    }

    private fun render() {
        val b   = _b ?: return
        val loc = location
        val now = AppClock.now()
        var items = Catalog.MESSIER.map { obj ->
            if (loc != null) {
                try {
                    val pos = AstroEngine.equatorialToHorizontal(
                        obj.raHours, obj.decDeg, now, loc.latitude, loc.longitude)
                    val rs = AstroEngine.riseSetTransit(
                        obj.raHours, obj.decDeg, now, loc.latitude, loc.longitude)
                    val setMins = when {
                        rs.isCircumpolar || rs.neverRises -> Int.MAX_VALUE
                        else -> rs.set?.let {
                            val cal = Calendar.getInstance().apply { time = it }
                            cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                        } ?: Int.MAX_VALUE
                    }
                    val computed = obj.copy(
                        altitude      = pos.altitude,
                        azimuth       = pos.azimuth,
                        riseTime      = AstroEngine.formatTime(rs.rise),
                        setTime       = AstroEngine.formatTime(rs.set),
                        transitTime   = AstroEngine.formatTime(rs.transit),
                        isCircumpolar = rs.isCircumpolar,
                        neverRises    = rs.neverRises
                    )
                    computed.setMinutes = setMins
                    computed
                } catch (_: Exception) { obj }
            } else obj
        }

        if (query.isNotBlank()) {
            val q = query.lowercase()
            items = items.filter {
                it.id.lowercase().contains(q) ||
                it.constellation.lowercase().contains(q) ||
                it.commonName.lowercase().contains(q) ||
                it.type.label.lowercase().contains(q)
            }
        }
        items = when (typeFilter) {
            "Galaxia"  -> items.filter { it.type == ObjectType.GALAXY }
            "Nebulosa" -> items.filter { it.type == ObjectType.NEBULA || it.type == ObjectType.PLANETARY_NEBULA }
            "Globular" -> items.filter { it.type == ObjectType.GLOBULAR }
            "Abierto"  -> items.filter { it.type == ObjectType.OPEN_CLUSTER }
            else       -> items
        }
        if (visibleOnly) items = items.filter { it.altitude > 10 }
        items = when (currentSort) {
            SortMode.ALTITUDE_DESC -> items.sortedByDescending { it.altitude }
            SortMode.MAGNITUDE_ASC -> items.sortedBy { it.magnitude }
            SortMode.SET_TIME_ASC  -> items.sortedBy { it.setMinutes }
        }

        adapter.submitList(items)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.locationListeners?.remove("CatalogFragment")
        (activity as? MainActivity)?.nightModeListeners?.remove("CatalogFragment")
        job?.cancel()
        _b = null
    }
}

class CatalogAdapter : ListAdapter<CatalogObject, CatalogAdapter.VH>(DIFF) {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView         = v.findViewById(R.id.tvCatName)
        val tvSub: TextView          = v.findViewById(R.id.tvCatSub)
        val tvAlt: TextView          = v.findViewById(R.id.tvCatAlt)
        val tvCoords: TextView       = v.findViewById(R.id.tvCatCoords)
        val tvType: TextView         = v.findViewById(R.id.tvCatType)
        val tvTimes: TextView        = v.findViewById(R.id.tvCatTimes)
        val visBar: View             = v.findViewById(R.id.catVisBar)
        val ivCatTypeIcon: ImageView = v.findViewById(R.id.ivCatTypeIcon)
        val azimuthView: AzimuthView = v.findViewById(R.id.azimuthView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_catalog, parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val o       = getItem(pos)
        val ctx     = h.itemView.context
        val isNight = NightModeManager.isNightMode
        val red     = NightModeManager.RED
        val redDim  = NightModeManager.RED_DIM

        if (isNight) h.itemView.setBackgroundColor(Color.rgb(10, 0, 0))
        else         h.itemView.setBackgroundResource(R.drawable.card_bg)

        h.tvName.text = o.displayName
        h.tvName.setTextColor(if (isNight) red else ContextCompat.getColor(ctx, R.color.text_primary))

        h.tvSub.text = o.subtitle
        h.tvSub.setTextColor(if (isNight) redDim else ContextCompat.getColor(ctx, R.color.text_dim))

        h.tvCoords.text = "AR ${AstroEngine.raToString(o.raHours)}  Dec ${AstroEngine.decToString(o.decDeg)}"
        h.tvCoords.setTextColor(if (isNight) redDim else ContextCompat.getColor(ctx, R.color.amber))

        h.tvType.text = o.type.label
        h.tvType.setTextColor(if (isNight) redDim else ContextCompat.getColor(ctx, R.color.blue_accent))

        h.ivCatTypeIcon.setImageResource(o.typeIconRes)
        if (isNight) h.ivCatTypeIcon.setColorFilter(red, PorterDuff.Mode.SRC_IN)
        else         h.ivCatTypeIcon.clearColorFilter()

        val altColor = when {
            o.altitude > 30 -> ContextCompat.getColor(ctx, R.color.green_vis)
            o.altitude > 10 -> ContextCompat.getColor(ctx, R.color.amber)
            else            -> ContextCompat.getColor(ctx, R.color.text_dim)
        }
        h.tvAlt.text = when {
            o.neverRises    -> "Never visible"
            o.isCircumpolar -> "Circumpolar"
            o.altitude > 0  -> "↑ %.1f°  Az %.0f°".format(o.altitude, o.azimuth)
            else            -> "Below horizon"
        }
        h.tvAlt.setTextColor(if (isNight) red else altColor)
        h.visBar.setBackgroundColor(if (isNight) red else altColor)

        h.tvTimes.text = if (!o.isCircumpolar && !o.neverRises)
            "↑ ${o.riseTime}  ⊙ ${o.transitTime}  ↓ ${o.setTime}"
        else ""
        h.tvTimes.setTextColor(if (isNight) redDim else ContextCompat.getColor(ctx, R.color.text_dim))

        h.azimuthView.setPosition(o.azimuth.toFloat(), o.altitude.toFloat())
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CatalogObject>() {
            override fun areItemsTheSame(a: CatalogObject, b: CatalogObject)    = a.id == b.id
            override fun areContentsTheSame(a: CatalogObject, b: CatalogObject) =
                a.altitude == b.altitude && a.id == b.id
        }
    }
}
