package com.football.livescores.matchlive.base

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.utils.SystemConfiguration
import com.football.livescores.matchlive.utils.SystemUtil


abstract class AbsBaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = SystemUtil.getPreLanguage(newBase)
        val context = SystemUtil.setLocale(newBase, lang) ?: newBase
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onCreate(savedInstanceState)
        SystemConfiguration.setStatusBarColor(
            this,
            R.color.transparent,
            SystemConfiguration.IconColor.ICON_LIGHT
        )
        bind()
    }

    abstract fun bind()

    override fun onResume() {
        super.onResume()
        hideNavigationBar()
    }
    private fun hideNavigationBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val window = getWindow()
            window.setDecorFitsSystemWindows(false)
            val insetsController = window.getInsetsController()
            if (insetsController != null) {
                insetsController.hide(WindowInsets.Type.navigationBars())
                insetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
            }
        } else {
            val decorView = getWindow().getDecorView()
            decorView.setSystemUiVisibility(
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            )
        }
    }
}
