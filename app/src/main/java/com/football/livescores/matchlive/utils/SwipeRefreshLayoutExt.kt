package com.football.livescores.matchlive.utils

import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Binds pull-to-refresh to the actual scrollable view so SwipeRefreshLayout
 * does not steal vertical scroll gestures when the list is not at the top.
 */
fun SwipeRefreshLayout.bindScrollableChild(scrollableProvider: () -> View?) {
    setOnChildScrollUpCallback { _, _ ->
        scrollableProvider()?.canScrollVertically(-1) ?: false
    }
}
