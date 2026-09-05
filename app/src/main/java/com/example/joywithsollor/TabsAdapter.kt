package com.example.joywithsollor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TabsAdapter(
    private var tabs: List<Tab>,
    private val onItemClick: (Int) -> Unit,
    private val onCloseClick: (Int) -> Unit
) : RecyclerView.Adapter<TabsAdapter.TabViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.url.text = tab.url.ifEmpty { "about:blank" }
        holder.itemView.setOnClickListener { onItemClick(position) }
        holder.closeBtn.setOnClickListener { onCloseClick(position) }
    }

    override fun getItemCount(): Int = tabs.size

    fun updateTabs(newTabs: List<Tab>) {
        tabs = newTabs
        notifyDataSetChanged()
    }

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val url: TextView = itemView.findViewById(R.id.tabUrl)
        val closeBtn: Button = itemView.findViewById(R.id.closeTabButton)
    }
}