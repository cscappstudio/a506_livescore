package com.football.livescores.matchlive.ui.custom

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.local.BillingManager
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.databinding.DialogPremiumPaywallBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PremiumPaywallDialog : DialogFragment() {

    @Inject
    lateinit var limitManager: RequestLimitManager

    @Inject
    lateinit var billingManager: BillingManager

    private var _binding: DialogPremiumPaywallBinding? = null
    private val binding get() = _binding!!

    // Default: Weekly Premium selected (matches IAPActivity)
    private var isYearlySelected = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPremiumPaywallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        isCancelable = false
        
        val isOutOfQuota = arguments?.getBoolean(ARG_OUT_OF_QUOTA, false) ?: false
        if (isOutOfQuota) {
            binding.tvTitle.text = getString(R.string.iap_out_of_quota_title)
            val rawSubtitle = getString(R.string.iap_out_of_quota_subtitle)
            val limit = limitManager.getDailyLimit().toString()
            binding.tvSubtitle.text = rawSubtitle.replace("20", limit)
            binding.tvSubtitle.visibility = View.VISIBLE
        } else {
            setupTitleHighlight()
            binding.tvSubtitle.visibility = View.GONE
        }
        
        setupPlanSelection()
        setupListeners()
        observeProductDetails()
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

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
    }

    private fun setupPlanSelection() {
        binding.cardPlanYearly.setOnClickListener {
            isYearlySelected = true
            updatePlanUI()
        }

        binding.cardPlanMonthly.setOnClickListener {
            isYearlySelected = false
            updatePlanUI()
        }

        updatePlanUI()
    }

    private fun updatePlanUI() {
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.white)
        val selectedPeriodColor = Color.parseColor("#E6FFFFFF")
        val unselectedPriceColor = Color.parseColor("#259CF4")
        val unselectedPeriodColor = ContextCompat.getColor(requireContext(), R.color.text_muted)

        if (isYearlySelected) {
            // Weekly Premium selected
            binding.layoutWeeklyContent.setBackgroundResource(R.drawable.bg_iap_plan_selected)
            binding.tvYearlyPrice.setTextColor(whiteColor)
            binding.tvWeeklyPeriod.setTextColor(selectedPeriodColor)
            binding.tvWeeklyPeriod.setTypeface(binding.tvWeeklyPeriod.typeface, Typeface.BOLD)

            binding.layoutMonthlyContent.setBackgroundResource(R.drawable.bg_iap_plan_unselected)
            binding.tvMonthlyPrice.setTextColor(unselectedPriceColor)
            binding.tvMonthlyPeriod.setTextColor(unselectedPeriodColor)
            binding.tvMonthlyPeriod.setTypeface(binding.tvMonthlyPeriod.typeface, Typeface.NORMAL)

            binding.tvCancelAnytime.text = getString(R.string.iap_cancel_anytime)
        } else {
            // Month VIP selected
            binding.layoutMonthlyContent.setBackgroundResource(R.drawable.bg_iap_plan_selected)
            binding.tvMonthlyPrice.setTextColor(whiteColor)
            binding.tvMonthlyPeriod.setTextColor(selectedPeriodColor)
            binding.tvMonthlyPeriod.setTypeface(binding.tvMonthlyPeriod.typeface, Typeface.BOLD)

            binding.layoutWeeklyContent.setBackgroundResource(R.drawable.bg_iap_plan_unselected)
            binding.tvYearlyPrice.setTextColor(unselectedPriceColor)
            binding.tvWeeklyPeriod.setTextColor(unselectedPeriodColor)
            binding.tvWeeklyPeriod.setTypeface(binding.tvWeeklyPeriod.typeface, Typeface.NORMAL)

            binding.tvCancelAnytime.text = getString(R.string.iap_cancel_anytime_monthly)
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
        val weeklyProduct = products.find { it.productId == BillingManager.PRODUCT_WEEKLY }
        val monthlyProduct = products.find { it.productId == BillingManager.PRODUCT_MONTHLY }

        weeklyProduct?.let { product ->
            val pricingPhase = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
            pricingPhase?.formattedPrice?.let { price ->
                binding.tvYearlyPrice.text = price
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
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnSubscribe.setOnClickListener {
            val products = billingManager.productDetailsList.value
            val targetProductId = if (isYearlySelected) BillingManager.PRODUCT_WEEKLY else BillingManager.PRODUCT_MONTHLY
            val targetProduct = products.find { it.productId == targetProductId }

            if (targetProduct != null) {
                // Real Google Play Purchase Flow
                try {
                    billingManager.launchBillingFlow(requireActivity(), targetProduct)
                    dismiss()
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.iap_toast_google_play_error, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // High-Fidelity Mock Billing Fallback
                binding.progressLoading.visibility = View.VISIBLE
                binding.btnSubscribe.isEnabled = false
                binding.btnSubscribe.text = getString(R.string.iap_btn_connecting_mock)

                Handler(Looper.getMainLooper()).postDelayed({
                    if (isAdded) {
                        limitManager.setPremium(true)
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.iap_toast_mock_success),
                            Toast.LENGTH_LONG
                        ).show()
                        
                        binding.progressLoading.visibility = View.GONE
                        binding.btnSubscribe.isEnabled = true
                        
                        // Force activity reload to reflect updates instantly
                        activity?.recreate()
                        dismiss()
                    }
                }, 1500)
            }
        }

        binding.tvRestore.setOnClickListener {
            Toast.makeText(
                requireContext(),
                getString(R.string.iap_restore_purchase),
                Toast.LENGTH_SHORT
            ).show()
            billingManager.queryPurchases()
        }

        binding.tvTerms.setOnClickListener {
            Toast.makeText(
                requireContext(),
                getString(R.string.iap_terms_of_use),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.tvPrivacy.setOnClickListener {
            Toast.makeText(
                requireContext(),
                getString(R.string.iap_privacy_policy_toast),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PremiumPaywallDialog"
        private const val ARG_OUT_OF_QUOTA = "arg_out_of_quota"
        
        fun newInstance(isOutOfQuota: Boolean = false): PremiumPaywallDialog {
            return PremiumPaywallDialog().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_OUT_OF_QUOTA, isOutOfQuota)
                }
            }
        }
    }
}
