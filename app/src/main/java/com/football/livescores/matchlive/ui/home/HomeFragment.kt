package com.football.livescores.matchlive.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.local.FavoriteManager
import com.football.livescores.matchlive.data.local.MatchReminderManager
import com.football.livescores.matchlive.databinding.FragmentHomeBinding
import com.football.livescores.matchlive.ui.custom.PremiumPaywallDialog
import com.football.livescores.matchlive.ui.detail.MatchDetailActivity
import com.football.livescores.matchlive.utils.bindScrollableChild
import dagger.hilt.android.AndroidEntryPoint
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.data.remote.RemoteConfigManager
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.football.livescores.matchlive.utils.SharePreferenceUtils
import com.football.livescores.matchlive.utils.NativeCallback
import com.football.livescores.matchlive.utils.Admob
import kotlinx.coroutines.launch

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var matchAdapter: MatchAdapter
    private lateinit var dateAdapter: DateAdapter

    private var lastSelectedDateStr: String? = null
    private var lastFilter: MatchFilter? = null

    @Inject
    lateinit var reminderManager: MatchReminderManager

    @Inject
    lateinit var favoriteManager: FavoriteManager

    @Inject
    lateinit var limitManager: RequestLimitManager



    private var pendingReminderAction: (() -> Unit)? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingReminderAction?.invoke()
        }
        pendingReminderAction = null
    }

    private fun showSettingsDialog() {
        val dialog = com.football.livescores.matchlive.ui.custom.NotificationPermissionDialog.newInstance()
        dialog.show(childFragmentManager, com.football.livescores.matchlive.ui.custom.NotificationPermissionDialog.TAG)
    }


    private fun checkAndRequestNotificationPermission(onGranted: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    permission
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                onGranted()
            } else {
                val sharedPrefs = requireContext().getSharedPreferences("livescore_permissions_prefs", android.content.Context.MODE_PRIVATE)
                val hasRequestedBefore = sharedPrefs.getBoolean("has_requested_notification", false)
                val showRationale = shouldShowRequestPermissionRationale(permission)

                if (hasRequestedBefore && !showRationale) {
                    showSettingsDialog()
                } else {
                    pendingReminderAction = onGranted
                    sharedPrefs.edit().putBoolean("has_requested_notification", true).apply()
                    notificationPermissionLauncher.launch(permission)
                }
            }
        } else {
            onGranted()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isLiveOnly = arguments?.getBoolean(ARG_LIVE_ONLY, false) ?: false
        if (isLiveOnly) {
            binding.timeFilterLayout.visibility = View.GONE
            binding.filterLayout.visibility = View.GONE
            binding.emptyState.setTitleAndDesc(R.string.empty_live_title, R.string.empty_live_desc)
            viewModel.setFilter(MatchFilter.LIVE)
        }

        setupRecyclerViews()
        setupFilters()
        setupCalendarPicker()
        if(!SharePreferenceUtils.isOrganic(context)) {
            loadNativeAd()
        } else {
            binding.frAdsHome.removeAllViews()
            binding.frAdsHome.visibility = View.GONE
        }

        binding.swipeRefreshLayout.apply {
            bindScrollableChild { binding.rvMatches }
            setOnRefreshListener { viewModel.manualRefresh() }
        }
        
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Refresh reminder state when user navigates back to screen
        viewModel.refreshReminders()
        if (viewModel.currentFilter.value == MatchFilter.LIVE) {
            viewModel.startLivePolling()
        }
    }

    companion object {
        private const val ARG_LIVE_ONLY = "arg_live_only"

        fun newInstance(isLiveOnly: Boolean): HomeFragment {
            return HomeFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_LIVE_ONLY, isLiveOnly)
                }
            }
        }
    }

    private fun setupRecyclerViews() {
        // Match Adapter setup
        matchAdapter = MatchAdapter(
            onMatchClick = { match, selectPredictionTab ->
                val intent = Intent(requireContext(), MatchDetailActivity::class.java).apply {
                    putExtra("MATCH_ID", match.id)
                    putExtra("HOME_TEAM", match.homeTeamName)
                    putExtra("AWAY_TEAM", match.awayTeamName)
                    putExtra("SELECT_PREDICTION_TAB", selectPredictionTab)
                }
                startActivity(intent)
            },
            onFavoriteClick = { match ->
                if (favoriteManager.canAddFavoriteFixture(match.id)) {
                    viewModel.toggleFavorite(match)
                } else {
                    val paywall = PremiumPaywallDialog.newInstance()
                    paywall.show(parentFragmentManager, PremiumPaywallDialog.TAG)
                }
            },
            onReminderClick = { match ->
                checkAndRequestNotificationPermission {
                    val isSet = reminderManager.toggleReminder(match)
                    val alertMsg = if (isSet) {
                        getString(R.string.reminder_set_toast)
                    } else {
                        getString(R.string.reminder_cancelled_toast)
                    }
                    android.widget.Toast.makeText(requireContext(), alertMsg, android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.refreshReminders()
                }
            }
        )
        matchAdapter.showFavoriteInStandardLayout = true
        binding.rvMatches.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMatches.adapter = matchAdapter

        // Date Selector Adapter setup
        dateAdapter = DateAdapter(
            onDateClick = { item, position ->
                viewModel.setSelectedDate(item.date)
            }
        )
        binding.rvDateSelector.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvDateSelector.adapter = dateAdapter
    }

    private fun setupFilters() {
        binding.btnLive.setOnClickListener {
            viewModel.setFilter(MatchFilter.LIVE)
        }
        binding.btnScheduled.setOnClickListener {
            viewModel.setFilter(MatchFilter.UPCOMING)
        }
        binding.btnFinished.setOnClickListener {
            viewModel.setFilter(MatchFilter.FINISHED)
        }
    }

    private fun setupCalendarPicker() {
        binding.btnCalendar.setOnClickListener {
            showDatePickerDialog()
        }
    }

    private fun showDatePickerDialog() {
        val selectedDateMs = viewModel.selectedDate.value.time
        val datePicker = MaterialDatePicker.Builder.datePicker().apply {
            setTheme(R.style.CustomDatePickerTheme)
            setTitleText(getString(R.string.select_match_date))
            setSelection(selectedDateMs)
        }.build()

        datePicker.addOnPositiveButtonClickListener { selectionTime ->
            // MaterialDatePicker uses UTC milliseconds. Map safely to local timezone to prevent shift bugs.
            val selectedCalendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = selectionTime
            }
            val localCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, selectedCalendar.get(Calendar.YEAR))
                set(Calendar.MONTH, selectedCalendar.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, selectedCalendar.get(Calendar.DAY_OF_MONTH))
            }
            viewModel.setSelectedDate(localCalendar.time)
        }
        datePicker.show(childFragmentManager, "MATERIAL_DATE_PICKER")
    }

    private fun isToday(date: Date): Boolean {
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }
        return today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
               today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                 // Collect Matches
                 launch {
                     viewModel.matches.collect { items ->
                          val currentFilter = viewModel.currentFilter.value
                          when (currentFilter) {
                              MatchFilter.LIVE -> binding.emptyState.setTitleAndDesc(
                                  R.string.empty_live_title,
                                  R.string.empty_live_desc
                              )
                              MatchFilter.UPCOMING -> binding.emptyState.setTitleAndDesc(
                                  R.string.empty_upcoming_title,
                                  R.string.empty_upcoming_desc
                              )
                              MatchFilter.FINISHED -> binding.emptyState.setTitleAndDesc(
                                  R.string.empty_finished_title,
                                  R.string.empty_finished_desc
                              )
                          }
                         binding.emptyState.isVisible = items.isEmpty() && !viewModel.isLoading.value
                         
                         val currentDate = viewModel.selectedDate.value
                         
                         val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                             timeZone = java.util.TimeZone.getDefault()
                         }
                         val currentDateStr = sdf.format(currentDate)
                         
                         val hasChanged = currentDateStr != lastSelectedDateStr || currentFilter != lastFilter
                         
                         lastSelectedDateStr = currentDateStr
                         lastFilter = currentFilter
                         
                         matchAdapter.isUpcomingTab = (currentFilter == MatchFilter.UPCOMING)
                         matchAdapter.submitList(items) {
                             if (hasChanged) {
                                 binding.rvMatches.scrollToPosition(0)
                             }
                         }
                     }
                 }
 
                 // Collect Loading
                 launch {
                     viewModel.isLoading.collect { loading ->
                         if (loading) {
                             binding.emptyState.visibility = View.GONE
                         } else {
                             binding.emptyState.isVisible = viewModel.matches.value.isEmpty()
                         }

                         if (binding.swipeRefreshLayout.isRefreshing) {
                             if (!loading) {
                                 binding.swipeRefreshLayout.isRefreshing = false
                             }
                         } else {
                             binding.loadingSpinner.isVisible = loading
                             if (loading) {
                                 binding.rvMatches.visibility = View.INVISIBLE
                             } else {
                                 binding.rvMatches.visibility = View.VISIBLE
                             }
                         }
                     }
                 }

                // Collect Filter Tab Highlight updates
                launch {
                    viewModel.currentFilter.collect { filter ->
                        updateFilterButtonUI(filter)
                    }
                }

                // Collect Date selection and update Horizontal calendar strip
                launch {
                    viewModel.selectedDate.collect { date ->
                        dateAdapter.setSelectedDate(date)
                        binding.rvDateSelector.scrollToPosition(dateAdapter.getSelectedPosition())
                        
                        // Synchronize state: hide sub-filters layout when selected date is NOT today
                        val isLiveOnly = arguments?.getBoolean(ARG_LIVE_ONLY, false) ?: false
                        binding.filterLayout.isVisible = isToday(date) && !isLiveOnly
                    }
                }
            }
        }
    }



        private fun updateFilterButtonUI(selectedFilter: MatchFilter) {
        val ctx = requireContext()
        val buttons = listOf(
            Triple(binding.btnFinished, MatchFilter.FINISHED, R.drawable.ic_tab_finished),
            Triple(binding.btnLive, MatchFilter.LIVE, R.drawable.ic_tab_live),
            Triple(binding.btnScheduled, MatchFilter.UPCOMING, R.drawable.ic_tab_upcoming)
        )

        buttons.forEach { (btn, filter, _) ->
            val isSelected = (selectedFilter == filter)
            btn.isSelected = isSelected
            btn.strokeWidth = 0
            if (isSelected) {
                btn.setBackgroundResource(R.drawable.bg_tab_filter_selected)
                btn.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                btn.iconTint = ContextCompat.getColorStateList(ctx, R.color.white)
            } else {
                btn.setBackgroundResource(R.drawable.bg_tab_filter_unselected)
                btn.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                btn.iconTint = ContextCompat.getColorStateList(ctx, R.color.white)
            }
        }
    }

    private fun loadNativeAd() {
        val currentBinding = _binding ?: return
        if (limitManager.isPremium()) {
            currentBinding.frAdsHome.visibility = View.GONE
            return
        }

        val adId = try {
            RemoteConfigManager.getInstance().getAdId("native_all", getString(R.string.native_all))
        } catch (e: Exception) {
            getString(R.string.native_all)
        }

        if (adId.isNotEmpty()) {
            val safeContext = context ?: return
            currentBinding.frAdsHome.visibility = View.VISIBLE
            // Inflate and show shimmer layout while loading
            val shimmerView = LayoutInflater.from(safeContext).inflate(R.layout.layout_shimmer_league, currentBinding.frAdsHome, false)
            currentBinding.frAdsHome.removeAllViews()
            currentBinding.frAdsHome.addView(shimmerView)

            Admob.getInstance().loadNativeAds(
                safeContext,
                adId,
                1,
                object : NativeCallback() {
                    override fun onNativeAdLoaded(nativeAd: NativeAd?) {
                        super.onNativeAdLoaded(nativeAd)
                        val b = _binding ?: return
                        val ctx = context ?: return

                        val adView = LayoutInflater.from(ctx)
                            .inflate(R.layout.layout_native_league, null) as NativeAdView
                        
                        b.frAdsHome.removeAllViews()
                        b.frAdsHome.addView(adView)
                        
                        Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)
                    }

                    override fun onAdFailedToLoad() {
                        super.onAdFailedToLoad()
                        val b = _binding ?: return
                        val ctx = context ?: return
                        b.frAdsHome.visibility = View.GONE
                    }
                }
            )
        } else {
            currentBinding.frAdsHome.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopLivePolling()
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
