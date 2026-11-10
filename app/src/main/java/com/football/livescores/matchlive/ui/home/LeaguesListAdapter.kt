package com.football.livescores.matchlive.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.football.livescores.matchlive.databinding.ItemFavoriteTeamLeagueBinding
import com.football.livescores.matchlive.ui.leagues.LeagueSelectorItem

class LeaguesListAdapter(
    private val onLeagueClick: (LeagueSelectorItem) -> Unit
) : ListAdapter<LeagueSelectorItem, LeaguesListAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteTeamLeagueBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemFavoriteTeamLeagueBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LeagueSelectorItem) {
            binding.tvName.text = item.name
            binding.tvSubtitle.text = "Giải đấu • ${item.country}"

            Glide.with(binding.root.context)
                .load(item.logo)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(binding.ivLogo)

            binding.ivStar.isVisible = false // Hide favorite star

            binding.root.setOnClickListener {
                onLeagueClick(item)
            }
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
