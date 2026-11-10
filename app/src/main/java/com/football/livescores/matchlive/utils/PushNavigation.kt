package com.football.livescores.matchlive.utils

sealed class PushNavigation {
    data object Default : PushNavigation()
    data class Match(val fixtureId: Int) : PushNavigation()
    data class League(val leagueId: Int) : PushNavigation()
    data class News(val newsId: String) : PushNavigation()
    data class Promo(val screenName: String) : PushNavigation()
}

data class PushPayload(
    val pushId: String?,
    val navigation: PushNavigation,
    val rawData: Map<String, String>
)
