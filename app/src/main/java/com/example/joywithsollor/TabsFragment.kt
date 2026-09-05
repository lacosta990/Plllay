package com.example.joywithsollor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TabsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var newTabButton: Button
    private lateinit var viewModel: TabsViewModel
    private lateinit var adapter: TabsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tabs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.tabsRecyclerView)
        newTabButton = view.findViewById(R.id.newTabButton)

        viewModel = ViewModelProvider(requireActivity()).get(TabsViewModel::class.java)

        val initialTabs = viewModel.tabs.value ?: emptyList()
        adapter = TabsAdapter(
            tabs = initialTabs,
            onItemClick = { position ->
                viewModel.switchTab(position)
                parentFragmentManager.popBackStack()
            },
            onCloseClick = { position ->
                viewModel.closeTab(position)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewModel.tabs.observe(viewLifecycleOwner) { tabs ->
            adapter.updateTabs(tabs)
        }

        newTabButton.setOnClickListener {
            val runtime = (requireActivity() as? MainActivity)?.runtime
            if (runtime != null) {
                // Создаём новую вкладку с домашней страницей (или Google, если хотите)
                // По умолчанию в createTab уже задан URL
                viewModel.createTab(null, runtime)
                parentFragmentManager.popBackStack()
            }
        }
    }
}