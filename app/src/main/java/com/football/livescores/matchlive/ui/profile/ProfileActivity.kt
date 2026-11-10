package com.football.livescores.matchlive.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.football.livescores.matchlive.BuildConfig
import com.football.livescores.matchlive.base.BaseActivity
import com.football.livescores.matchlive.databinding.ActivityProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun bind() {
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        displayAppVersion()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateLanguageDisplay()
    }

    private fun displayAppVersion() {
        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"
    }

    private fun updateLanguageDisplay() {
        val onboardingPrefs = getSharedPreferences("livescore_onboarding_prefs", Context.MODE_PRIVATE)
        val selectedLanguage = onboardingPrefs.getString("selected_language", "English") ?: "English"
        binding.tvLanguageValue.text = selectedLanguage
    }

    private fun setupListeners() {
        updateLanguageDisplay()

        binding.rowLanguage.setOnClickListener {
            val intent = Intent(this, com.cscmobi.libraryads.views.language.CSCLanguageActivity::class.java).apply {
                putExtra(com.cscmobi.libraryads.commons.utils.Constants.FROM_SETTING, true)
            }
            startActivity(intent)
        }

        binding.rowPrivacyPolicy.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://sites.google.com/view/game-empire-studio")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
