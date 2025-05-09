package com.example.bookgenie

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.bookgenie.databinding.FragmentSettingsBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!! // Null değilse binding'e erişim sağlar

    private lateinit var sharedPreferences: SharedPreferences

    // Navigasyon ve Menü ID'leri
    // Bu ID'lerin nav_graph.xml ve menu XML dosyanızdaki tanımlarla eşleştiğinden emin olun.
    // Fragment ID'leri (nav_graph.xml'deki <fragment android:id="@+id/...">)
    private val mainPageFragmentId by lazy { R.id.mainPageFragment }
    private val userInfoFragmentId by lazy { R.id.userInfoFragment }
    private val searchFragmentId by lazy { R.id.searchFragment }
    private val settingsFragmentId by lazy { R.id.settingsFragment } // Bu fragment'ın kendi ID'si

    // Action ID'leri (nav_graph.xml'deki <action android:id="@+id/...">)
    // SettingsFragment'tan diğerlerine geçiş için
    private val actionToUserInfo by lazy { R.id.action_settingsFragment_to_userInfoFragment }
    private val actionToMainPage by lazy { R.id.action_settingsFragment_to_mainPageFragment }
    private val actionToSearch by lazy { R.id.action_settingsFragment_to_searchFragment }
    // Gerekirse diğer action'lar için de benzer tanımlamalar yapabilirsiniz.

    // Menu Item ID'leri (bottom_nav_menu.xml'deki <item android:id="@+id/...">)
    // Bunlar genellikle fragment ID'leri ile aynı olur (mevcut yapınızda olduğu gibi)
    private val menuMainPageId by lazy { R.id.mainPageFragment }
    private val menuUserInfoId by lazy { R.id.userInfoFragment }
    private val menuSearchId by lazy { R.id.searchFragment }
    private val menuSettingsId by lazy { R.id.settingsFragment }
    private val menuFabId by lazy { R.id.idFab } // FAB için placeholder ID'niz

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

        setupClickListeners()
        setupSwitches()
    }

    private fun setupClickListeners() {
        binding.buttonEditProfile.setOnClickListener {
            // UserInfoFragment'a geçiş
            // 'actionToUserInfo' ID'sinin nav_graph'ta SettingsFragment'tan UserInfoFragment'a
            // doğru bir şekilde tanımlandığından emin olun.
            findNavController().navigate(actionToUserInfo)
        }
    }

    private fun setupSwitches() {
        // Switch'lerin referanslarını al
        val switchTheme: SwitchMaterial = binding.switchTheme
        val switchNotifications: SwitchMaterial = binding.switchNotifications

        // Kayıtlı ayarları yükle ve switch'lerin durumunu ayarla
        loadAndApplySettings(switchTheme, switchNotifications)

        // Tema switch'i için listener
        switchTheme.setOnCheckedChangeListener { _, isChecked ->
            val currentNightMode = AppCompatDelegate.getDefaultNightMode()
            val newNightMode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }

            // Sadece mod gerçekten değişiyorsa işlem yap
            if (currentNightMode != newNightMode) {
                AppCompatDelegate.setDefaultNightMode(newNightMode)
                saveThemeSetting(isChecked) // Sadece tema ayarını kaydet
                // Değişikliğin anında yansıması için Activity'yi yeniden oluştur.
                // Bu, mevcut Activity'yi ve içindeki Fragment'ları yeniden yükler.
                // Kullanıcı deneyimi için bu bir kesinti olabilir, alternatifler düşünülebilir.
                requireActivity().recreate()
            }
        }

        // Bildirim switch'i için listener
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSetting(isChecked) // Sadece bildirim ayarını kaydet
            // Burada bildirimleri etkinleştirme/devre dışı bırakma mantığı eklenebilir.
            // Örneğin: if (isChecked) NotificationManagerCompat.from(requireContext()).areNotificationsEnabled() ...
            // Toast.makeText(requireContext(), "Bildirimler ${if (isChecked) "açıldı" else "kapatıldı"}", Toast.LENGTH_SHORT).show()
        }
    }



    private fun loadAndApplySettings(switchTheme: SwitchMaterial, switchNotifications: SwitchMaterial) {
        // Tema ayarını yükle
        val isDarkModeSaved = sharedPreferences.getBoolean(KEY_DARK_MODE, isSystemNightMode())
        switchTheme.isChecked = isDarkModeSaved
        // AppCompatDelegate.setDefaultNightMode(if (isDarkModeSaved) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        // Not: Temanın Application sınıfında veya ana Activity'de zaten uygulanmış olması beklenir.
        // Burada sadece switch'in durumunu senkronize ediyoruz. Eğer recreate() kullanılıyorsa,
        // setDefaultNightMode'un tekrar çağrılmasına gerek yok.

        // Bildirim ayarını yükle
        val areNotificationsEnabled = sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, true) // Varsayılan: açık
        switchNotifications.isChecked = areNotificationsEnabled
    }

    private fun saveThemeSetting(isDarkMode: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_DARK_MODE, isDarkMode)
            // apply() otomatik olarak çağrılır
        }
    }

    private fun saveNotificationSetting(isEnabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_NOTIFICATIONS_ENABLED, isEnabled)
            // apply() otomatik olarak çağrılır
        }
    }

    private fun isSystemNightMode(): Boolean {
        // Sistem temasını kontrol et
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // View binding referansını temizle
    }
}