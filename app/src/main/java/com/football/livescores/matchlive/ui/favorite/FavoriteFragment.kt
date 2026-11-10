package com.football.livescores.matchlive.ui.favorite

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.ui.main.MainActivity
import com.football.livescores.matchlive.databinding.FragmentFavoriteBinding
import com.football.livescores.matchlive.ui.detail.MatchDetailActivity
import com.football.livescores.matchlive.ui.home.MatchAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

import com.football.livescores.matchlive.data.local.MatchReminderManager
import javax.inject.Inject
import androidx.core.content.ContextCompat
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.ui.home.MatchListItem
import com.football.livescores.matchlive.utils.bindScrollableChild

@AndroidEntryPoint
class FavoriteFragment : Fragment() {

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FavoriteViewModel by viewModels()
    private lateinit var matchAdapter: MatchAdapter

    @Inject
    lateinit var reminderManager: MatchReminderManager

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
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        observeViewModel()

        binding.swipeRefreshLayout.apply {
            bindScrollableChild { binding.rvMatches }
            setOnRefreshListener { viewModel.refreshFavoriteMatchesFromServer() }
        }

        binding.btnDiscover.setOnClickListener {
            (activity as? MainActivity)?.switchToTab(R.id.nav_live)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh favorite matches when fragment is shown
        viewModel.loadFavoriteMatches()
    }

    private fun setupRecyclerViews() {
        // Matches Adapter
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
                viewModel.toggleFixtureFavorite(match)
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
                    viewModel.loadFavoriteMatches() // Refresh visual state
                }
            }
        )
        binding.rvMatches.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMatches.adapter = matchAdapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe favorite matches
                launch {
                    viewModel.favoriteMatches.collect { items ->
                        binding.emptyStateLayout.isVisible = items.isEmpty()
                        binding.tvEmptyMessage.text = getString(R.string.no_favorite_matches)
                        
                        matchAdapter.submitList(items)
                    }
                }

                // Observe refreshing state
                launch {
                    viewModel.isRefreshing.collect { refreshing ->
                        binding.swipeRefreshLayout.isRefreshing = refreshing
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
