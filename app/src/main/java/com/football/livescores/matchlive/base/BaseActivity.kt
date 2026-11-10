package com.football.livescores.matchlive.base

import android.app.Dialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.utils.SystemConfiguration
import com.football.livescores.matchlive.utils.SystemUtil


abstract class BaseActivity : AppCompatActivity() {

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var noInternetDialog: Dialog? = null

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
        SystemConfiguration.setStatusBarColor(
            this,
            R.color.transparent,
            SystemConfiguration.IconColor.ICON_LIGHT
        )
        super.onCreate(savedInstanceState)
        SystemUtil.setLocale(this)
        bind()
    }

    abstract fun bind()

    override fun onStart() {
        super.onStart()
        registerNetworkCallback()
        checkNetworkStatus()
    }

    override fun onStop() {
        super.onStop()
        unregisterNetworkCallback()
        dismissNoInternetDialog()
    }

    override fun onResume() {
        super.onResume()
        hideNavigationBar()
    }

    private fun checkNetworkStatus() {
        if (!SystemUtil.isNetworkConnected(this)) {
            showNoInternetDialog()
        } else {
            dismissNoInternetDialog()
        }
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread {
                        dismissNoInternetDialog()
                    }
                }

                override fun onLost(network: Network) {
                    runOnUiThread {
                        showNoInternetDialog()
                    }
                }
            }
            val builder = NetworkRequest.Builder()
            connectivityManager?.registerNetworkCallback(builder.build(), networkCallback!!)
        } catch (e: Exception) {
            android.util.Log.e("BaseActivity", "Failed to register network callback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            android.util.Log.e("BaseActivity", "Failed to unregister network callback: ${e.message}")
        } finally {
            networkCallback = null
            connectivityManager = null
        }
    }

    private fun showNoInternetDialog() {
        if (isFinishing || isDestroyed) return
        if (noInternetDialog?.isShowing == true) return

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_no_internet)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        noInternetDialog = dialog
        dialog.show()
    }

    private fun dismissNoInternetDialog() {
        noInternetDialog?.let {
            if (it.isShowing) {
                it.dismiss()
            }
        }
        noInternetDialog = null
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
