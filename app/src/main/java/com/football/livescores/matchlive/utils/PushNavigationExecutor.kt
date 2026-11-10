package com.football.livescores.matchlive.utils

import android.content.Context
import android.content.Intent
import com.football.livescores.matchlive.ui.detail.MatchDetailActivity
import com.football.livescores.matchlive.ui.iap.IAPActivity
import com.football.livescores.matchlive.ui.main.MainActivity
import com.football.livescores.matchlive.ui.news.NewsDetailActivity

object PushNavigationExecutor {

    const val PROMO_VIP_SUBSCRIPTION = "vip_subscription"

    fun createSplashIntent(context: Context, payload: PushPayload): Intent {
        return toDestinationIntent(context, payload.navigation).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            payload.rawData.forEach { (key, value) -> putExtra(key, value) }
        }
    }

    fun toDestinationIntent(context: Context, navigation: PushNavigation): Intent {
        android.util.Log.d("FCMService", "Building destination intent for navigation: $navigation")
        return when (navigation) {
            is PushNavigation.Match -> Intent(context, MatchDetailActivity::class.java).apply {
                putExtra("MATCH_ID", navigation.fixtureId)
            }
            is PushNavigation.League -> Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_LEAGUE_ID, navigation.leagueId)
            }
            is PushNavigation.News -> {
                if (navigation.newsId.isNotEmpty()) {
                    Intent(context, NewsDetailActivity::class.java).apply {
                        putExtra("extra_news_id", navigation.newsId)
                    }
                } else {
                    Intent(context, MainActivity::class.java)
                }
            }
            is PushNavigation.Promo -> {
                if (navigation.screenName == PROMO_VIP_SUBSCRIPTION) {
                    Intent(context, IAPActivity::class.java).apply {
                        putExtra(IAPActivity.EXTRA_FROM_PUSH, true)
                    }
                } else {
                    Intent(context, MainActivity::class.java)
                }
            }
            PushNavigation.Default -> Intent(context, MainActivity::class.java)
        }
    }

    fun notificationId(payload: PushPayload): Int {
        return payload.pushId?.hashCode()
            ?: payload.navigation.hashCode()
    }
}
