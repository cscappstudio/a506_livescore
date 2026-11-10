package com.football.livescores.matchlive.data.remote.model

import com.google.gson.annotations.SerializedName

data class NewsCategoryDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String
)
