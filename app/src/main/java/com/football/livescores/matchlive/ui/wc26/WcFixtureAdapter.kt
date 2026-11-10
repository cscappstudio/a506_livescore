package com.football.livescores.matchlive.ui.wc26

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.local.entity.CachedMatchEntity
import com.football.livescores.matchlive.data.remote.model.MatchItemDto
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WcFixtureAdapter(
    private val context: Context,
    private val onMatchClick: (MatchItemDto, Boolean) -> Unit,
    private val onReminderClick: (MatchItemDto, ImageView) -> Unit,
    private val onFavoriteClick: (MatchItemDto, ImageView) -> Unit,
    private val isReminderSet: (Int) -> Boolean,
    private val isFavoriteSet: (Int) -> Boolean,
    private val onAdViewReady: (View, String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_FIXTURE = 1
        const val VIEW_TYPE_AD = 2
        const val VIEW_TYPE_EMPTY = 3
    }

    var isUpcomingTab: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    private var items = listOf<WcFixtureItem>()

    fun submitList(newItems: List<WcFixtureItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is WcFixtureItem.HeaderItem -> VIEW_TYPE_HEADER
            is WcFixtureItem.MatchItem -> VIEW_TYPE_FIXTURE
            is WcFixtureItem.AdItem -> VIEW_TYPE_AD
            is WcFixtureItem.EmptyItem -> VIEW_TYPE_EMPTY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                HeaderViewHolder(inflater.inflate(R.layout.item_wc_date_header, parent, false))
            }
            VIEW_TYPE_FIXTURE -> {
                FixtureViewHolder(inflater.inflate(R.layout.item_wc_fixture, parent, false))
            }
            VIEW_TYPE_AD -> {
                AdViewHolder(inflater.inflate(R.layout.layout_native_no_media, parent, false))
            }
            VIEW_TYPE_EMPTY -> {
                val emptyView = com.football.livescores.matchlive.ui.custom.EmptyStateView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    text = context.getString(R.string.empty_fixtures)
                    setPadding(0, 32 * resources.displayMetrics.density.toInt(), 0, 0)
                }
                EmptyViewHolder(emptyView)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is HeaderViewHolder -> holder.bind(item as WcFixtureItem.HeaderItem)
            is FixtureViewHolder -> holder.bind(item as WcFixtureItem.MatchItem)
            is AdViewHolder -> holder.bind(item as WcFixtureItem.AdItem)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDateHeader: TextView = itemView.findViewById(R.id.tvDateHeader)
        fun bind(item: WcFixtureItem.HeaderItem) {
            tvDateHeader.text = item.dateText
        }
    }

    inner class FixtureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFixtureRound: TextView = itemView.findViewById(R.id.tvFixtureRound)
        private val tvFixtureStadium: TextView = itemView.findViewById(R.id.tvFixtureStadium)
        private val tvFixtureTeam1: TextView = itemView.findViewById(R.id.tvFixtureTeam1)
        private val tvFixtureTeam2: TextView = itemView.findViewById(R.id.tvFixtureTeam2)
        private val ivFixtureHomeLogo: ImageView = itemView.findViewById(R.id.ivFixtureHomeLogo)
        private val ivFixtureAwayLogo: ImageView = itemView.findViewById(R.id.ivFixtureAwayLogo)
        private val tvVS: TextView = itemView.findViewById(R.id.tvVS)
        private val tvFixtureTime: TextView = itemView.findViewById(R.id.tvFixtureTime)
        private val ivReminder: ImageView = itemView.findViewById(R.id.ivReminder)
        private val ivFavorite: ImageView = itemView.findViewById(R.id.ivFavorite)

        private val layoutWcLiveFinished: View = itemView.findViewById(R.id.layoutWcLiveFinished)
        private val layoutWcUpcoming: View = itemView.findViewById(R.id.layoutWcUpcoming)
        private val layoutWcScoreBox: View = itemView.findViewById(R.id.layoutWcScoreBox)

        // Upcoming views
        private val tvMatchTimeUpcoming: TextView = itemView.findViewById(R.id.tvMatchTimeUpcoming)
        private val tvHomeNameUpcoming: TextView = itemView.findViewById(R.id.tvHomeNameUpcoming)
        private val tvAwayNameUpcoming: TextView = itemView.findViewById(R.id.tvAwayNameUpcoming)
        private val ivHomeLogoUpcoming: ImageView = itemView.findViewById(R.id.ivHomeLogoUpcoming)
        private val ivAwayLogoUpcoming: ImageView = itemView.findViewById(R.id.ivAwayLogoUpcoming)
        private val layoutMatchDetailsClickUpcoming: View = itemView.findViewById(R.id.layoutMatchDetailsClickUpcoming)
        private val layoutPredictButton: View = itemView.findViewById(R.id.layoutPredictButton)
        private val btnPredict: View = itemView.findViewById(R.id.btnPredict)
        private val ivReminderUpcoming: ImageView = itemView.findViewById(R.id.ivReminderUpcoming)
        private val ivFavoriteUpcoming: ImageView = itemView.findViewById(R.id.ivFavoriteUpcoming)

        fun bind(item: WcFixtureItem.MatchItem) {
            val match = item.match
            val groupStageString = context.getString(R.string.wc_group_stage_name)
            val rawRound = match.league.round ?: groupStageString
            val displayRound = rawRound.replace("Group Stage", groupStageString).uppercase(Locale.getDefault())
            tvFixtureRound.text = displayRound

            val venueName = match.fixture.venue?.name ?: context.getString(R.string.wc_default_stadium)
            val venueCity = match.fixture.venue?.city ?: ""
            tvFixtureStadium.text = "$venueName, $venueCity"

            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val localTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
            val kickoffTime = try {
                val dateObj = parser.parse(match.fixture.date)
                if (dateObj != null) localTimeFormat.format(dateObj) else ""
            } catch (e: Exception) {
                ""
            }

            val isUpcoming = isUpcomingTab && (match.fixture.status.short == "NS" || match.fixture.status.short == "TBD")

            if (isUpcoming) {
                itemView.setOnClickListener(null)
                itemView.isClickable = false

                layoutWcLiveFinished.visibility = View.GONE
                layoutWcUpcoming.visibility = View.VISIBLE

                tvMatchTimeUpcoming.text = kickoffTime

                tvHomeNameUpcoming.text = match.teams.home.name
                tvAwayNameUpcoming.text = match.teams.away.name

                Glide.with(itemView.context)
                    .load(match.teams.home.logo)
                    .placeholder(R.drawable.ic_favorite_border)
                    .into(ivHomeLogoUpcoming)
                ivHomeLogoUpcoming.imageTintList = null

                Glide.with(itemView.context)
                    .load(match.teams.away.logo)
                    .placeholder(R.drawable.ic_favorite_border)
                    .into(ivAwayLogoUpcoming)
                ivAwayLogoUpcoming.imageTintList = null

                val isUpcomingFuture = isUpcoming && (match.fixture.timestamp * 1000 > System.currentTimeMillis())
                ivReminderUpcoming.visibility = if (isUpcomingFuture) View.VISIBLE else View.GONE

                val isRemind = isReminderSet(match.fixture.id)
                ivReminderUpcoming.setImageResource(if (isRemind) R.drawable.ic_bell_active else R.drawable.ic_bell)
                ivReminderUpcoming.setColorFilter(ContextCompat.getColor(context, if (isRemind) R.color.accent_green else R.color.text_muted))

                ivFavoriteUpcoming.visibility = View.GONE

                layoutPredictButton.visibility = if (isUpcoming) View.VISIBLE else View.GONE
                if (isUpcoming) {
                    btnPredict.setOnClickListener { onMatchClick(match, true) }
                }

                layoutMatchDetailsClickUpcoming.setOnClickListener { onMatchClick(match, false) }
                ivReminderUpcoming.setOnClickListener { onReminderClick(match, ivReminderUpcoming) }
                ivFavoriteUpcoming.setOnClickListener { onFavoriteClick(match, ivFavoriteUpcoming) }

            } else {
                itemView.setOnClickListener { onMatchClick(match, false) }
                itemView.isClickable = true

                layoutWcLiveFinished.visibility = View.VISIBLE
                layoutWcUpcoming.visibility = View.GONE

                tvFixtureTeam1.text = match.teams.home.name
                tvFixtureTeam2.text = match.teams.away.name

                Glide.with(itemView.context)
                    .load(match.teams.home.logo)
                    .placeholder(R.drawable.ic_favorite_border)
                    .into(ivFixtureHomeLogo)
                ivFixtureHomeLogo.imageTintList = null

                Glide.with(itemView.context)
                    .load(match.teams.away.logo)
                    .placeholder(R.drawable.ic_favorite_border)
                    .into(ivFixtureAwayLogo)
                ivFixtureAwayLogo.imageTintList = null

                val homeGoal = match.goals.home
                val awayGoal = match.goals.away

                if (homeGoal != null && awayGoal != null) {
                    tvVS.text = "$homeGoal - $awayGoal"
                    tvVS.setTextColor(ContextCompat.getColor(context, R.color.primaryBlue))
                    layoutWcScoreBox.setBackgroundResource(R.drawable.bg_score_box_blue)
                } else {
                    tvVS.text = "VS"
                    tvVS.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                    layoutWcScoreBox.setBackgroundResource(R.drawable.bg_score_box_gray)
                }

                val statusShort = match.fixture.status.short
                val isLive = statusShort in listOf("1H", "2H", "HT", "ET", "BT", "P", "LIVE")
                val isFinished = statusShort in listOf("FT", "AET", "PEN")

                if (isLive) {
                    tvFixtureTime.text = match.fixture.status.elapsed?.let { "$it'" } ?: "LIVE"
                    tvFixtureTime.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
                } else if (isFinished) {
                    tvFixtureTime.text = "FT"
                    tvFixtureTime.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                } else {
                    tvFixtureTime.text = kickoffTime
                    tvFixtureTime.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                }

                val isUpcomingStatus = match.fixture.status.short == "NS" || match.fixture.status.short == "TBD"
                val isUpcomingFuture = isUpcomingStatus && (match.fixture.timestamp * 1000 > System.currentTimeMillis())
                ivReminder.visibility = if (isUpcomingFuture) View.VISIBLE else View.GONE

                val isRemind = isReminderSet(match.fixture.id)
                ivReminder.setImageResource(if (isRemind) R.drawable.ic_bell_active else R.drawable.ic_bell)
                ivReminder.setColorFilter(ContextCompat.getColor(context, if (isRemind) R.color.accent_green else R.color.text_muted))

                ivFavorite.visibility = View.GONE

                ivReminder.setOnClickListener { onReminderClick(match, ivReminder) }
                ivFavorite.setOnClickListener { onFavoriteClick(match, ivFavorite) }
            }
        }
    }

    inner class AdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: WcFixtureItem.AdItem) {
            itemView.visibility = View.INVISIBLE
            onAdViewReady(itemView, item.adId)
        }
    }

    inner class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
