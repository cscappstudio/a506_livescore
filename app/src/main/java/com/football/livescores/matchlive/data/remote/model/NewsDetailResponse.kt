package com.football.livescores.matchlive.data.remote.model

import com.google.gson.annotations.SerializedName

data class NewsDetailResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String?,
    @SerializedName("data")
    val data: NewsItemDto?
)
