package com.football.livescores.matchlive.ui.leagues

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.databinding.ItemLeagueSelectorBinding

data class LeagueSelectorItem(
    val id: Int,
    val name: String,
    val logo: String,
    val country: String
)

class LeagueSelectorAdapter(
    private val onLeagueClick: (LeagueSelectorItem) -> Unit
) : ListAdapter<LeagueSelectorItem, LeagueSelectorAdapter.ViewHolder>(DiffCallback) {

    private var selectedLeagueId: Int = -1

    fun setSelectedLeagueId(id: Int) {
        if (selectedLeagueId != id) {
            selectedLeagueId = id
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLeagueSelectorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemLeagueSelectorBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LeagueSelectorItem) {
            binding.tvLeagueName.text = item.name

            // Load logo via Glide
            Glide.with(binding.root.context)
                .load(item.logo)
                .placeholder(R.mipmap.ic_launcher)
                .into(binding.ivLeagueLogo)

            val isSelected = item.id == selectedLeagueId
            val ctx = binding.root.context

            if (isSelected) {
                binding.cardLeague.strokeWidth = dpToPx(1.5f)
                binding.cardLeague.strokeColor = ContextCompat.getColor(ctx, R.color.accent_green)
                binding.tvLeagueName.setTextColor(ContextCompat.getColor(ctx, R.color.text_white))
            } else {
                binding.cardLeague.strokeWidth = 0
                binding.tvLeagueName.setTextColor(ContextCompat.getColor(ctx, R.color.text_muted))
            }

            binding.root.setOnClickListener {
                onLeagueClick(item)
            }
        }

        private fun dpToPx(dp: Float): Int {
            val density = binding.root.context.resources.displayMetrics.density
            return (dp * density).toInt()
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<LeagueSelectorItem>() {
            override fun areItemsTheSame(oldItem: LeagueSelectorItem, newItem: LeagueSelectorItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: LeagueSelectorItem, newItem: LeagueSelectorItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
