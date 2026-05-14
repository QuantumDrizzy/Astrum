package com.astrum.app.ui

import android.graphics.Color
import android.view.*
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.astrum.app.NightModeManager
import com.astrum.app.R
import com.astrum.app.astro.Catalog
import com.astrum.app.astro.PlanetCalc

sealed class NowItem {
    data class PlanetItem(val data: PlanetCalc.PlanetData) : NowItem()
    data class CatalogItem(val obj: com.astrum.app.astro.CatalogObject) : NowItem()
    data class StarItem(val star: Catalog.BrightStar) : NowItem()
}

class NowAdapter : ListAdapter<NowItem, NowAdapter.VH>(DIFF) {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvItemName)
        val tvSub: TextView = view.findViewById(R.id.tvItemSub)
        val tvAlt: TextView = view.findViewById(R.id.tvItemAlt)
        val tvAz: TextView = view.findViewById(R.id.tvItemAz)
        val tvType: TextView = view.findViewById(R.id.tvItemType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_now, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val isNight = NightModeManager.isNightMode
        val red     = NightModeManager.RED
        val redDim  = NightModeManager.RED_DIM
        val ctx     = holder.view.context

        if (isNight) holder.itemView.setBackgroundColor(Color.rgb(10, 0, 0))
        else         holder.itemView.setBackgroundResource(R.drawable.card_bg)

        when (val item = getItem(position)) {
            is NowItem.PlanetItem -> {
                val pd = item.data
                holder.tvName.text = "${pd.elements.symbol} ${pd.elements.name}"
                holder.tvSub.text = "%.3f UA · %.0f km".format(pd.distAU, pd.distKm.toDouble())
                holder.tvAlt.text = "↑ %.1f°".format(pd.altitude)
                holder.tvAz.text = "Az %.0f°".format(pd.azimuth)
                holder.tvType.text = "Planeta"
                holder.tvType.setBackgroundResource(R.drawable.badge_planet)
                setAltColor(holder, pd.altitude, isNight)
            }
            is NowItem.CatalogItem -> {
                val obj = item.obj
                holder.tvName.text = obj.displayName
                holder.tvSub.text = obj.subtitle
                holder.tvAlt.text = "↑ %.1f°".format(obj.altitude)
                holder.tvAz.text = "Az %.0f°".format(obj.azimuth)
                holder.tvType.text = obj.type.label
                holder.tvType.setBackgroundResource(typeBadge(obj.type))
                setAltColor(holder, obj.altitude, isNight)
            }
            is NowItem.StarItem -> {
                val s = item.star
                holder.tvName.text = s.name
                holder.tvSub.text = "${s.constellation} · ${s.spectralType} · mag ${s.magnitude}"
                holder.tvAlt.text = "↑ %.1f°".format(s.altitude)
                holder.tvAz.text = "Az %.0f°".format(s.azimuth)
                holder.tvType.text = "Estrella"
                holder.tvType.setBackgroundResource(R.drawable.badge_star)
                setAltColor(holder, s.altitude, isNight)
            }
        }

        holder.tvName.setTextColor(if (isNight) red    else ContextCompat.getColor(ctx, R.color.text_primary))
        holder.tvSub.setTextColor( if (isNight) redDim else ContextCompat.getColor(ctx, R.color.text_dim))
        holder.tvAz.setTextColor(  if (isNight) redDim else ContextCompat.getColor(ctx, R.color.amber))
    }

    private fun setAltColor(holder: VH, alt: Double, isNight: Boolean) {
        val ctx = holder.view.context
        val altColor = when {
            alt > 30 -> ContextCompat.getColor(ctx, R.color.green_vis)
            alt > 10 -> ContextCompat.getColor(ctx, R.color.amber)
            else     -> ContextCompat.getColor(ctx, R.color.red_dim)
        }
        val color = if (isNight) NightModeManager.RED else altColor
        holder.tvAlt.setTextColor(color)
        val indicator = holder.view.findViewById<View>(R.id.visIndicator)
        indicator?.setBackgroundColor(color)
    }

    private fun typeBadge(type: com.astrum.app.astro.ObjectType) = when (type) {
        com.astrum.app.astro.ObjectType.GALAXY -> R.drawable.badge_galaxy
        com.astrum.app.astro.ObjectType.NEBULA,
        com.astrum.app.astro.ObjectType.PLANETARY_NEBULA -> R.drawable.badge_nebula
        com.astrum.app.astro.ObjectType.GLOBULAR -> R.drawable.badge_globular
        com.astrum.app.astro.ObjectType.OPEN_CLUSTER,
        com.astrum.app.astro.ObjectType.STAR_CLOUD -> R.drawable.badge_cluster
        else -> R.drawable.badge_other
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is NowItem.PlanetItem -> 0
        is NowItem.CatalogItem -> 1
        is NowItem.StarItem -> 2
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<NowItem>() {
            override fun areItemsTheSame(a: NowItem, b: NowItem) = when {
                a is NowItem.PlanetItem && b is NowItem.PlanetItem ->
                    a.data.elements.name == b.data.elements.name
                a is NowItem.CatalogItem && b is NowItem.CatalogItem ->
                    a.obj.id == b.obj.id
                a is NowItem.StarItem && b is NowItem.StarItem ->
                    a.star.name == b.star.name
                else -> false
            }
            override fun areContentsTheSame(a: NowItem, b: NowItem) = a == b
        }
    }
}
