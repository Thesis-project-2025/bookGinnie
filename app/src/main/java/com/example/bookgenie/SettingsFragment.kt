package com.example.bookgenie

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.bookgenie.databinding.FragmentSettingsBinding
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var currentUser: FirebaseUser? = null

    // Action ID'leri
    private val actionToUserInfo by lazy { R.id.action_settingsFragment_to_userInfoFragment }
    private val actionSettingsToOnboarding by lazy { R.id.action_settingsFragment_to_onboardingFragment }

    companion object {
        const val PREFS_NAME = "SettingsPrefs"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val TAG = "SettingsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Servisleri ve SharedPreferences'ı initialize et
        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, 0)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        currentUser = auth.currentUser

        setupClickListeners()
        setupSwitches()
    }

    private fun setupClickListeners() {
        binding.buttonEditProfile.setOnClickListener {
            findNavController().navigate(actionToUserInfo)
        }

        binding.buttonDeleteAccount.setOnClickListener {
            showDeleteAccountConfirmationDialog()
        }
    }

    private fun setupSwitches() {
        val switchTheme: SwitchMaterial = binding.switchTheme
        val switchNotifications: SwitchMaterial = binding.switchNotifications

        // 1. Mevcut ayarları yükle ve switch'lerin durumunu ayarla
        loadAndApplySettings(switchTheme, switchNotifications)

        // 2. Tema switch'i için dinleyici (listener) ata
        switchTheme.setOnCheckedChangeListener { _, isChecked ->
            // Seçimi anında kaydet
            saveThemeSetting(isChecked)

            // Yeni tema modunu belirle
            val newNightMode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }

            // Değişikliği uygula
            AppCompatDelegate.setDefaultNightMode(newNightMode)

            // requireActivity().recreate() aktivitenin yeniden oluşturulmasını sağlayarak temanın
            // anında yansımasına neden olur. Daha pürüzsüz bir geçiş isteniyorsa bu satır
            // kaldırılabilir, tema bir sonraki ekran geçişinde güncellenir.
        }

        // 3. Bildirim switch'i için dinleyici ata
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSetting(isChecked)
        }
    }

    private fun loadAndApplySettings(switchTheme: SwitchMaterial, switchNotifications: SwitchMaterial) {
        // Switch'in durumunu belirlemek için en güvenilir yol, doğrudan AppCompatDelegate'in o anki modunu kontrol etmektir.
        when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> {
                // Eğer mod sistem ayarını takip ediyorsa, sistemin mevcut durumuna bak.
                switchTheme.isChecked = isSystemNightMode()
            }
            AppCompatDelegate.MODE_NIGHT_YES -> {
                switchTheme.isChecked = true
            }
            AppCompatDelegate.MODE_NIGHT_NO -> {
                switchTheme.isChecked = false
            }
        }

        // Bildirim ayarını yükle
        val areNotificationsEnabled = sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        switchNotifications.isChecked = areNotificationsEnabled
    }

    private fun saveThemeSetting(isDarkMode: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_DARK_MODE, isDarkMode)
        }
    }

    private fun saveNotificationSetting(isEnabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_NOTIFICATIONS_ENABLED, isEnabled)
        }
    }

    private fun isSystemNightMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun showDeleteAccountConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_account_dialog_title))
            .setMessage(getString(R.string.delete_account_dialog_message))
            .setPositiveButton(getString(R.string.delete_confirm)) { dialog, _ ->
                deleteUserAccount()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel_dialog)) { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(R.drawable.ic_warning)
            .show()
    }

    private fun deleteUserAccount() {
        currentUser?.let { user ->
            showLoading(true)
            val userId = user.uid

            db.collection("users_info").document(userId).delete()
                .addOnSuccessListener {
                    Log.d(TAG, "User data from Firestore deleted successfully.")
                    deleteProfileImageFromStorage(userId) {
                        deleteUserFromAuth(user)
                    }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Log.e(TAG, "Error deleting user data from Firestore", e)
                    Toast.makeText(context, getString(R.string.error_deleting_data, e.message), Toast.LENGTH_LONG).show()
                }
        } ?: run {
            Toast.makeText(context, getString(R.string.user_not_logged_in), Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteProfileImageFromStorage(userId: String, onComplete: () -> Unit) {
        val fileName = "${userId}_profile.jpg"
        val profileImageRef = storage.reference.child("profile_images/$fileName")

        profileImageRef.delete()
            .addOnSuccessListener {
                Log.d(TAG, "Profile image deleted from Storage successfully.")
                onComplete()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error deleting profile image from Storage (may not exist)", e)
                onComplete()
            }
    }

    private fun deleteUserFromAuth(user: FirebaseUser) {
        user.delete()
            .addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    Log.d(TAG, "User account deleted from Firebase Auth.")
                    Toast.makeText(context, getString(R.string.account_deleted_successfully), Toast.LENGTH_SHORT).show()
                    navigateToOnboarding()
                } else {
                    Log.e(TAG, "Error deleting user account from Firebase Auth", task.exception)
                    Toast.makeText(context, getString(R.string.error_deleting_account, task.exception?.message), Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun navigateToOnboarding() {
        // Navigasyon yığınını temizleyerek Onboarding ekranına git.
        val startDestinationId = findNavController().graph.startDestinationId
        val navOptions = NavOptions.Builder()
            .setPopUpTo(startDestinationId, true)
            .build()
        findNavController().navigate(actionSettingsToOnboarding, null, navOptions)
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBarSettings.visibility = if (isLoading) View.VISIBLE else View.GONE
        val enableViews = !isLoading
        binding.buttonEditProfile.isEnabled = enableViews
        binding.switchNotifications.isEnabled = enableViews
        binding.switchTheme.isEnabled = enableViews
        binding.buttonDeleteAccount.isEnabled = enableViews
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}