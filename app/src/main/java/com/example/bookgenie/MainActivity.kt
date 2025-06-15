package com.example.bookgenie

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.bookgenie.databinding.ActivityMainBinding
import com.example.bookgenie.drawable.AnimatedGradientDrawable
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var sharedPreferences: SharedPreferences
    private var animatedBackground: AnimatedGradientDrawable? = null
    private var isAnimatedBgActiveForCurrentDest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Tema, herhangi bir UI oluşturulmadan ÖNCE uygulanmalıdır.
        applyThemeOnAppStartup()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Renkleri al
        val colorDayStart = ContextCompat.getColor(this, R.color.Shadowed_Earth_1)
        val colorDayEnd = ContextCompat.getColor(this, R.color.Shadowed_Earth_3)
        val colorNightStart = ContextCompat.getColor(this, R.color.Shadowed_Earth_6)
        val colorNightEnd = ContextCompat.getColor(this, R.color.Shadowed_Earth_2)

        animatedBackground = AnimatedGradientDrawable(
            colorDayStart, colorDayEnd,
            colorNightStart, colorNightEnd,
            duration = 15000L
        )

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        navController = navHostFragment.navController

        // 2. Oturum Kontrolü: NavController başlatıldıktan sonra yapılmalı.
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            // Kullanıcı zaten giriş yapmış.
            // Navigasyon grafiğinin başlangıç noktası (genellikle Onboarding veya giriş ekranları)
            // yerine doğrudan ana sayfaya yönlendir.
            val navGraph = navController.navInflater.inflate(R.navigation.main_activity_nav)
            val startDestinationId = navGraph.startDestinationId

            if (navController.currentDestination?.id == startDestinationId) {
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(startDestinationId, true) // Başlangıç ekranını yığından temizle
                    .build()
                navController.navigate(R.id.mainPageFragment, null, navOptions)
            }
        }
        // --- Oturum Kontrolü Bitti ---

        val bottomAppBar = binding.bottomAppBar
        val fab = binding.fab

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val shouldShowAnimatedBackground = when (destination.id) {
                R.id.onboardingFragment,
                R.id.logInFragment,
                R.id.signUpFragment -> true
                else -> false
            }

            isAnimatedBgActiveForCurrentDest = shouldShowAnimatedBackground

            if (shouldShowAnimatedBackground) {
                binding.root.background = animatedBackground
                animatedBackground?.startAnimation()
            } else {
                animatedBackground?.stopAnimation()
                // Arka planı temanın rengiyle eşleştir.
                // Bu, temanın anında uygulanması için önemlidir.
                val typedArray = theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
                val backgroundColor = typedArray.getColor(0, 0)
                typedArray.recycle()
                binding.root.setBackgroundColor(backgroundColor)
            }

            val shouldHideBottomBar = when (destination.id) {
                R.id.onboardingFragment,
                R.id.logInFragment,
                R.id.signUpFragment,
                R.id.fairyTaleFragment,
                R.id.generationFragment,
                R.id.speechFragment,
                R.id.bookDetailsFragment -> true
                else -> false
            }

            if (shouldHideBottomBar) {
                if (bottomAppBar.visibility == View.VISIBLE) {
                    val barHeight = bottomAppBar.height.toFloat().takeIf { it > 0 } ?: 150f
                    bottomAppBar.animate().translationY(barHeight).setDuration(200).withEndAction {
                        bottomAppBar.visibility = View.GONE
                    }.start()
                    fab.hide()
                }
            } else {
                if (bottomAppBar.visibility == View.GONE) {
                    bottomAppBar.visibility = View.VISIBLE
                    bottomAppBar.translationY = bottomAppBar.height.toFloat().takeIf { it > 0 } ?: 150f
                    bottomAppBar.animate().translationY(0f).setDuration(200).start()
                    fab.show()
                }
            }
        }

        binding.bottomNavigationView.setupWithNavController(navController)
        binding.bottomNavigationView.background = null

        binding.fab.setOnClickListener {
            navController.navigate(R.id.action_global_fairyFragment)
        }

        try {
            val bounceAnimation = AnimationUtils.loadAnimation(this, R.anim.bounce)
            binding.fab.startAnimation(bounceAnimation)
            val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse)
            binding.fab.startAnimation(pulseAnimation)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyThemeOnAppStartup() {
        sharedPreferences = getSharedPreferences(SettingsFragment.PREFS_NAME, 0)

        // Kullanıcının daha önce bir seçim yapıp yapmadığını kontrol et.
        // Eğer bir kayıt yoksa, sistem ayarını takip et.
        if (!sharedPreferences.contains(SettingsFragment.KEY_DARK_MODE)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            return
        }

        // Eğer kayıt varsa, kullanıcının seçimini uygula.
        val isDarkMode = sharedPreferences.getBoolean(SettingsFragment.KEY_DARK_MODE, false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAnimatedBgActiveForCurrentDest) {
            animatedBackground?.startAnimation()
        }
    }

    override fun onPause() {
        super.onPause()
        animatedBackground?.stopAnimation()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}