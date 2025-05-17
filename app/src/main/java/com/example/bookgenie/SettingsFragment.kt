package com.example.bookgenie

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
// AlertDialog kaldırıldı, çünkü çıkış yapma diyaloğu artık yok.
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.fragment.app.Fragment
// NavOptions kaldırıldı, çünkü çıkış yapma navigasyonu artık yok.
import androidx.navigation.fragment.findNavController
import com.example.bookgenie.databinding.FragmentSettingsBinding
import com.google.android.material.switchmaterial.SwitchMaterial
// FirebaseAuth kaldırıldı, çünkü çıkış yapma işlemi artık burada değil.

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPreferences: SharedPreferences
    // private lateinit var auth: FirebaseAuth // Kaldırıldı

    // Action ID'leri
    private val actionToUserInfo by lazy { R.id.action_settingsFragment_to_userInfoFragment }
    // private val actionSettingsToOnboarding by lazy { R.id.action_settingsFragment_to_onboardingFragment } // Kaldırıldı


    companion object {
        const val PREFS_NAME = "SettingsPrefs"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
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

        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, 0)
        // auth = FirebaseAuth.getInstance() // Kaldırıldı

        setupClickListeners()
        setupSwitches()
    }

    private fun setupClickListeners() {
        binding.buttonEditProfile.setOnClickListener {
            // UserInfoFragment'a geçiş için action ID'nizin nav grafiğinde doğru tanımlandığından emin olun
            findNavController().navigate(actionToUserInfo)
        }

        // binding.buttonLogOut.setOnClickListener { ... } // Kaldırıldı
    }

    // showLogOutConfirmationDialog() metodu kaldırıldı
    // logOutUser() metodu kaldırıldı

    private fun setupSwitches() {
        val switchTheme: SwitchMaterial = binding.switchTheme
        val switchNotifications: SwitchMaterial = binding.switchNotifications

        loadAndApplySettings(switchTheme, switchNotifications)

        switchTheme.setOnCheckedChangeListener { _, isChecked ->
            val newNightMode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }

            // Sadece mod gerçekten değişiyorsa veya SharedPreferences'taki değerden farklıysa işlem yap
            if (AppCompatDelegate.getDefaultNightMode() != newNightMode) {
                AppCompatDelegate.setDefaultNightMode(newNightMode)
                saveThemeSetting(isChecked)
                requireActivity().recreate() // Temanın anında uygulanması için
            } else {
                // Eğer tema zaten istenen moddaysa bile, switch kullanıcının isteğini yansıtmalı
                // ve bu durum kaydedilmeli.
                saveThemeSetting(isChecked)
            }
        }

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSetting(isChecked)
            // Gerçek bildirim ayarları burada yönetilebilir.
        }
    }

    private fun loadAndApplySettings(switchTheme: SwitchMaterial, switchNotifications: SwitchMaterial) {
        // Tema ayarını yükle ve switch'in durumunu ayarla
        // Varsayılan değer olarak sistem temasını kullan
        val isDarkModeSaved = sharedPreferences.getBoolean(KEY_DARK_MODE, isSystemNightMode())
        switchTheme.isChecked = isDarkModeSaved

        // Bildirim ayarını yükle
        val areNotificationsEnabled = sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, true) // Varsayılan: açık
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
        // Sistem temasını kontrol et
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}