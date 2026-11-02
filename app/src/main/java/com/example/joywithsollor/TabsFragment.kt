package com.example.joywithsollor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class TabsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View =
        inflater.inflate(R.layout.fragment_tabs, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val vm = ViewModelProvider(requireActivity()).get(TabsViewModel::class.java)
        val list = view.findViewById<RecyclerView>(R.id.tabsRecyclerView)
        val add = view.findViewById<FloatingActionButton>(R.id.newTabButton)

        val adapter = TabsAdapter(
            onClick = { pos -> if (pos >= 0) { vm.switchTab(pos); parentFragmentManager.popBackStack() } },
            onClose = { pos -> if (pos >= 0) vm.closeTab(pos) }
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        val refresh: () -> Unit = { adapter.submit(vm.tabs.value.orEmpty(), vm.activeIndex.value ?: 0) }
        vm.tabs.observe(viewLifecycleOwner) { refresh() }
        vm.activeIndex.observe(viewLifecycleOwner) { refresh() }
        refresh()

        add.setOnClickListener {
            (requireActivity() as? MainActivity)?.createNewTab()
            parentFragmentManager.popBackStack()
        }

        view.findViewById<android.widget.ImageButton>(R.id.extensionsButton).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ExtensionsFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}
