package com.football.livescores.matchlive.data.remote.model

import com.google.gson.annotations.SerializedName

data class NewsItemDto(
    @SerializedName("id")
    val id: String?,
    @SerializedName("title")
    val title: String?,
    @SerializedName("summary")
    val summary: String?,
    @SerializedName("content")
    val content: String?,
    @SerializedName("image_url")
    val imageUrl: String?,
    @SerializedName("category")
    val category: String?,
    @SerializedName("categories")
    val categories: List<String>?,
    @SerializedName("tags")
    val tags: List<String>?,
    @SerializedName("published_at")
    val publishedAt: String?,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("updated_at")
    val updatedAt: String?,
    @SerializedName("status")
    val status: String?
)
