package com.example.silentzonefinder_android.adapter

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.silentzonefinder_android.databinding.ItemSearchHistoryBinding
import com.example.silentzonefinder_android.utils.SearchHistoryItem

class SearchHistoryAdapter(
    private val items: MutableList<SearchHistoryItem>,
    private val onSelect: (String) -> Unit,
    private val onDelete: (SearchHistoryItem) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.SearchHistoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchHistoryViewHolder {
        val binding = ItemSearchHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SearchHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchHistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<SearchHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class SearchHistoryViewHolder(
        private val binding: ItemSearchHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchHistoryItem) {
            binding.queryTextView.text = item.query
            binding.timestampTextView.text = DateUtils.getRelativeTimeSpanString(
                item.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )

            binding.root.setOnClickListener { onSelect(item.query) }
            binding.deleteButton.setOnClickListener { onDelete(item) }
        }
    }
}




