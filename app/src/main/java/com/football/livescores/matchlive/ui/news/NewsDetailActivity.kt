package com.football.livescores.matchlive.ui.news

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.base.BaseActivity
import com.football.livescores.matchlive.data.remote.model.NewsItemDto
import com.football.livescores.matchlive.data.repository.NewsRepository
import com.football.livescores.matchlive.databinding.ActivityNewsDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.view.LayoutInflater
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.data.remote.RemoteConfigManager
import com.football.livescores.matchlive.utils.SharePreferenceUtils
import com.football.livescores.matchlive.utils.NativeCallback
import com.football.livescores.matchlive.utils.Admob

@AndroidEntryPoint
class NewsDetailActivity : BaseActivity() {

    companion object {
        private const val EXTRA_NEWS_ID = "extra_news_id"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_SUMMARY = "extra_summary"
        private const val EXTRA_CONTENT = "extra_content"
        private const val EXTRA_IMAGE_URL = "extra_image_url"
        private const val EXTRA_CATEGORY = "extra_category"
        private const val EXTRA_TIME = "extra_time"

        fun startActivity(context: Context, item: NewsItemDto) {
            val intent = Intent(context, NewsDetailActivity::class.java).apply {
                putExtra(EXTRA_NEWS_ID, item.id ?: "")
                putExtra(EXTRA_TITLE, item.title ?: "")
                putExtra(EXTRA_SUMMARY, item.summary ?: "")
                putExtra(EXTRA_CONTENT, item.content ?: "")
                putExtra(EXTRA_IMAGE_URL, item.imageUrl ?: "")
                putExtra(EXTRA_CATEGORY, item.category ?: item.categories?.firstOrNull() ?: "NEWS")
                putExtra(EXTRA_TIME, item.publishedAt ?: "")
            }
            context.startActivity(intent)
        }
    }

    @Inject
    lateinit var newsRepository: NewsRepository

    @Inject
    lateinit var limitManager: RequestLimitManager

    private lateinit var binding: ActivityNewsDetailBinding
    private var currentTitle: String = ""
    private var currentImageUrl: String = ""

    override fun bind() {
        binding = ActivityNewsDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val newsId = intent.getStringExtra(EXTRA_NEWS_ID) ?: ""
        val initialTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val initialSummary = intent.getStringExtra(EXTRA_SUMMARY) ?: ""
        val initialContent = intent.getStringExtra(EXTRA_CONTENT) ?: ""
        val initialImageUrl = intent.getStringExtra(EXTRA_IMAGE_URL) ?: ""
        val initialCategory = intent.getStringExtra(EXTRA_CATEGORY) ?: "NEWS"
        val initialTime = intent.getStringExtra(EXTRA_TIME) ?: ""

        setupClickListeners()
        populateData(initialTitle, initialSummary, initialContent, initialImageUrl, initialCategory, initialTime, emptyList())

        if (!SharePreferenceUtils.isOrganic(baseContext)) {
            loadNativeAd()
        } else {
            binding.frAds.removeAllViews()
            binding.frAds.visibility = View.GONE
        }

        if (newsId.isNotEmpty()) {
            fetchNewsDetailFromApi(newsId)
        }
    }


    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnShare.setOnClickListener {
            val appLink = "https://play.google.com/store/apps/details?id=$packageName"
            val textToShare = buildString {
                if (currentTitle.isNotEmpty()) {
                    append(currentTitle)
                    append("\n\n")
                }
                if (currentImageUrl.isNotEmpty()) {
                    append(currentImageUrl)
                    append("\n\n")
                }
                append(appLink)
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, currentTitle.ifEmpty { getString(R.string.news_share_title) })
                putExtra(Intent.EXTRA_TEXT, textToShare)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.news_share_title)))
        }
    }

    private fun fetchNewsDetailFromApi(newsId: String) {
        lifecycleScope.launch {
            binding.progressBarDetail.isVisible = true
            val lang = com.football.livescores.matchlive.utils.SystemUtil.getPreLanguage(this@NewsDetailActivity).ifEmpty { "en" }
            val result = newsRepository.getNewsDetail(newsId, lang = lang)
            binding.progressBarDetail.isVisible = false
            result.onSuccess { detailItem ->
                populateData(
                    title = detailItem.title ?: "",
                    summary = detailItem.summary ?: "",
                    content = detailItem.content ?: "",
                    imageUrl = detailItem.imageUrl ?: "",
                    category = detailItem.category ?: detailItem.categories?.firstOrNull() ?: "NEWS",
                    time = detailItem.publishedAt ?: "",
                    tags = detailItem.tags ?: emptyList()
                )
            }
        }
    }


    private fun populateData(
        title: String,
        summary: String,
        content: String,
        imageUrl: String,
        category: String,
        time: String,
        tags: List<String>
    ) {
        currentTitle = title
        currentImageUrl = imageUrl
        binding.tvDetailTitle.text = title
        binding.tvDetailCategory.text = category.uppercase()
        binding.tvDetailTime.text = formatPublishedTime(time)

        val displayContent = if (content.length >= summary.length && content.isNotEmpty()) content else summary
        binding.tvDetailContent.text = displayContent.ifEmpty { title }

        if (imageUrl.isNotEmpty()) {
            binding.ivDetailImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.nodata)
                .error(R.drawable.nodata)
                .into(binding.ivDetailImage)
        } else {
            binding.ivDetailImage.visibility = View.GONE
        }

        renderTags(tags)
    }

    private fun renderTags(tags: List<String>) {
        binding.chipGroupTags.removeAllViews()
        if (tags.isEmpty()) {
            binding.chipGroupTags.visibility = View.GONE
            return
        }
        binding.chipGroupTags.visibility = View.GONE
        tags.forEach { tagText ->
            val chip = Chip(this).apply {
                text = tagText
                setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.card_dark)))
                setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.divider_dark)))
                chipStrokeWidth = 2f
                setTextColor(ContextCompat.getColor(context, R.color.text_white))
                textSize = 11f
                isClickable = false
                isCheckable = false
            }
            binding.chipGroupTags.addView(chip)
        }
    }

    private fun formatPublishedTime(timeString: String?): String {
        if (timeString.isNullOrEmpty()) return ""
        return try {
            val clean = timeString.replace("T", " ")
            if (clean.length >= 16) {
                clean.substring(0, 16)
            } else {
                clean
            }
        } catch (e: Exception) {
            timeString ?: ""
        }
    }

    private fun loadNativeAd() {
        if (limitManager.isPremium()) {
            binding.frAds.visibility = View.GONE
            return
        }

        val adId = try {
            RemoteConfigManager.getInstance().getAdId("native_all", getString(R.string.native_all))
        } catch (e: Exception) {
            getString(R.string.native_all)
        }

        if (adId.isNotEmpty()) {
            binding.frAds.visibility = View.VISIBLE
            val shimmerView = LayoutInflater.from(this).inflate(R.layout.layout_shimmer_league, binding.frAds, false)
            binding.frAds.removeAllViews()
            binding.frAds.addView(shimmerView)

            Admob.getInstance().loadNativeAds(
                this,
                adId,
                1,
                object : NativeCallback() {
                    override fun onNativeAdLoaded(nativeAd: NativeAd?) {
                        super.onNativeAdLoaded(nativeAd)

                        val adView = LayoutInflater.from(this@NewsDetailActivity)
                            .inflate(R.layout.layout_native_league, null) as NativeAdView

                        binding.frAds.removeAllViews()
                        binding.frAds.addView(adView)

                        Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)
                    }

                    override fun onAdFailedToLoad() {
                        super.onAdFailedToLoad()
                        binding.frAds.visibility = View.GONE
                    }
                }
            )
        } else {
            binding.frAds.visibility = View.GONE
        }
    }
}
