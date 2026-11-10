package com.football.livescores.matchlive.ui.home

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.databinding.DialogCalendarPickerBinding
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CustomCalendarDialog(
    private val initialDate: Date,
    private val onDateSelected: (Date) -> Unit
) : DialogFragment() {

    private var _binding: DialogCalendarPickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var displayedMonth: Calendar
    private lateinit var selectedDayCal: Calendar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Store selected date and browsing month separately
        selectedDayCal = Calendar.getInstance().apply { time = initialDate }
        displayedMonth = Calendar.getInstance().apply {
            time = initialDate
            set(Calendar.DAY_OF_MONTH, 1) // Start boundary at first of month
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCalendarPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            // Set dimensions to wrap content cleanly
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            // Transparent window to allow custom card border and rounded corners to render
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup 7-column grid layout for RecyclerView days
        binding.rvCalendarDays.layoutManager = GridLayoutManager(requireContext(), 7)

        // Month switching logic
        binding.btnPrevMonth.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, -1)
            renderCalendar()
        }

        binding.btnNextMonth.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, 1)
            renderCalendar()
        }

        renderCalendar()
    }

    private fun renderCalendar() {
        // 1. Update Month Year Title text
        val monthName = displayedMonth.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: ""
        val year = displayedMonth.get(Calendar.YEAR)
        binding.tvMonthYear.text = "${monthName.replaceFirstChar { it.uppercase() }} $year"

        // 2. Compute calendar grid structure
        val daysList = ArrayList<Int?>()

        // Get first day of week offset (1 = Sunday, 2 = Monday, etc.)
        displayedMonth.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeekOffset = displayedMonth.get(Calendar.DAY_OF_WEEK) - 1 // Sunday-first offset

        // Insert empty cells before 1st of month
        for (i in 0 until firstDayOfWeekOffset) {
            daysList.add(null)
        }

        // Add valid month days (1 to max)
        val maxDays = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (day in 1..maxDays) {
            daysList.add(day)
        }

        // 3. Bind days data to RecyclerView
        binding.rvCalendarDays.adapter = DaysAdapter(daysList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Highly performant internal adapter to bind individual days inside the calendar.
     */
    private inner class DaysAdapter(
        private val days: List<Int?>
    ) : RecyclerView.Adapter<DaysAdapter.DayViewHolder>() {

        inner class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDayText: TextView = view.findViewById(R.id.tvDayText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
            return DayViewHolder(view)
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
            val day = days[position]
            if (day == null) {
                holder.tvDayText.text = ""
                holder.tvDayText.background = null
                holder.tvDayText.isClickable = false
            } else {
                holder.tvDayText.text = day.toString()
                holder.tvDayText.isClickable = true

                // Check if this date represents the active selection
                val isSelected = selectedDayCal.get(Calendar.YEAR) == displayedMonth.get(Calendar.YEAR) &&
                                 selectedDayCal.get(Calendar.MONTH) == displayedMonth.get(Calendar.MONTH) &&
                                 selectedDayCal.get(Calendar.DAY_OF_MONTH) == day

                if (isSelected) {
                    holder.tvDayText.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    holder.tvDayText.setBackgroundResource(R.drawable.bg_calendar_selected_day)
                } else {
                    holder.tvDayText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_white))
                    // Use system selectable item background for modern click feedback
                    val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
                    val typedArray = requireContext().obtainStyledAttributes(attrs)
                    val backgroundResource = typedArray.getResourceId(0, 0)
                    holder.tvDayText.setBackgroundResource(backgroundResource)
                    typedArray.recycle()
                }

                // Click event: returns selected date and closes picker
                holder.tvDayText.setOnClickListener {
                    val clickedDate = Calendar.getInstance().apply {
                        time = displayedMonth.time
                        set(Calendar.DAY_OF_MONTH, day)
                    }.time
                    onDateSelected(clickedDate)
                    dismiss()
                }
            }
        }

        override fun getItemCount(): Int = days.size
    }
}
