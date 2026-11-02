package com.example.joywithsollor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class ExtensionsAdapter(
    private val onInstall: (String) -> Unit,
    private val onUpdate: (String) -> Unit,
    private val onUninstall: (String) -> Unit,
    private val onEnabled: (String, Boolean) -> Unit
) : RecyclerView.Adapter<ExtensionsAdapter.VH>() {

    private var items: List<ExtensionEntry> = emptyList()

    fun submit(list: List<ExtensionEntry>) { items = list; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_extension, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.name.text = e.name
        holder.version.text = if (e.version.isBlank()) "" else "v${e.version}"
        holder.desc.text = e.description
        holder.desc.visibility = if (e.description.isBlank()) View.GONE else View.VISIBLE

        holder.install.visibility = if (e.installed) View.GONE else View.VISIBLE
        holder.update.visibility = if (e.installed) View.VISIBLE else View.GONE
        holder.uninstall.visibility = if (e.installed) View.VISIBLE else View.GONE
        holder.enabled.visibility = if (e.installed) View.VISIBLE else View.GONE

        holder.enabled.setOnCheckedChangeListener(null)
        holder.enabled.isChecked = e.enabled
        holder.enabled.setOnCheckedChangeListener { _, checked -> onEnabled(e.id, checked) }

        holder.install.setOnClickListener { onInstall(e.id) }
        holder.update.setOnClickListener { onUpdate(e.id) }
        holder.uninstall.setOnClickListener { onUninstall(e.id) }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.extName)
        val version: TextView = v.findViewById(R.id.extVersion)
        val desc: TextView = v.findViewById(R.id.extDescription)
        val install: MaterialButton = v.findViewById(R.id.extInstall)
        val update: MaterialButton = v.findViewById(R.id.extUpdate)
        val uninstall: MaterialButton = v.findViewById(R.id.extUninstall)
        val enabled: SwitchMaterial = v.findViewById(R.id.extEnabled)
    }
}
