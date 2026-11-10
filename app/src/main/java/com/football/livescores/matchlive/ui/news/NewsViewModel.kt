package com.football.livescores.matchlive.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.football.livescores.matchlive.data.remote.model.NewsCategoryDto
import com.football.livescores.matchlive.data.remote.model.NewsItemDto
import com.football.livescores.matchlive.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Context
import com.football.livescores.matchlive.utils.SystemUtil
import dagger.hilt.android.qualifiers.ApplicationContext

sealed class NewsUiState {
    object Loading : NewsUiState()
    data class Success(
        val categories: List<NewsCategoryDto>,
        val allNews: List<NewsItemDto>,
        val filteredNews: List<NewsItemDto>,
        val selectedCategoryId: String
    ) : NewsUiState()
    data class Error(val message: String) : NewsUiState()
}

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private var currentCategoryId = "all"
    private var loadedCategories: List<NewsCategoryDto> = listOf(NewsCategoryDto("all", "All"))

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = NewsUiState.Loading

            val userLang = SystemUtil.getPreLanguage(context).ifEmpty { "en" }
            val categoriesResult = newsRepository.getNewsCategories(lang = userLang)
            categoriesResult.onSuccess { apiCategories ->
                val fullList = mutableListOf(NewsCategoryDto("all", "All"))
                fullList.addAll(apiCategories)
                loadedCategories = fullList
            }

            val newsResult = newsRepository.getLatestNews(lang = userLang, page = 1, limit = 50)
            newsResult.onSuccess { newsList ->
                val filtered = applyCategoryFilter(newsList, currentCategoryId)
                _uiState.value = NewsUiState.Success(
                    categories = loadedCategories,
                    allNews = newsList,
                    filteredNews = filtered,
                    selectedCategoryId = currentCategoryId
                )
            }.onFailure { exception ->
                _uiState.value = NewsUiState.Error(
                    exception.message ?: "Failed to load news. Please try again."
                )
            }
        }
    }

    fun selectCategory(categoryId: String) {
        currentCategoryId = categoryId
        val currentState = _uiState.value
        if (currentState is NewsUiState.Success) {
            val filtered = applyCategoryFilter(currentState.allNews, categoryId)
            _uiState.value = currentState.copy(
                filteredNews = filtered,
                selectedCategoryId = categoryId
            )
        }
    }

    private fun applyCategoryFilter(list: List<NewsItemDto>, categoryId: String): List<NewsItemDto> {
        if (categoryId.equals("all", ignoreCase = true)) {
            return list
        }
        val rawKey = categoryId.lowercase().trim()
        val normalizedKey = rawKey.replace("-", " ")

        return list.filter { news ->
            val cat = news.category?.lowercase() ?: ""
            val cats = news.categories?.map { it.lowercase() } ?: emptyList()
            val tags = news.tags?.map { it.lowercase() } ?: emptyList()

            val catNorm = cat.replace("-", " ")
            val catsNorm = cats.map { it.replace("-", " ") }
            val tagsNorm = tags.map { it.replace("-", " ") }

            cat == rawKey ||
                    catNorm == normalizedKey ||
                    catNorm.contains(normalizedKey) ||
                    cats.contains(rawKey) ||
                    catsNorm.contains(normalizedKey) ||
                    catsNorm.any { it.contains(normalizedKey) } ||
                    tagsNorm.any { it.contains(normalizedKey) }
        }
    }
}
