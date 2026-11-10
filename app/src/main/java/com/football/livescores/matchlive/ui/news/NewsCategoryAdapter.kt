package com.football.livescores.matchlive.ui.news

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.remote.model.NewsCategoryDto
import com.football.livescores.matchlive.databinding.ItemNewsCategoryBinding

class NewsCategoryAdapter(
    private val onCategorySelected: (NewsCategoryDto) -> Unit
) : ListAdapter<NewsCategoryDto, NewsCategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    var selectedCategoryId: String = "all"
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemNewsCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, item.id.equals(selectedCategoryId, ignoreCase = true))
    }

    inner class CategoryViewHolder(private val binding: ItemNewsCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: NewsCategoryDto, isSelected: Boolean) {
            val context = binding.root.context
            val categoryDisplayName = when (category.id.lowercase()) {
                "all" -> context.getString(R.string.news_category_all)
                "transfers" -> context.getString(R.string.news_category_transfers)
                "worldcup", "world-cup" -> context.getString(R.string.news_category_world_cup)
                "premier-league", "premierleague" -> context.getString(R.string.news_category_premier_league)
                "champions-league", "championsleague" -> context.getString(R.string.news_category_champions_league)
                else -> category.name
            }
            binding.tvCategoryName.text = categoryDisplayName

            if (isSelected) {
                binding.tvCategoryName.setBackgroundResource(R.drawable.bg_news_tab_selected)
                binding.tvCategoryName.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
                binding.tvCategoryName.setTypeface(null, Typeface.BOLD)
            } else {
                binding.tvCategoryName.setBackgroundResource(R.drawable.bg_news_tab_unselected)
                binding.tvCategoryName.setTextColor(ContextCompat.getColor(context, R.color.text_white))
                binding.tvCategoryName.setTypeface(null, Typeface.BOLD)
            }

            val clickListener = View.OnClickListener {
                if (!selectedCategoryId.equals(category.id, ignoreCase = true)) {
                    selectedCategoryId = category.id
                    onCategorySelected(category)
                }
            }

            binding.root.setOnClickListener(clickListener)
            binding.tvCategoryName.setOnClickListener(clickListener)
        }
    }

    private class CategoryDiffCallback : DiffUtil.ItemCallback<NewsCategoryDto>() {
        override fun areItemsTheSame(oldItem: NewsCategoryDto, newItem: NewsCategoryDto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NewsCategoryDto, newItem: NewsCategoryDto): Boolean {
            return oldItem == newItem
        }
    }
}
