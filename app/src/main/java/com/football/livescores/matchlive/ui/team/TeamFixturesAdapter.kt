package com.football.livescores.matchlive.ui.team

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.remote.model.TeamFixtureItemDto
import com.football.livescores.matchlive.databinding.ItemTeamFixtureBinding

class TeamFixturesAdapter(
    private val currentTeamId: Int,
    private val onMatchClick: (TeamFixtureItemDto) -> Unit
) : ListAdapter<TeamFixtureItemDto, TeamFixturesAdapter.ViewHolder>(FixtureDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTeamFixtureBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTeamFixtureBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TeamFixtureItemDto) {
            val context = binding.root.context

            binding.root.setOnClickListener {
                onMatchClick(item)
            }

            val leagueName = item.league?.name ?: "Football"
            val round = item.round ?: ""
            binding.tvLeagueName.text = if (round.isNotEmpty()) "$leagueName - $round" else leagueName

            binding.tvMatchDate.text = formatDate(item.date ?: item.fixtureDate)

            Glide.with(context)
                .load(item.league?.logo)
                .placeholder(R.drawable.nodata)
                .into(binding.ivLeagueLogo)

            // Home Team
            val homeTeam = item.teams?.home
            binding.tvHomeTeamName.text = homeTeam?.name ?: "Home"
            Glide.with(context)
                .load(homeTeam?.logo)
                .placeholder(R.mipmap.ic_launcher)
                .into(binding.ivHomeTeamLogo)

            // Away Team
            val awayTeam = item.teams?.away
            binding.tvAwayTeamName.text = awayTeam?.name ?: "Away"
            Glide.with(context)
                .load(awayTeam?.logo)
                .placeholder(R.mipmap.ic_launcher)
                .into(binding.ivAwayTeamLogo)

            // Goals & Score
            val homeGoals = item.goals?.home ?: item.score?.fulltime?.home ?: 0
            val awayGoals = item.goals?.away ?: item.score?.fulltime?.away ?: 0
            binding.tvScore.text = "$homeGoals - $awayGoals"

            val htHome = item.score?.halftime?.home
            val htAway = item.score?.halftime?.away
            val htText = if (htHome != null && htAway != null) "FT ($htHome-$htAway)" else (item.status?.short ?: "FT")
            binding.tvStatusShort.text = htText

            // W / D / L Result Badge logic
            val isHome = homeTeam?.id == currentTeamId
            val isAway = awayTeam?.id == currentTeamId

            when {
                homeGoals == awayGoals -> {
                    binding.tvResultBadge.text = "D"
                    binding.tvResultBadge.setTextColor(Color.parseColor("#080E16"))
                    binding.tvResultBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BBDB38"))
                }
                (isHome && homeGoals > awayGoals) || (isAway && awayGoals > homeGoals) -> {
                    binding.tvResultBadge.text = "W"
                    binding.tvResultBadge.setTextColor(Color.parseColor("#080E16"))
                    binding.tvResultBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4EF36A"))
                }
                else -> {
                    binding.tvResultBadge.text = "L"
                    binding.tvResultBadge.setTextColor(Color.WHITE)
                    binding.tvResultBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E4234D"))
                }
            }
        }

        private fun formatDate(dateStr: String?): String {
            if (dateStr.isNullOrEmpty()) return ""
            return try {
                val clean = dateStr.replace("T", " ")
                if (clean.length >= 10) clean.substring(0, 10) else clean
            } catch (e: Exception) {
                dateStr
            }
        }
    }

    private class FixtureDiffCallback : DiffUtil.ItemCallback<TeamFixtureItemDto>() {
        override fun areItemsTheSame(oldItem: TeamFixtureItemDto, newItem: TeamFixtureItemDto): Boolean {
            return oldItem.getRealFixtureId == newItem.getRealFixtureId
        }

        override fun areContentsTheSame(oldItem: TeamFixtureItemDto, newItem: TeamFixtureItemDto): Boolean {
            return oldItem == newItem
        }
    }
}
