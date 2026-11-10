package com.football.livescores.matchlive.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import com.football.livescores.matchlive.base.BaseActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.football.livescores.matchlive.ui.main.MainActivity
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.databinding.ActivityIntroBinding
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.data.remote.RemoteConfigManager
import com.football.livescores.matchlive.ui.iap.IAPActivity
import com.football.livescores.matchlive.ui.permission.PermissionActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IntroActivity : BaseActivity() {

    @Inject
    lateinit var limitManager: RequestLimitManager

    private lateinit var binding: ActivityIntroBinding
    private val viewModel: OnboardingViewModel by viewModels()
    private lateinit var selectionAdapter: IntroSelectionAdapter

    override fun bind() {
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.progressBarSteps.max = 2

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        selectionAdapter = IntroSelectionAdapter(
            onItemClick = { item ->
                viewModel.toggleSelection(item.id)
            },
            isSelectedPredicate = { item ->
                viewModel.isSelected(item.id, item.name)
            }
        )

        binding.rvIntroList.layoutManager = LinearLayoutManager(this)
        binding.rvIntroList.adapter = selectionAdapter
    }

    private fun setupListeners() {
        // Back Button
        binding.btnBack.setOnClickListener {
            viewModel.prevStep()
        }

        // Skip Button
        binding.btnSkip.setOnClickListener {
            viewModel.skipOnboarding()
        }

        // Next / Finish Button
        binding.btnNext.setOnClickListener {
            viewModel.nextStep()
        }

        // Search text watcher for real-time filtering
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                viewModel.setSearchQuery(query)
                binding.btnClear.isVisible = query.isNotEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Clear search text
        binding.btnClear.setOnClickListener {
            binding.etSearch.text.clear()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Observe Step Transitions
                launch {
                    viewModel.currentStep.collectLatest { step ->
                        updateStepUI(step)
                    }
                }

                // 2. Observe Items List (Real-time filtered & prioritized)
                launch {
                    viewModel.uiItems.collectLatest { items ->
                        selectionAdapter.submitList(items) {
                            val isEmpty = items.isEmpty()
                            binding.emptyStateLayout.isVisible = isEmpty
                            binding.rvIntroList.isVisible = !isEmpty
                        }
                    }
                }

                // 3. Observe Onboarding Completion Event
                launch {
                    viewModel.onboardingCompleted.collectLatest { completed ->
                        if (completed) {
                            val showIap = try {
                                RemoteConfigManager.getInstance().showOnboardingIap()
                            } catch (e: Exception) {
                                true
                            }

                            val intent = if (hasNotificationPermission()) {
                                if (limitManager.isPremium() || !showIap) {
                                    Intent(this@IntroActivity, MainActivity::class.java)
                                } else {
                                    Intent(this@IntroActivity, IAPActivity::class.java).apply {
                                        putExtra("FROM_ONBOARDING", true)
                                    }
                                }
                            } else {
                                Intent(this@IntroActivity, PermissionActivity::class.java)
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun updateStepUI(step: Int) {
        // Clear search input on screen transition
        binding.etSearch.text.clear()

        when (step) {
            1 -> {
                // Step 1: Select Leagues (Hot lists first)
                binding.btnBack.isVisible = false // Back button NOT shown on Step 1
                binding.tvStepProgress.text = getString(R.string.intro_step_1_progress).replace("/3", "/2")
                binding.progressBarSteps.progress = 1
                binding.tvTitle.text = getString(R.string.intro_step_1_title)
                binding.tvSubtitle.text = getString(R.string.intro_step_1_subtitle)
                binding.etSearch.hint = getString(R.string.intro_step_1_hint)
                binding.btnNext.text = getString(R.string.intro_next)
            }
            2 -> {
                // Step 2: Select Teams (Priority to clubs inside selected leagues)
                binding.btnBack.isVisible = true // Back button shown on Step 2
                binding.tvStepProgress.text = getString(R.string.intro_step_2_progress).replace("/3", "/2")
                binding.progressBarSteps.progress = 2
                binding.tvTitle.text = getString(R.string.intro_step_2_title)
                binding.tvSubtitle.text = getString(R.string.intro_step_2_subtitle)
                binding.etSearch.hint = getString(R.string.intro_step_2_hint)
                binding.btnNext.text = getString(R.string.intro_finish)
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}

class IntroSelectionAdapter(
    private val onItemClick: (OnboardingItem) -> Unit,
    private val isSelectedPredicate: (OnboardingItem) -> Boolean
) : ListAdapter<OnboardingItem, IntroSelectionAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSelection)
        val cvLogo = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cvLogoContainer)
        val ivLogo = view.findViewById<ImageView>(R.id.ivLogo)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvSubtitle)
        val ivCheck = view.findViewById<ImageView>(R.id.ivCheckIndicator)

        fun bind(item: OnboardingItem) {
            tvTitle.text = item.name

            val isChecked = isSelectedPredicate(item)
            ivCheck.setImageResource(
                if (isChecked) R.drawable.ic_check_circle
                else R.drawable.ic_check_circle_outline
            )

            // Dynamic Styling for Card selected state
            if (isChecked) {
                card.strokeColor = ContextCompat.getColor(itemView.context, R.color.accent_green)
                card.strokeWidth = 2
            } else {
                card.strokeColor = ContextCompat.getColor(itemView.context, R.color.divider_dark)
                card.strokeWidth = 1
            }

            // Configure Logo and Subtitle based on item type
            when (item.type) {
                "league" -> {
                    cvLogo.isVisible = true
                    tvSubtitle.isVisible = true
                    val hotSuffix = if (item.isHot) itemView.context.getString(R.string.item_hot) else ""
                    tvSubtitle.text = "${item.subtitle}$hotSuffix"
                    Glide.with(itemView.context)
                        .load(item.logo)
                        .placeholder(R.mipmap.ic_launcher)
                        .into(ivLogo)
                }
                "team" -> {
                    cvLogo.isVisible = true
                    tvSubtitle.isVisible = true
                    tvSubtitle.text = itemView.context.getString(R.string.item_belongs, item.subtitle)
                    Glide.with(itemView.context)
                        .load(item.logo)
                        .placeholder(R.mipmap.ic_launcher)
                        .into(ivLogo)
                }
            }

            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<OnboardingItem>() {
        override fun areItemsTheSame(oldItem: OnboardingItem, newItem: OnboardingItem): Boolean {
            return oldItem.type == newItem.type && oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: OnboardingItem, newItem: OnboardingItem): Boolean {
            return oldItem == newItem
        }
    }
}
