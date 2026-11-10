package com.football.livescores.matchlive.utils

import android.content.Intent
import android.os.Bundle

object PushDataParser {

    const val KEY_PUSH_ID = "push_id"
    const val KEY_PUSH_TYPE = "push_type"
    const val KEY_ACTION_VALUE = "action_value"
    const val KEY_CLICK_ACTION = "click_action"

    private val SKIP_KEY_PREFIXES = listOf(
        "android.",
        "google.",
        "gcm.",
        "from",
        "push_destination",
        "push_match_id",
        "push_home_team",
        "push_away_team"
    )

    fun parse(data: Map<String, String>): PushPayload {
        val pushId = data[KEY_PUSH_ID]?.takeIf { it.isNotBlank() }
        val pushType = data[KEY_PUSH_TYPE]?.trim()?.lowercase()
        val actionValue = data[KEY_ACTION_VALUE]?.trim()

        val navigation = when (pushType) {
            "match" -> {
                val fixtureId = actionValue?.toIntOrNull()
                    ?: data["fixture_id"]?.toIntOrNull()
                    ?: data["fixtureId"]?.toIntOrNull()
                    ?: data["match_id"]?.toIntOrNull()
                    ?: data["MATCH_ID"]?.toIntOrNull()
                if (fixtureId != null) PushNavigation.Match(fixtureId) else PushNavigation.Default
            }
            "league" -> {
                val leagueId = actionValue?.toIntOrNull()
                    ?: data["league_id"]?.toIntOrNull()
                    ?: data["leagueId"]?.toIntOrNull()
                if (leagueId != null) PushNavigation.League(leagueId) else PushNavigation.Default
            }
            "news" -> {
                val newsId = actionValue?.ifEmpty { null }
                    ?: data["news_id"]
                    ?: data["newsId"]
                    ?: data["id"]
                    ?: ""
                if (newsId.isNotEmpty()) PushNavigation.News(newsId) else PushNavigation.Default
            }
            "promo" -> {
                if (!actionValue.isNullOrEmpty()) PushNavigation.Promo(actionValue) else PushNavigation.Default
            }
            "default" -> PushNavigation.Default
            null -> parseLegacyPayload(data)
            else -> PushNavigation.Default
        }

        return PushPayload(
            pushId = pushId,
            navigation = navigation,
            rawData = data
        )
    }

    fun parseFromIntent(intent: Intent?): PushPayload? {
        val data = intentToDataMap(intent) ?: return null
        if (data[KEY_PUSH_TYPE].isNullOrBlank() && !hasLegacyKeys(data)) return null
        return parse(data)
    }

    fun intentToDataMap(intent: Intent?): Map<String, String>? {
        val bundle = intent?.extras ?: return null
        return bundleToDataMap(bundle).takeIf { it.isNotEmpty() }
    }

    fun bundleToDataMap(bundle: Bundle): Map<String, String> {
        return bundle.keySet()
            .filterNot { key -> SKIP_KEY_PREFIXES.any { key.startsWith(it) } }
            .mapNotNull { key ->
                bundle.get(key)?.toString()?.takeIf { it.isNotBlank() }?.let { key to it }
            }
            .toMap()
    }

    private fun parseLegacyPayload(data: Map<String, String>): PushNavigation {
        val fixtureId = data["fixture_id"]?.toIntOrNull()
            ?: data["fixtureId"]?.toIntOrNull()
            ?: data["match_id"]?.toIntOrNull()
            ?: data["MATCH_ID"]?.toIntOrNull()
        return if (fixtureId != null) PushNavigation.Match(fixtureId) else PushNavigation.Default
    }

    private fun hasLegacyKeys(data: Map<String, String>): Boolean {
        return data.containsKey("fixture_id")
            || data.containsKey("fixtureId")
            || data.containsKey("match_id")
            || data.containsKey("MATCH_ID")
    }
}
