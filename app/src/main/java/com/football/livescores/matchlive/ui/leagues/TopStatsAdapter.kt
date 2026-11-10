package com.football.livescores.matchlive.ui.leagues

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.remote.model.TopPlayerItemDto
import com.football.livescores.matchlive.databinding.ItemTopPlayerBinding
import androidx.core.content.ContextCompat

class TopStatsAdapter(
    private val isAssists: Boolean,
    private val onPlayerClick: (TopPlayerItemDto) -> Unit
) : ListAdapter<TopPlayerItemDto, TopStatsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopPlayerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    inner class ViewHolder(private val binding: ItemTopPlayerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TopPlayerItemDto, rank: Int) {
            binding.tvPlayerRank.text = rank.toString()
            binding.tvPlayerName.text = item.player.name
            
            val stat = item.statistics.firstOrNull()
            binding.tvPlayerClub.text = stat?.team?.name ?: "Unknown Club"

            // Load Player Photo via Glide
            Glide.with(binding.root.context)
                .load(item.player.photo)
                .placeholder(R.mipmap.ic_launcher)
                .circleCrop()
                .into(binding.ivPlayerPhoto)

            // Load Team Crest Logo via Glide
            Glide.with(binding.root.context)
                .load(stat?.team?.logo)
                .placeholder(R.mipmap.ic_launcher)
                .into(binding.ivPlayerTeamLogo)

            // Display Goals or Assists value
            val countValue = if (isAssists) {
                stat?.goals?.assists ?: 0
            } else {
                stat?.goals?.total ?: 0
            }
            binding.tvStatCount.text = countValue.toString()
            val context = itemView.context
            binding.tvStatLabel.text = if (isAssists) {
                context.getString(R.string.assists_label)
            } else {
                context.getString(R.string.goals_label)
            }

            // Set Up Metallic Top 3 Glowing Badges (Gold, Silver, Bronze)
            when (rank) {
                1 -> {
                    // Gold
                    binding.tvPlayerRank.setTextColor(ContextCompat.getColor(context, R.color.stats_rank_gold))
                    binding.tvPlayerRank.textStyleBold()
                    binding.cardPlayerImage.strokeColor = ContextCompat.getColor(context, R.color.stats_rank_gold)
                    binding.cardPlayerImage.strokeWidth = dpToPx(2)
                }
                2 -> {
                    // Silver
                    binding.tvPlayerRank.setTextColor(ContextCompat.getColor(context, R.color.stats_rank_silver))
                    binding.tvPlayerRank.textStyleBold()
                    binding.cardPlayerImage.strokeColor = ContextCompat.getColor(context, R.color.stats_rank_silver)
                    binding.cardPlayerImage.strokeWidth = dpToPx(1.5f)
                }
                3 -> {
                    // Bronze
                    binding.tvPlayerRank.setTextColor(ContextCompat.getColor(context, R.color.stats_rank_bronze))
                    binding.tvPlayerRank.textStyleBold()
                    binding.cardPlayerImage.strokeColor = ContextCompat.getColor(context, R.color.stats_rank_bronze)
                    binding.cardPlayerImage.strokeWidth = dpToPx(1.5f)
                }
                else -> {
                    // Normal
                    binding.tvPlayerRank.setTextColor(ContextCompat.getColor(context, R.color.textSecondary)) // muted text
                    binding.tvPlayerRank.textStyleNormal()
                    binding.cardPlayerImage.strokeColor = ContextCompat.getColor(context, R.color.divider) // divider_dark -> divider
                    binding.cardPlayerImage.strokeWidth = dpToPx(1)
                }
            }

            binding.root.setOnClickListener { onPlayerClick(item) }
        }

        private fun TextView.textStyleBold() {
            this.typeface = android.graphics.Typeface.create(this.typeface, android.graphics.Typeface.BOLD)
        }

        private fun TextView.textStyleNormal() {
            this.typeface = android.graphics.Typeface.create(this.typeface, android.graphics.Typeface.NORMAL)
        }

        private fun dpToPx(dp: Float): Int {
            val density = binding.root.context.resources.displayMetrics.density
            return (dp * density).toInt()
        }

        private fun dpToPx(dp: Int): Int {
            val density = binding.root.context.resources.displayMetrics.density
            return (dp * density).toInt()
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<TopPlayerItemDto>() {
            override fun areItemsTheSame(oldItem: TopPlayerItemDto, newItem: TopPlayerItemDto): Boolean {
                return oldItem.player.id == newItem.player.id
            }

            override fun areContentsTheSame(oldItem: TopPlayerItemDto, newItem: TopPlayerItemDto): Boolean {
                return oldItem == newItem
            }
        }
    }
}
