package com.football.livescores.matchlive.ui.favorite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.databinding.ItemFavoriteTeamLeagueBinding

sealed class FavoriteItem {
    abstract val id: Int
    abstract val name: String
    abstract val logo: String

    data class Team(
        override val id: Int,
        override val name: String,
        override val logo: String
    ) : FavoriteItem()

    data class League(
        override val id: Int,
        override val name: String,
        override val logo: String,
        val country: String
    ) : FavoriteItem()
}

class FavoriteTeamsLeaguesAdapter(
    private val onUnfavoriteClick: (FavoriteItem) -> Unit
) : ListAdapter<FavoriteItem, FavoriteTeamsLeaguesAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteTeamLeagueBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemFavoriteTeamLeagueBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FavoriteItem) {
            binding.tvName.text = item.name

            when (item) {
                is FavoriteItem.Team -> {
                    binding.tvSubtitle.text = itemView.context.getString(R.string.club)
                }
                is FavoriteItem.League -> {
                    binding.tvSubtitle.text = itemView.context.getString(R.string.league_with_country, item.country)
                }
            }

            Glide.with(binding.ivLogo.context)
                .load(item.logo)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(binding.ivLogo)

            binding.ivStar.setOnClickListener {
                onUnfavoriteClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<FavoriteItem>() {
            override fun areItemsTheSame(oldItem: FavoriteItem, newItem: FavoriteItem): Boolean {
                val sameType = (oldItem is FavoriteItem.Team && newItem is FavoriteItem.Team) ||
                        (oldItem is FavoriteItem.League && newItem is FavoriteItem.League)
                return sameType && oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: FavoriteItem, newItem: FavoriteItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
