package com.example.joywithsollor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ExtensionsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View =
        inflater.inflate(R.layout.fragment_extensions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as MainActivity
        val mgr = ExtensionsManager.get(activity, activity.runtime)

        val list = view.findViewById<RecyclerView>(R.id.extensionsRecyclerView)
        val progress = view.findViewById<ProgressBar>(R.id.extensionsProgress)

        val adapter = ExtensionsAdapter(
            onInstall = { mgr.install(it) },
            onUpdate = { mgr.update(it) },
            onUninstall = { mgr.uninstall(it) },
            onEnabled = { id, on -> mgr.setEnabled(id, on) }
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        mgr.entries.observe(viewLifecycleOwner) { adapter.submit(it) }
        mgr.busy.observe(viewLifecycleOwner) { progress.visibility = if (it != null) View.VISIBLE else View.GONE }
        mgr.message.observe(viewLifecycleOwner) {
            if (!it.isNullOrBlank()) { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show(); mgr.clearMessage() }
        }

        view.findViewById<View>(R.id.extensionsRefresh).setOnClickListener { mgr.loadCatalog() }
        view.findViewById<View>(R.id.extensionsUpdateAll).setOnClickListener { mgr.updateAll() }
        view.findViewById<View>(R.id.extensionsClose).setOnClickListener { parentFragmentManager.popBackStack() }

        mgr.loadCatalog()
    }
}
