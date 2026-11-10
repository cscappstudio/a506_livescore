package com.football.livescores.matchlive.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.football.livescores.matchlive.BuildConfig
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var isVietnamese = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        displayAppVersion()
    }

    override fun onResume() {
        super.onResume()
        val onboardingPrefs = requireContext().getSharedPreferences("livescore_onboarding_prefs", android.content.Context.MODE_PRIVATE)
        val selectedLanguage = onboardingPrefs.getString("selected_language", "English") ?: "English"
        binding.tvLanguageValue.text = selectedLanguage
    }

    private fun displayAppVersion() {
        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"
    }

    private fun setupListeners() {
        val onboardingPrefs = requireContext().getSharedPreferences("livescore_onboarding_prefs", android.content.Context.MODE_PRIVATE)
        val selectedLanguage = onboardingPrefs.getString("selected_language", "English") ?: "English"
        binding.tvLanguageValue.text = selectedLanguage

        // Language selection: Navigate to CSCLanguageActivity instead of showing dialog popup
        binding.rowLanguage.setOnClickListener {
            val intent = android.content.Intent(requireContext(), com.cscmobi.libraryads.views.language.CSCLanguageActivity::class.java).apply {
                putExtra(com.cscmobi.libraryads.commons.utils.Constants.FROM_SETTING, true)
            }
            startActivity(intent)
        }

        binding.rowPrivacyPolicy.setOnClickListener {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://sites.google.com/view/game-empire-studio")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
