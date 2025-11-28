package com.example.silentzonefinder_android

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.silentzonefinder_android.adapter.SearchHistoryAdapter
import com.example.silentzonefinder_android.databinding.ActivitySearchHistoryBinding
import com.example.silentzonefinder_android.utils.SearchHistoryItem
import com.example.silentzonefinder_android.utils.SearchHistoryManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class SearchHistoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTED_QUERY = "extra_selected_query"
    }

    private lateinit var binding: ActivitySearchHistoryBinding
    private lateinit var adapter: SearchHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        renderHistory()
    }

    private fun setupRecyclerView() {
        adapter = SearchHistoryAdapter(
            items = mutableListOf(),
            onSelect = { query -> returnSelectedQuery(query) },
            onDelete = { item -> removeHistoryItem(item) }
        )
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.clearAllButton.setOnClickListener {
            val history = SearchHistoryManager.getHistory(this)
            if (history.isEmpty()) return@setOnClickListener

            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.search_history_clear_all_dialog_title))
                .setMessage(getString(R.string.search_history_clear_all_dialog_message))
                .setNegativeButton(getString(R.string.search_history_clear_all_dialog_cancel), null)
                .setPositiveButton(getString(R.string.search_history_clear_all_dialog_confirm)) { _, _ ->
                    SearchHistoryManager.clearHistory(this)
                    renderHistory()
                    Snackbar.make(
                        binding.root,
                        getString(R.string.search_history_all_deleted),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
                .show()
        }
    }

    private fun renderHistory() {
        val history = SearchHistoryManager.getHistory(this)
        adapter.submitList(history)

        val isEmpty = history.isEmpty()
        binding.historyRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.emptyStateTextView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.clearAllButton.isEnabled = !isEmpty
    }

    private fun removeHistoryItem(item: SearchHistoryItem) {
        SearchHistoryManager.removeSearchQuery(this, item.query)
        renderHistory()
        Snackbar.make(
            binding.root,
            getString(R.string.search_history_item_deleted),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun returnSelectedQuery(query: String) {
        val intent = Intent().apply {
            putExtra(EXTRA_SELECTED_QUERY, query)
        }
        setResult(RESULT_OK, intent)
        finish()
    }
}





