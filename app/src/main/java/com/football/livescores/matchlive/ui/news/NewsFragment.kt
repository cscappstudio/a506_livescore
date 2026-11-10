package com.football.livescores.matchlive.ui.news

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
import com.football.livescores.matchlive.databinding.FragmentNewsBinding
import com.football.livescores.matchlive.utils.AdsConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NewsFragment : Fragment() {

    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewsViewModel by viewModels()
    private lateinit var newsAdapter: NewsAdapter
    private lateinit var categoryAdapter: NewsCategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCategoriesRecyclerView()
        setupNewsRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupCategoriesRecyclerView() {
        categoryAdapter = NewsCategoryAdapter { category ->
            viewModel.selectCategory(category.id)
        }
        val safeCtx = context ?: return
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(safeCtx, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
    }

    private fun setupNewsRecyclerView() {
        newsAdapter = NewsAdapter { newsItem ->
            val ctx = context ?: return@NewsAdapter
            val act = activity as? androidx.appcompat.app.AppCompatActivity
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                AdsConfig.showInterClickAd(act) {
                    if (!act.isFinishing && !act.isDestroyed) {
                        NewsDetailActivity.startActivity(act, newsItem)
                    }
                }
            } else {
                val currentCtx = context ?: return@NewsAdapter
                NewsDetailActivity.startActivity(currentCtx, newsItem)
            }
        }
        val safeCtx = context ?: return
        binding.rvNews.apply {
            layoutManager = LinearLayoutManager(safeCtx)
            adapter = newsAdapter
        }
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadData()
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadData()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.swipeRefresh.isRefreshing = false
                    when (state) {
                        is NewsUiState.Loading -> {
                            binding.progressBar.isVisible = true
                            binding.rvNews.isVisible = false
                            binding.layoutError.isVisible = false
                        }
                        is NewsUiState.Success -> {
                            binding.progressBar.isVisible = false
                            binding.layoutError.isVisible = false
                            binding.rvNews.isVisible = true

                            categoryAdapter.selectedCategoryId = state.selectedCategoryId
                            categoryAdapter.submitList(state.categories)

                            newsAdapter.submitList(state.filteredNews) {
                                binding.rvNews.scrollToPosition(0)
                            }

                            if (state.filteredNews.isEmpty()) {
                                binding.layoutError.isVisible = true
                                binding.tvErrorMessage.text =
                                    getString(R.string.news_no_data_category)
                            }
                        }
                        is NewsUiState.Error -> {
                            binding.progressBar.isVisible = false
                            binding.rvNews.isVisible = false
                            binding.layoutError.isVisible = true
                            binding.tvErrorMessage.text = if (state.message.contains("HTTP", ignoreCase = true) || 
                                state.message.contains("429") || 
                                state.message.contains("Exception", ignoreCase = true)) {
                                getString(R.string.news_no_data_category)
                            } else {
                                state.message
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): NewsFragment {
            return NewsFragment()
        }
    }
}
