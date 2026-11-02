package com.example.joywithsollor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton

class TabsAdapter(
    private var tabs: List<Tab> = emptyList(),
    private var activeIndex: Int = 0,
    private val onClick: (Int) -> Unit,
    private val onClose: (Int) -> Unit
) : RecyclerView.Adapter<TabsAdapter.VH>() {

    fun submit(list: List<Tab>, active: Int) {
        tabs = list
        activeIndex = active
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false))

    override fun getItemCount() = tabs.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tab = tabs[position]
        holder.title.text = tab.title.ifBlank { "Новая вкладка" }
        holder.address.text = prettyUrl(tab.url)
        holder.dot.visibility = if (position == activeIndex) View.VISIBLE else View.INVISIBLE
        holder.itemView.setOnClickListener { onClick(holder.adapterPosition) }
        holder.close.setOnClickListener { onClose(holder.adapterPosition) }
    }

    /** Адрес для показа: без схемы и без www */
    private fun prettyUrl(url: String): String {
        if (url.isBlank() || url == "about:blank") return ""
        return url
            .replaceFirst(Regex("^https?://"), "")
            .replaceFirst(Regex("^www\\.", RegexOption.IGNORE_CASE), "")
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tabTitle)
        val address: TextView = v.findViewById(R.id.tabAddress)
        val dot: View = v.findViewById(R.id.tabActiveDot)
        val close: ImageButton = v.findViewById(R.id.closeTabButton)
    }
}
