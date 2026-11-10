package com.football.livescores.matchlive.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.football.livescores.matchlive.data.remote.model.LineupPlayerWrapperDto
import com.football.livescores.matchlive.databinding.ItemLineupRowBinding

class LineupAdapter : RecyclerView.Adapter<LineupAdapter.LineupViewHolder>() {

    private var homeXI = listOf<LineupPlayerWrapperDto>()
    private var awayXI = listOf<LineupPlayerWrapperDto>()

    fun submitLineups(homeList: List<LineupPlayerWrapperDto>, awayList: List<LineupPlayerWrapperDto>) {
        homeXI = homeList
        awayXI = awayList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineupViewHolder {
        val binding = ItemLineupRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LineupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LineupViewHolder, position: Int) {
        val homePlayer = homeXI.getOrNull(position)?.player
        val awayPlayer = awayXI.getOrNull(position)?.player
        holder.bind(homePlayer, awayPlayer)
    }

    override fun getItemCount() = Math.max(homeXI.size, awayXI.size)

    inner class LineupViewHolder(private val binding: ItemLineupRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(homePlayer: com.football.livescores.matchlive.data.remote.model.LineupPlayerDto?, awayPlayer: com.football.livescores.matchlive.data.remote.model.LineupPlayerDto?) {
            if (homePlayer != null) {
                binding.tvHomePlayerNumber.text = homePlayer.number.toString()
                binding.tvHomePlayerName.text = homePlayer.name
            } else {
                binding.tvHomePlayerNumber.text = ""
                binding.tvHomePlayerName.text = ""
            }

            if (awayPlayer != null) {
                binding.tvAwayPlayerNumber.text = awayPlayer.number.toString()
                binding.tvAwayPlayerName.text = awayPlayer.name
            } else {
                binding.tvAwayPlayerNumber.text = ""
                binding.tvAwayPlayerName.text = ""
            }
        }
    }
}
