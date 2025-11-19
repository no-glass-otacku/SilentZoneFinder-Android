package com.example.silentzonefinder_android.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.silentzonefinder_android.MainActivity
import com.example.silentzonefinder_android.databinding.ItemPlaceResultBinding

class PlaceSearchAdapter(
    private val onPlaceClick: (MainActivity.PlaceDocument) -> Unit
) : RecyclerView.Adapter<PlaceSearchAdapter.PlaceViewHolder>() {

    private val items = mutableListOf<MainActivity.PlaceDocument>()

    fun submitList(newItems: List<MainActivity.PlaceDocument>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val binding = ItemPlaceResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlaceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PlaceViewHolder(
        private val binding: ItemPlaceResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(place: MainActivity.PlaceDocument) {
            binding.placeNameTextView.text = place.place_name
            binding.addressTextView.text = place.road_address_name.takeIf { it.isNotBlank() } ?: place.address_name

            val category = place.category_name
            binding.categoryTextView.isVisible = category.isNotBlank()
            binding.categoryTextView.text = category

            val distanceText = place.distance.takeIf { it.isNotBlank() }?.let { "${it}m" }
            binding.distanceTextView.isVisible = !distanceText.isNullOrBlank()
            binding.distanceTextView.text = distanceText ?: ""

            binding.root.setOnClickListener { onPlaceClick(place) }
        }
    }
}
