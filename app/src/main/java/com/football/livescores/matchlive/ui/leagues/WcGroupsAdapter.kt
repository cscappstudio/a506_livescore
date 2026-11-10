package com.football.livescores.matchlive.ui.leagues

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.remote.model.StandingRowDto
import com.football.livescores.matchlive.databinding.ItemWcGroupBinding

data class WcGroupItem(
    val groupName: String,
    val rows: List<StandingRowDto>
)

class WcGroupsAdapter : ListAdapter<WcGroupItem, WcGroupsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWcGroupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemWcGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WcGroupItem) {
            val displayGroupName = item.groupName.replace("Group", "GROUP").uppercase()
            binding.tvGroupName.text = displayGroupName

            val inflater = LayoutInflater.from(binding.root.context)
            binding.layoutTeamRowsContainer.removeAllViews()

            item.rows.sortedBy { it.rank }.forEach { row ->
                val rowView = inflater.inflate(R.layout.item_wc_group_row, binding.layoutTeamRowsContainer, false)

                rowView.findViewById<android.widget.TextView>(R.id.tvRowRank).text = row.rank.toString()

                val ivFlag = rowView.findViewById<android.widget.ImageView>(R.id.ivRowFlag)
                Glide.with(rowView.context)
                    .load(row.team.logo)
                    .placeholder(R.drawable.ic_favorite_border)
                    .into(ivFlag)

                rowView.findViewById<android.widget.TextView>(R.id.tvRowName).text = row.team.name

                // Stats columns
                rowView.findViewById<android.widget.TextView>(R.id.tvRowPlayed).text = (row.all?.played ?: 0).toString()
                rowView.findViewById<android.widget.TextView>(R.id.tvRowWon).text = (row.all?.win ?: 0).toString()
                rowView.findViewById<android.widget.TextView>(R.id.tvRowDrawn).text = (row.all?.draw ?: 0).toString()
                rowView.findViewById<android.widget.TextView>(R.id.tvRowLost).text = (row.all?.lose ?: 0).toString()

                // GD
                val gd = row.goalsDiff ?: 0
                val gdText = if (gd > 0) "+$gd" else gd.toString()
                rowView.findViewById<android.widget.TextView>(R.id.tvRowGD).text = gdText

                // PTS
                rowView.findViewById<android.widget.TextView>(R.id.tvRowPTS).text = row.points.toString()

                binding.layoutTeamRowsContainer.addView(rowView)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<WcGroupItem>() {
            override fun areItemsTheSame(oldItem: WcGroupItem, newItem: WcGroupItem): Boolean {
                return oldItem.groupName == newItem.groupName
            }

            override fun areContentsTheSame(oldItem: WcGroupItem, newItem: WcGroupItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
