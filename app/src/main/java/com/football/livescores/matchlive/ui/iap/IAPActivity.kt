package com.football.livescores.matchlive.ui.iap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.base.BaseActivity
import com.football.livescores.matchlive.data.local.BillingManager
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.databinding.ActivityIapBinding
import com.football.livescores.matchlive.ui.main.MainActivity
import com.football.livescores.matchlive.utils.SystemConfiguration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IAPActivity : BaseActivity() {

    companion object {
        const val EXTRA_FROM_PUSH = "FROM_PUSH"
    }

    @Inject
    lateinit var limitManager: RequestLimitManager

    @Inject
    lateinit var billingManager: BillingManager

    private lateinit var binding: ActivityIapBinding
    private var isMonthlySelected = false // Default: Weekly VIP selected

    override fun bind() {
        // Fast-path bypass for premium users: avoid displaying the UI entirely
        if (limitManager.isPremium()) {
            navigateToHome()
            return
        }

        SystemConfiguration.setTransparentStatusBar(this, SystemConfiguration.IconColor.ICON_LIGHT)

        binding = ActivityIapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTitleHighlight()
        setupPlanSelection()
        setupListeners()
        observeProductDetails()
        observePremiumStatus()
    }

    private fun setupTitleHighlight() {
        val currentText = resources.getText(R.string.iap_upgrade_title)
        if (currentText is Spanned && currentText.getSpans(0, currentText.length, ForegroundColorSpan::class.java).isNotEmpty()) {
            binding.tvTitle.text = currentText
            return
        }
        val raw = currentText.toString()
        val spannable = SpannableString(raw)
        val highlightColor = Color.parseColor("#259CF4")
        val vipIndex = raw.indexOf("VIP", ignoreCase = true)
        if (vipIndex >= 0) {
            spannable.setSpan(
                ForegroundColorSpan(highlightColor),
                vipIndex,
                raw.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            binding.tvTitle.text = spannable
        }
    }

    private fun observePremiumStatus() {
        lifecycleScope.launch {
            limitManager.isPremiumFlow.collectLatest { isPremium ->
                if (isPremium) {
                    navigateToHome()
                }
            }
        }
    }

    private fun setupPlanSelection() {
        binding.cardPlanWeekly.setOnClickListener {
            isMonthlySelected = false
            updatePlanUI()
        }

        binding.cardPlanMonthly.setOnClickListener {
            isMonthlySelected = true
            updatePlanUI()
        }

        // Initialize UI states
        updatePlanUI()
    }

    private fun updatePlanUI() {
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        val selectedPeriodColor = Color.parseColor("#E6FFFFFF")
        val unselectedPriceColor = Color.parseColor("#259CF4")
        val unselectedPeriodColor = ContextCompat.getColor(this, R.color.text_muted)

        if (isMonthlySelected) {
            binding.layoutMonthlyContent.setBackgroundResource(R.drawable.bg_iap_plan_selected)
            binding.tvMonthlyPrice.setTextColor(whiteColor)
            binding.tvMonthlyPeriod.setTextColor(selectedPeriodColor)
            binding.tvMonthlyPeriod.setTypeface(binding.tvMonthlyPeriod.typeface, Typeface.BOLD)

            binding.layoutWeeklyContent.setBackgroundResource(R.drawable.bg_iap_plan_unselected)
            binding.tvWeeklyPrice.setTextColor(unselectedPriceColor)
            binding.tvWeeklyPeriod.setTextColor(unselectedPeriodColor)
            binding.tvWeeklyPeriod.setTypeface(binding.tvWeeklyPeriod.typeface, Typeface.NORMAL)

            binding.tvCancelAnytime.text = getString(R.string.iap_cancel_anytime_monthly)
        } else {
            binding.layoutWeeklyContent.setBackgroundResource(R.drawable.bg_iap_plan_selected)
            binding.tvWeeklyPrice.setTextColor(whiteColor)
            binding.tvWeeklyPeriod.setTextColor(selectedPeriodColor)
            binding.tvWeeklyPeriod.setTypeface(binding.tvWeeklyPeriod.typeface, Typeface.BOLD)

            binding.layoutMonthlyContent.setBackgroundResource(R.drawable.bg_iap_plan_unselected)
            binding.tvMonthlyPrice.setTextColor(unselectedPriceColor)
            binding.tvMonthlyPeriod.setTextColor(unselectedPeriodColor)
            binding.tvMonthlyPeriod.setTypeface(binding.tvMonthlyPeriod.typeface, Typeface.NORMAL)

            binding.tvCancelAnytime.text = getString(R.string.iap_cancel_anytime)
        }
    }

    private fun observeProductDetails() {
        lifecycleScope.launch {
            billingManager.productDetailsList.collectLatest { products ->
                if (products.isNotEmpty()) {
                    updateProductPricingUI(products)
                }
            }
        }
    }

    private fun updateProductPricingUI(products: List<ProductDetails>) {
        val weeklyProduct = products.find { it.productId == BillingManager.Companion.PRODUCT_WEEKLY }
        val monthlyProduct = products.find { it.productId == BillingManager.Companion.PRODUCT_MONTHLY }

        weeklyProduct?.let { product ->
            val pricingPhase = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
            pricingPhase?.formattedPrice?.let { price ->
                binding.tvWeeklyPrice.text = price
            }
        }

        monthlyProduct?.let { product ->
            val pricingPhase = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
            pricingPhase?.formattedPrice?.let { price ->
                binding.tvMonthlyPrice.text = price
            }
        }
    }

    private fun setupListeners() {
        binding.btnSkip.setOnClickListener {
            navigateToHome()
        }

        binding.btnSubscribe.setOnClickListener {
            val products = billingManager.productDetailsList.value
            val targetProductId = if (isMonthlySelected) BillingManager.Companion.PRODUCT_MONTHLY else BillingManager.Companion.PRODUCT_WEEKLY
            val targetProduct = products.find { it.productId == targetProductId }

            if (targetProduct != null) {
                // Real Google Play Purchase Flow
                try {
                    billingManager.launchBillingFlow(this, targetProduct)
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.iap_toast_google_play_error, e.message), Toast.LENGTH_SHORT).show()
                }
            } else {
                // High-Fidelity Mock Billing Fallback
                binding.progressLoading.visibility = View.VISIBLE
                binding.btnSubscribe.isEnabled = false
                binding.btnSubscribe.text = getString(R.string.iap_btn_connecting_mock)

                Handler(Looper.getMainLooper()).postDelayed({
                    limitManager.setPremium(true)
                    Toast.makeText(
                        this,
                        getString(R.string.iap_toast_mock_success),
                        Toast.LENGTH_LONG
                    ).show()

                    binding.progressLoading.visibility = View.GONE
                    binding.btnSubscribe.isEnabled = true

                    navigateToHome()
                }, 1500)
            }
        }

        binding.tvPrivacy.setOnClickListener {
            Toast.makeText(this, getString(R.string.iap_privacy_policy_toast), Toast.LENGTH_SHORT).show()
        }

        binding.tvRestore.setOnClickListener {
            Toast.makeText(this, getString(R.string.iap_restore_purchase), Toast.LENGTH_SHORT).show()
            billingManager.queryPurchases()
        }

        binding.tvTerms.setOnClickListener {
            Toast.makeText(this, getString(R.string.iap_terms_of_use), Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToHome() {
        val fromOnboarding = intent.getBooleanExtra("FROM_ONBOARDING", false)
        if (fromOnboarding) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        finish()
    }
}
