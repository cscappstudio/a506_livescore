package com.football.livescores.matchlive.ui.custom

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.databinding.DialogNotificationPermissionBinding

class NotificationPermissionDialog : DialogFragment() {

    private var _binding: DialogNotificationPermissionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogNotificationPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        isCancelable = true

        binding.btnSettings.setOnClickListener {
            openAppSettings()
            dismiss()
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }
        
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
    }

    private fun openAppSettings() {
        val context = requireContext()
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "NotificationPermissionDialog"
        
        fun newInstance(): NotificationPermissionDialog {
            return NotificationPermissionDialog()
        }
    }
}
