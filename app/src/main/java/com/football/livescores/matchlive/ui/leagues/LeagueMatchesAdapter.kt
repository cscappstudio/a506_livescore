package com.football.livescores.matchlive.ui.leagues

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.remote.model.MatchItemDto
import com.football.livescores.matchlive.databinding.ItemLeagueMatchCardBinding
import com.football.livescores.matchlive.databinding.ItemLeagueMatchDateHeaderBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

sealed class LeagueMatchListItem {
    data class DateHeader(val dateDisplay: String, val dateRaw: String) : LeagueMatchListItem()
    data class Match(val match: MatchItemDto) : LeagueMatchListItem()
}

class LeagueMatchesAdapter(
    private val onMatchClick: (MatchItemDto) -> Unit
) : ListAdapter<LeagueMatchListItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_MATCH = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<LeagueMatchListItem>() {
            override fun areItemsTheSame(oldItem: LeagueMatchListItem, newItem: LeagueMatchListItem): Boolean {
                return when {
                    oldItem is LeagueMatchListItem.DateHeader && newItem is LeagueMatchListItem.DateHeader ->
                        oldItem.dateRaw == newItem.dateRaw
                    oldItem is LeagueMatchListItem.Match && newItem is LeagueMatchListItem.Match ->
                        oldItem.match.fixture.id == newItem.match.fixture.id
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: LeagueMatchListItem, newItem: LeagueMatchListItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is LeagueMatchListItem.DateHeader -> TYPE_HEADER
            is LeagueMatchListItem.Match -> TYPE_MATCH
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val binding = ItemLeagueMatchDateHeaderBinding.inflate(inflater, parent, false)
            HeaderViewHolder(binding)
        } else {
            val binding = ItemLeagueMatchCardBinding.inflate(inflater, parent, false)
            MatchViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is LeagueMatchListItem.DateHeader -> (holder as HeaderViewHolder).bind(item)
            is LeagueMatchListItem.Match -> (holder as MatchViewHolder).bind(item.match)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemLeagueMatchDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LeagueMatchListItem.DateHeader) {
            binding.tvMatchDateHeader.text = item.dateDisplay
        }
    }

    inner class MatchViewHolder(private val binding: ItemLeagueMatchCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MatchItemDto) {
            val context = binding.root.context
            val round = item.league.round ?: ""
            binding.tvRoundName.text = round.ifEmpty { item.league.name }

            val statusShort = item.fixture.status.short.uppercase()
            val statusLong = item.fixture.status.long

            binding.tvHomeName.text = item.teams.home.name
            binding.tvAwayName.text = item.teams.away.name

            Glide.with(context)
                .load(item.teams.home.logo)
                .placeholder(R.drawable.nodata)
                .error(R.drawable.nodata)
                .into(binding.ivHomeLogo)

            Glide.with(context)
                .load(item.teams.away.logo)
                .placeholder(R.drawable.nodata)
                .error(R.drawable.nodata)
                .into(binding.ivAwayLogo)

            when (statusShort) {
                "FT", "AET", "PEN" -> {
                    binding.tvMatchStatus.text = statusShort
                    binding.tvHomeScore.text = (item.goals.home ?: 0).toString()
                    binding.tvAwayScore.text = (item.goals.away ?: 0).toString()
                }
                "1H", "2H", "HT", "LIVE" -> {
                    val elapsed = item.fixture.status.elapsed
                    binding.tvMatchStatus.text = if (elapsed != null) "${elapsed}'" else statusShort
                    binding.tvHomeScore.text = (item.goals.home ?: 0).toString()
                    binding.tvAwayScore.text = (item.goals.away ?: 0).toString()
                }
                else -> {
                    // Not started or scheduled
                    val formattedTime = formatTime(item.fixture.date)
                    binding.tvMatchStatus.text = formattedTime
                    binding.tvHomeScore.text = "-"
                    binding.tvAwayScore.text = "-"
                }
            }

            binding.root.setOnClickListener {
                onMatchClick(item)
            }
        }

        private fun formatTime(dateStr: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(dateStr)
                if (date != null) {
                    val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    outputFormat.timeZone = TimeZone.getDefault()
                    outputFormat.format(date)
                } else {
                    "NS"
                }
            } catch (e: Exception) {
                "NS"
            }
        }
    }
}
