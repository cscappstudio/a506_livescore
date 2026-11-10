package com.football.livescores.matchlive.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.local.entity.CachedMatchEntity
import com.football.livescores.matchlive.databinding.ItemFavoriteTeamLeagueBinding
import com.football.livescores.matchlive.databinding.ItemMatchBinding

sealed class SearchResultItem {
    data class Header(val title: String) : SearchResultItem()
    data class Match(val match: CachedMatchEntity, val isFavorite: Boolean) : SearchResultItem()
    data class Team(val id: Int, val name: String, val logo: String, val isFavorite: Boolean) : SearchResultItem()
    data class League(val id: Int, val name: String, val logo: String, val country: String, val isFavorite: Boolean) : SearchResultItem()
}

class SearchResultsAdapter(
    private val onMatchClick: (CachedMatchEntity) -> Unit,
    private val onMatchFavToggle: (CachedMatchEntity) -> Unit,
    private val onTeamFavToggle: (id: Int, name: String, logo: String) -> Unit,
    private val onLeagueFavToggle: (id: Int, name: String, logo: String, country: String) -> Unit
) : ListAdapter<SearchResultItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_MATCH = 1
        private const val TYPE_TEAM_LEAGUE = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<SearchResultItem>() {
            override fun areItemsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem): Boolean {
                if (oldItem::class != newItem::class) return false
                return when {
                    oldItem is SearchResultItem.Header && newItem is SearchResultItem.Header -> oldItem.title == newItem.title
                    oldItem is SearchResultItem.Match && newItem is SearchResultItem.Match -> oldItem.match.id == newItem.match.id
                    oldItem is SearchResultItem.Team && newItem is SearchResultItem.Team -> oldItem.id == newItem.id
                    oldItem is SearchResultItem.League && newItem is SearchResultItem.League -> oldItem.id == newItem.id
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SearchResultItem.Header -> TYPE_HEADER
            is SearchResultItem.Match -> TYPE_MATCH
            is SearchResultItem.Team, is SearchResultItem.League -> TYPE_TEAM_LEAGUE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                // Inline header view creation to avoid adding another XML file
                val view = inflater.inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
                view.setTextColor(ContextCompat.getColor(parent.context, R.color.accent_green))
                view.textSize = 15f
                view.setPadding(36, 32, 36, 12)
                view.setTypeface(null, android.graphics.Typeface.BOLD)
                HeaderViewHolder(view)
            }
            TYPE_MATCH -> {
                val binding = ItemMatchBinding.inflate(inflater, parent, false)
                MatchViewHolder(binding)
            }
            TYPE_TEAM_LEAGUE -> {
                val binding = ItemFavoriteTeamLeagueBinding.inflate(inflater, parent, false)
                TeamLeagueViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SearchResultItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SearchResultItem.Match -> (holder as MatchViewHolder).bind(item)
            is SearchResultItem.Team -> (holder as TeamLeagueViewHolder).bindTeam(item)
            is SearchResultItem.League -> (holder as TeamLeagueViewHolder).bindLeague(item)
        }
    }

    class HeaderViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(item: SearchResultItem.Header) {
            textView.text = item.title.uppercase()
        }
    }

    inner class MatchViewHolder(private val binding: ItemMatchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SearchResultItem.Match) {
            val match = item.match
            binding.tvHomeName.text = match.homeTeamName
            binding.tvAwayName.text = match.awayTeamName

            // Status Short
            binding.tvMatchStatus.text = match.statusShort
            binding.livePulse.isVisible = match.statusShort in listOf("1H", "2H", "HT", "ET", "BT", "P", "INT", "LIVE")

            // Scores
            if (match.goalsHome != null && match.goalsAway != null) {
                binding.tvHomeScore.text = match.goalsHome.toString()
                binding.tvAwayScore.text = match.goalsAway.toString()
                binding.tvHomeScore.isVisible = true
                binding.tvAwayScore.isVisible = true
            } else {
                binding.tvHomeScore.isVisible = false
                binding.tvAwayScore.isVisible = false
                // If scheduled, show relative date/time based on device timezone
                val matchDate = java.util.Date(match.dateTimestamp * 1000)
                val sdfToday = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getDefault()
                }
                val isToday = sdfToday.format(matchDate) == sdfToday.format(java.util.Date())
                val pattern = if (isToday) "HH:mm" else "dd/MM HH:mm"
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getDefault()
                }
                binding.tvMatchStatus.text = sdf.format(matchDate)
            }

            // Logos
            Glide.with(binding.ivHomeLogo.context)
                .load(match.homeTeamLogo)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(binding.ivHomeLogo)

            Glide.with(binding.ivAwayLogo.context)
                .load(match.awayTeamLogo)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(binding.ivAwayLogo)

            // Favorite match status
            binding.ivFavorite.visibility = View.GONE

            binding.layoutMatchDetailsClick.setOnClickListener {
                onMatchClick(match)
            }

            binding.ivFavorite.setOnClickListener {
                onMatchFavToggle(match)
            }
        }
    }

    inner class TeamLeagueViewHolder(private val binding: ItemFavoriteTeamLeagueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindTeam(item: SearchResultItem.Team) {
            binding.tvName.text = item.name
            binding.tvSubtitle.text = binding.root.context.getString(R.string.club)

            Glide.with(binding.ivLogo.context)
                .load(item.logo)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(binding.ivLogo)

            binding.ivStar.isVisible = false
        }

        fun bindLeague(item: SearchResultItem.League) {
            binding.tvName.text = item.name
            binding.tvSubtitle.text = binding.root.context.getString(R.string.league_with_country, item.country)

            Glide.with(binding.ivLogo.context)
                .load(item.logo)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(binding.ivLogo)

            binding.ivStar.isVisible = false
        }
    }
}
