package com.football.livescores.matchlive.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import androidx.core.content.ContextCompat
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.local.entity.CachedMatchEntity
import com.football.livescores.matchlive.databinding.ItemLeagueHeaderBinding
import com.football.livescores.matchlive.databinding.ItemMatchBinding
import com.football.livescores.matchlive.databinding.LayoutNativeNoMediaBinding
import androidx.appcompat.app.AppCompatActivity
import com.football.livescores.matchlive.data.remote.RemoteConfigManager
import com.football.livescores.matchlive.utils.Admob
import com.football.livescores.matchlive.utils.NativeCallback

sealed class MatchListItem {
    data class LeagueHeader(val id: Int, val name: String, val logo: String) : MatchListItem()
    data class MatchItem(
        val match: CachedMatchEntity,
        val isFavorite: Boolean = false,
        val isReminderSet: Boolean = false
    ) : MatchListItem()
    data class NativeAd(
        val id: String,
        var nativeAd: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd? = null
    ) : MatchListItem()
}

class MatchAdapter(
    private val onMatchClick: (CachedMatchEntity, Boolean) -> Unit,
    private val onFavoriteClick: (CachedMatchEntity) -> Unit,
    private val onReminderClick: (CachedMatchEntity) -> Unit
) : ListAdapter<MatchListItem, RecyclerView.ViewHolder>(DiffCallback) {

    var isUpcomingTab: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var showFavoriteInStandardLayout: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    companion object {
        private const val TYPE_LEAGUE_HEADER = 0
        private const val TYPE_MATCH_ITEM = 1
        private const val TYPE_NATIVE_AD = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is MatchListItem.LeagueHeader -> TYPE_LEAGUE_HEADER
            is MatchListItem.MatchItem -> TYPE_MATCH_ITEM
            is MatchListItem.NativeAd -> TYPE_NATIVE_AD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_LEAGUE_HEADER -> {
                val binding = ItemLeagueHeaderBinding.inflate(inflater, parent, false)
                LeagueHeaderViewHolder(binding)
            }
            TYPE_MATCH_ITEM -> {
                val binding = ItemMatchBinding.inflate(inflater, parent, false)
                MatchItemViewHolder(binding)
            }
            TYPE_NATIVE_AD -> {
                val binding = LayoutNativeNoMediaBinding.inflate(inflater, parent, false)
                NativeAdViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is MatchListItem.LeagueHeader -> (holder as LeagueHeaderViewHolder).bind(item)
            is MatchListItem.MatchItem -> (holder as MatchItemViewHolder).bind(item)
            is MatchListItem.NativeAd -> (holder as NativeAdViewHolder).bind(item)
        }
    }

    inner class LeagueHeaderViewHolder(private val binding: ItemLeagueHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MatchListItem.LeagueHeader) {
            binding.tvLeagueTitle.text = item.name
            Glide.with(binding.root.context)
                .load(item.logo)
                .into(binding.ivLeagueLogo)
        }
    }

    inner class MatchItemViewHolder(private val binding: ItemMatchBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MatchListItem.MatchItem) {
            val match = item.match
            val isUpcoming = isUpcomingTab && (match.statusShort == "NS" || match.statusShort == "TBD")

            if (isUpcoming) {
                binding.layoutLiveFinished.visibility = View.GONE
                binding.layoutUpcoming.visibility = View.VISIBLE

                // Format match time
                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getDefault()
                }
                val timeStr = sdf.format(java.util.Date(match.dateTimestamp * 1000))
                binding.tvMatchTimeUpcoming.text = timeStr

                binding.tvHomeNameUpcoming.text = match.homeTeamName
                binding.tvAwayNameUpcoming.text = match.awayTeamName

                Glide.with(binding.root.context).load(match.homeTeamLogo).into(binding.ivHomeLogoUpcoming)
                Glide.with(binding.root.context).load(match.awayTeamLogo).into(binding.ivAwayLogoUpcoming)

                // Bind favorite icon state
                binding.ivFavoriteUpcoming.visibility = View.GONE

                // Bind reminder icon state (only visible for upcoming matches in the future)
                val isUpcomingFuture = isUpcoming && (match.dateTimestamp * 1000 > System.currentTimeMillis())
                binding.ivReminderUpcoming.isVisible = isUpcomingFuture
                if (isUpcomingFuture) {
                    val isRemind = item.isReminderSet
                    binding.ivReminderUpcoming.setImageResource(
                        if (isRemind) R.drawable.ic_bell_active else R.drawable.ic_bell
                    )
                    binding.ivReminderUpcoming.setColorFilter(
                        ContextCompat.getColor(
                            binding.ivReminderUpcoming.context,
                            if (isRemind) R.color.accent_green else R.color.text_muted
                        )
                    )
                    binding.ivReminderUpcoming.setOnClickListener { onReminderClick(match) }
                }

                binding.layoutPredictButton.isVisible = isUpcoming
                binding.btnPredict.setOnClickListener { onMatchClick(match, true) }
                binding.layoutMatchDetailsClickUpcoming.setOnClickListener { onMatchClick(match, false) }
                binding.ivFavoriteUpcoming.setOnClickListener { onFavoriteClick(match) }

            } else {
                binding.layoutLiveFinished.visibility = View.VISIBLE
                binding.layoutUpcoming.visibility = View.GONE

                binding.tvHomeName.text = match.homeTeamName
                binding.tvAwayName.text = match.awayTeamName

                // Home & Away team Logos
                Glide.with(binding.root.context).load(match.homeTeamLogo).into(binding.ivHomeLogo)
                Glide.with(binding.root.context).load(match.awayTeamLogo).into(binding.ivAwayLogo)

                val isLive = match.statusShort in listOf("1H", "2H", "HT", "ET", "BT", "P", "LIVE")
                val isUpcomingStatus = match.statusShort == "NS" || match.statusShort == "TBD"

                if (isLive) {
                    binding.tvHomeScore.visibility = View.VISIBLE
                    binding.tvAwayScore.visibility = View.VISIBLE
                    binding.tvHomeScore.text = match.goalsHome?.toString() ?: "-"
                    binding.tvAwayScore.text = match.goalsAway?.toString() ?: "-"
                    binding.tvScoreSeparator.text = "-"
                    binding.tvScoreSeparator.setTextColor(ContextCompat.getColor(binding.root.context, R.color.textPrimary))
                    binding.tvHomeScore.setTextColor(ContextCompat.getColor(binding.root.context, R.color.textPrimary))
                    binding.tvAwayScore.setTextColor(ContextCompat.getColor(binding.root.context, R.color.textPrimary))
                    binding.layoutScoreBox.background = null

                    binding.tvMatchStatus.text = match.elapsed?.let { "$it'" } ?: "LIVE"
                    binding.livePulse.isVisible = true
                    binding.layoutLiveExtra.isVisible = false
                } else if (isUpcomingStatus) {
                    binding.tvHomeScore.visibility = View.GONE
                    binding.tvAwayScore.visibility = View.GONE
                    binding.tvScoreSeparator.text = "VS"
                    binding.tvScoreSeparator.setTextColor(ContextCompat.getColor(binding.root.context, R.color.text_muted))
                    binding.layoutScoreBox.background = null

                    binding.livePulse.isVisible = false
                    binding.layoutLiveExtra.isVisible = false
                    
                    // Format relative kickoff date/time
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
                } else {
                    binding.tvHomeScore.visibility = View.VISIBLE
                    binding.tvAwayScore.visibility = View.VISIBLE
                    binding.tvHomeScore.text = match.goalsHome?.toString() ?: "-"
                    binding.tvAwayScore.text = match.goalsAway?.toString() ?: "-"
                    binding.tvScoreSeparator.text = "-"
                    binding.tvScoreSeparator.setTextColor(ContextCompat.getColor(binding.root.context, R.color.textPrimary))
                    binding.tvHomeScore.setTextColor(ContextCompat.getColor(binding.root.context, R.color.textPrimary))
                    binding.tvAwayScore.setTextColor(ContextCompat.getColor(binding.root.context, R.color.textPrimary))
                    binding.layoutScoreBox.background = null

                    binding.livePulse.isVisible = false
                    binding.layoutLiveExtra.isVisible = false

                    // Format relative kickoff date/time for finished matches
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

                // Bind favorite icon state
                binding.ivFavorite.visibility = View.GONE

                // Bind reminder icon state (only visible for upcoming matches in the future)
                val isUpcomingFuture = isUpcomingStatus && (match.dateTimestamp * 1000 > System.currentTimeMillis())
                binding.ivReminder.isVisible = isUpcomingFuture
                if (isUpcomingFuture) {
                    val isRemind = item.isReminderSet
                    binding.ivReminder.setImageResource(
                        if (isRemind) R.drawable.ic_bell_active else R.drawable.ic_bell
                    )
                    binding.ivReminder.setColorFilter(
                        ContextCompat.getColor(
                            binding.ivReminder.context,
                            if (isRemind) R.color.accent_green else R.color.text_muted
                        )
                    )
                    binding.ivReminder.setOnClickListener { onReminderClick(match) }
                }

                binding.layoutMatchDetailsClick.setOnClickListener { onMatchClick(match, false) }
                binding.ivFavorite.setOnClickListener { onFavoriteClick(match) }
            }
        }
    }

    inner class NativeAdViewHolder(private val binding: LayoutNativeNoMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MatchListItem.NativeAd) {
            val cachedAd = item.nativeAd
            if (cachedAd != null) {
                renderNativeAd(cachedAd)
            } else {
                val context = binding.root.context
                val adId = try {
                    RemoteConfigManager.getInstance()
                        .getAdId("native_all", context.getString(R.string.native_all))
                } catch (e: Exception) {
                    context.getString(R.string.native_all)
                }
                Admob.getInstance().loadNativeAds(
                    context,
                    adId,
                    1,
                    object : NativeCallback() {
                        override fun onAdFailedToLoad() {
                            super.onAdFailedToLoad()
                            val layoutParams = binding.root.layoutParams
                            layoutParams.height = 0
                            binding.root.layoutParams = layoutParams
                        }

                        override fun onNativeAdLoaded(loadedAd: NativeAd?) {
                            super.onNativeAdLoaded(loadedAd)
                            if (loadedAd != null) {
                                item.nativeAd = loadedAd
                                renderNativeAd(loadedAd)
                            }
                        }
                    }
                )
            }
        }

        private fun renderNativeAd(nativeAd: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd) {
            // Restore height in case it was previously collapsed
            val layoutParams = binding.root.layoutParams
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            binding.root.layoutParams = layoutParams

            // Bind the loaded native ad to our NativeAdView (which is the root of binding)
            val adView = binding.root
            
            // Hide close button if present
            val closeButton = adView.findViewById<View>(R.id.close)
            closeButton?.visibility = View.GONE

            Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<MatchListItem>() {
        override fun areItemsTheSame(oldItem: MatchListItem, newItem: MatchListItem): Boolean {
            return when {
                oldItem is MatchListItem.LeagueHeader && newItem is MatchListItem.LeagueHeader ->
                    oldItem.id == newItem.id
                oldItem is MatchListItem.MatchItem && newItem is MatchListItem.MatchItem ->
                    oldItem.match.id == newItem.match.id
                oldItem is MatchListItem.NativeAd && newItem is MatchListItem.NativeAd ->
                    oldItem.id == newItem.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: MatchListItem, newItem: MatchListItem): Boolean {
            return oldItem == newItem
        }
    }
}
