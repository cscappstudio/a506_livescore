package com.football.livescores.matchlive.ui.news

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.remote.model.NewsItemDto
import com.football.livescores.matchlive.databinding.ItemNewsCardBinding
import com.football.livescores.matchlive.databinding.ItemNewsFeaturedBinding

class NewsAdapter(
    private val onNewsClick: (NewsItemDto) -> Unit
) : ListAdapter<NewsItemDto, RecyclerView.ViewHolder>(NewsDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_FEATURED = 0
        private const val VIEW_TYPE_STANDARD = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_FEATURED else VIEW_TYPE_STANDARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_FEATURED) {
            val binding = ItemNewsFeaturedBinding.inflate(inflater, parent, false)
            FeaturedViewHolder(binding)
        } else {
            val binding = ItemNewsCardBinding.inflate(inflater, parent, false)
            StandardViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is FeaturedViewHolder) {
            holder.bind(item)
        } else if (holder is StandardViewHolder) {
            holder.bind(item)
        }
    }

    inner class FeaturedViewHolder(private val binding: ItemNewsFeaturedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NewsItemDto) {
            binding.tvFeaturedTitle.text = item.title ?: ""
            binding.tvFeaturedSummary.text = item.summary ?: item.content ?: ""
            binding.tvFeaturedCategory.text = (item.category ?: item.categories?.firstOrNull() ?: "NEWS").uppercase()
            binding.tvFeaturedTime.text = formatPublishedTime(item.publishedAt)

            Glide.with(binding.root.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.nodata)
                .error(R.drawable.nodata)
                .into(binding.ivFeaturedImage)

            binding.cardFeatured.setOnClickListener {
                onNewsClick(item)
            }
        }
    }

    inner class StandardViewHolder(private val binding: ItemNewsCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NewsItemDto) {
            binding.tvTitle.text = item.title ?: ""
            binding.tvSummary.text = item.summary ?: item.content ?: ""
            binding.tvCategory.text = (item.category ?: item.categories?.firstOrNull() ?: "NEWS").uppercase()
            binding.tvTime.text = formatPublishedTime(item.publishedAt)

            Glide.with(binding.root.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.nodata)
                .error(R.drawable.nodata)
                .into(binding.ivNewsImage)

            binding.cardNews.setOnClickListener {
                onNewsClick(item)
            }
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
            timeString
        }
    }

    private class NewsDiffCallback : DiffUtil.ItemCallback<NewsItemDto>() {
        override fun areItemsTheSame(oldItem: NewsItemDto, newItem: NewsItemDto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NewsItemDto, newItem: NewsItemDto): Boolean {
            return oldItem == newItem
        }
    }
}
