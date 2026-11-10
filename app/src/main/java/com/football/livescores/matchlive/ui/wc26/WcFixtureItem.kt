package com.football.livescores.matchlive.ui.wc26

import com.football.livescores.matchlive.data.remote.model.MatchItemDto

sealed class WcFixtureItem {
    data class HeaderItem(val dateText: String) : WcFixtureItem()
    data class MatchItem(val match: MatchItemDto) : WcFixtureItem()
    data class AdItem(val adId: String) : WcFixtureItem()
    object EmptyItem : WcFixtureItem()
}
