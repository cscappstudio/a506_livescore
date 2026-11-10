package com.football.livescores.matchlive.data.repository

import com.football.livescores.matchlive.data.remote.ApiService
import com.football.livescores.matchlive.data.remote.model.NewsCategoryDto
import com.football.livescores.matchlive.data.remote.model.NewsItemDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getLatestNews(lang: String = "en", page: Int = 1, limit: Int = 50): Result<List<NewsItemDto>> {
        return try {
            val response = apiService.getLatestNews(lang = lang, page = page, limit = limit)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(if (!response.message.isNullOrEmpty()) response.message else "Failed to fetch news"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNewsCategories(lang: String = "en"): Result<List<NewsCategoryDto>> {
        return try {
            val response = apiService.getNewsCategories(lang = lang)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(if (!response.message.isNullOrEmpty()) response.message else "Failed to fetch categories"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNewsDetail(newsId: String, lang: String = "en"): Result<NewsItemDto> {
        return try {
            val response = apiService.getNewsDetail(newsId = newsId, lang = lang)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(if (!response.message.isNullOrEmpty()) response.message else "Failed to fetch news detail"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
