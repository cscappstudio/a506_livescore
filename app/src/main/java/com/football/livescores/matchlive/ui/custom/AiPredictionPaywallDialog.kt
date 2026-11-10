package com.football.livescores.matchlive.ui.custom

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.local.BillingManager
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.databinding.DialogAiPredictionPaywallBinding
import com.football.livescores.matchlive.ui.iap.IAPActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AiPredictionPaywallDialog : DialogFragment() {

    @Inject
    lateinit var limitManager: RequestLimitManager

    @Inject
    lateinit var billingManager: BillingManager

    private var _binding: DialogAiPredictionPaywallBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAiPredictionPaywallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        isCancelable = true

        setupTitleHighlight()
        setupListeners()
    }

    private fun setupTitleHighlight() {
        val currentText = resources.getText(R.string.dialog_ai_title)
        if (currentText is Spanned && currentText.getSpans(0, currentText.length, ForegroundColorSpan::class.java).isNotEmpty()) {
            binding.tvTitle.text = currentText
            return
        }
        val raw = currentText.toString()
        val spannable = SpannableString(raw)
        val highlightColor = Color.parseColor("#259CF4")
        val aiIndex = raw.indexOf("AI", ignoreCase = true)
        if (aiIndex >= 0) {
            spannable.setSpan(
                ForegroundColorSpan(highlightColor),
                aiIndex,
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

    private fun setupListeners() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnLater.setOnClickListener {
            dismiss()
        }

        binding.btnGoPremium.setOnClickListener {
            val intent = Intent(requireContext(), IAPActivity::class.java)
            startActivity(intent)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AiPredictionPaywallDialog"
        
        fun newInstance(): AiPredictionPaywallDialog {
            return AiPredictionPaywallDialog()
        }
    }
}
