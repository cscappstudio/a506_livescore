package com.football.livescores.matchlive.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.databinding.ItemDateBinding
import java.text.SimpleDateFormat
import java.util.*

data class DateItem(
    val date: Date,
    val dayName: String,
    val dayNumber: String,
    val monthName: String,
    var isSelected: Boolean = false
)

class DateAdapter(
    private val onDateClick: (DateItem, Int) -> Unit
) : RecyclerView.Adapter<DateAdapter.DateViewHolder>() {

    private val dates = ArrayList<DateItem>()
    private var selectedPosition = 2 // Center of 5 items (index 2)

    init {
        regenerateDates(Date())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
        val binding = ItemDateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
        holder.bind(dates[position], position)
    }

    override fun getItemCount() = dates.size

    fun getSelectedPosition(): Int = selectedPosition

    /**
     * Highlights the selected date. If the date is already in our 5-day window, 
     * we change the active index smoothly. If it's outside the window, we re-center the 5-day window.
     */
    fun setSelectedDate(selectedDate: Date) {
        var foundIndex = -1
        for (i in dates.indices) {
            if (isSameDay(dates[i].date, selectedDate)) {
                foundIndex = i
                break
            }
        }

        if (foundIndex != -1) {
            if (selectedPosition != foundIndex) {
                val prevSelected = selectedPosition
                selectedPosition = foundIndex

                if (prevSelected in dates.indices) {
                    dates[prevSelected].isSelected = false
                    notifyItemChanged(prevSelected)
                }
                dates[selectedPosition].isSelected = true
                notifyItemChanged(selectedPosition)
            }
        } else {
            regenerateDates(selectedDate)
        }
    }

    private fun regenerateDates(centerDate: Date) {
        dates.clear()
        val calendar = Calendar.getInstance().apply { time = centerDate }
        calendar.add(Calendar.DAY_OF_YEAR, -2) // 2 days before center

        val tz = TimeZone.getDefault()
        val sdfDay = SimpleDateFormat("EEE", Locale.US).apply { timeZone = tz }
        val sdfDate = SimpleDateFormat("dd", Locale.US).apply { timeZone = tz }
        val sdfMonth = SimpleDateFormat("MMM", Locale.US).apply { timeZone = tz }

        selectedPosition = 2

        for (i in 0..4) { // Exactly 5 days window
            val currentDate = calendar.time
            val isSelected = i == selectedPosition
            val isTodayVal = isSameDay(currentDate, Date())
            val dayName = if (isTodayVal) "Today" else sdfDay.format(currentDate)

            dates.add(
                DateItem(
                    date = currentDate,
                    dayName = dayName,
                    dayNumber = sdfDate.format(currentDate),
                    monthName = sdfMonth.format(currentDate),
                    isSelected = isSelected
                )
            )
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        notifyDataSetChanged()
    }

    private fun isSameDay(d1: Date, d2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = d1 }
        val cal2 = Calendar.getInstance().apply { time = d2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    inner class DateViewHolder(private val binding: ItemDateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DateItem, position: Int) {
            binding.tvDay.text = item.dayName
            binding.tvDate.text = item.dayNumber
            binding.tvMonth.text = item.monthName
            binding.indicator.visibility = View.INVISIBLE

            val context = binding.root.context
            if (item.isSelected) {
                binding.dateTabRoot.setBackgroundResource(R.drawable.bg_date_card_selected)
                binding.tvDay.setTextColor(ContextCompat.getColor(context, R.color.white))
                binding.tvDate.setTextColor(ContextCompat.getColor(context, R.color.white))
                binding.tvMonth.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                binding.dateTabRoot.setBackgroundResource(R.drawable.bg_date_card_unselected)
                binding.tvDay.setTextColor(ContextCompat.getColor(context, R.color.textSecondary))
                binding.tvDate.setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
                binding.tvMonth.setTextColor(ContextCompat.getColor(context, R.color.textSecondary))
            }

            binding.dateTabRoot.setOnClickListener {
                if (selectedPosition != position) {
                    val prevSelected = selectedPosition
                    selectedPosition = position

                    dates[prevSelected].isSelected = false
                    dates[selectedPosition].isSelected = true

                    notifyItemChanged(prevSelected)
                    notifyItemChanged(selectedPosition)

                    onDateClick(item, position)
                }
            }
        }
    }
}
