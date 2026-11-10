package com.football.livescores.matchlive.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.local.RequestLimitManager
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EmptyStateViewEntryPoint {
        fun requestLimitManager(): RequestLimitManager
    }

    private val limitManager: RequestLimitManager by lazy {
        EntryPoints.get(context.applicationContext, EmptyStateViewEntryPoint::class.java).requestLimitManager()
    }

    private val ivEmpty: ImageView
    private val tvTitle: TextView
    private val tvEmpty: TextView
    private var originalTitle: CharSequence? = null
    private var originalText: CharSequence? = null

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "request_count" || key == "is_premium_user") {
            post {
                updateEmptyStateText()
            }
        }
    }

    init {
        orientation = VERTICAL
        gravity = android.view.Gravity.CENTER
        LayoutInflater.from(context).inflate(R.layout.layout_empty_state_internal, this, true)
        ivEmpty = findViewById(R.id.ivEmptyStateInternal)
        tvTitle = findViewById(R.id.tvEmptyTitleInternal)
        tvEmpty = findViewById(R.id.tvEmptyStateInternal)
        
        val prefs = context.getSharedPreferences("livescore_request_limits_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        
        updateEmptyStateText()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        val prefs = context.getSharedPreferences("livescore_request_limits_prefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility == VISIBLE) {
            updateEmptyStateText()
        }
    }

    private fun isLimitExceeded(): Boolean {
        return try {
            limitManager.isLimitExceeded()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun updateEmptyStateText() {
        if (isLimitExceeded()) {
            tvTitle.visibility = View.VISIBLE
            tvTitle.setText(R.string.iap_out_of_quota_title)
            tvEmpty.setText(R.string.limit_exceeded_empty_state)
        } else {
            if (originalTitle != null) {
                tvTitle.text = originalTitle
                tvTitle.visibility = if (originalTitle.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            tvEmpty.text = originalText ?: context.getString(R.string.empty_general_desc)
        }
    }

    var text: CharSequence?
        get() = tvEmpty.text
        set(value) {
            originalText = value
            updateEmptyStateText()
        }

    fun setText(resId: Int) {
        originalText = context.getString(resId)
        updateEmptyStateText()
    }

    fun setTitleAndDesc(title: CharSequence, desc: CharSequence) {
        originalTitle = title
        originalText = desc
        tvTitle.text = title
        tvTitle.visibility = if (title.isEmpty()) View.GONE else View.VISIBLE
        tvEmpty.text = desc
    }

    fun setTitleAndDesc(titleResId: Int, descResId: Int) {
        setTitleAndDesc(context.getString(titleResId), context.getString(descResId))
    }
}
