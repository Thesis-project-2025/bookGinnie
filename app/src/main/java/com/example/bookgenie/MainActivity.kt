package com.example.bookgenie

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat // ContextCompat importu eklendi
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.bookgenie.databinding.ActivityMainBinding
import com.example.bookgenie.drawable.AnimatedGradientDrawable // Drawable importu

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var animatedBackground: AnimatedGradientDrawable? = null // Drawable referansı
    private var isAnimatedBgActiveForCurrentDest = false // Mevcut hedef için animasyon aktif mi?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Animasyonlu Arkaplan Kurulumu ---
        // Renkleri al (Onboarding'deki gibi)
        val colorDayStart = ContextCompat.getColor(this, R.color.Shadowed_Earth_1) // Güncel renk ID'lerini kullan
        val colorDayEnd = ContextCompat.getColor(this, R.color.Shadowed_Earth_3)
        val colorNightStart = ContextCompat.getColor(this, R.color.Shadowed_Earth_6)
        val colorNightEnd = ContextCompat.getColor(this, R.color.Shadowed_Earth_2)

        animatedBackground = AnimatedGradientDrawable(
            colorDayStart, colorDayEnd,
            colorNightStart, colorNightEnd,
            duration = 15000L // Süreyi ayarla
        )
        // --- Bitti ---

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        navController = navHostFragment.navController

        val bottomAppBar = binding.bottomAppBar
        val fab = binding.fab

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Hangi fragmentların animasyonlu arkaplanı kullanacağını belirle
            val shouldShowAnimatedBackground = when (destination.id) {
                R.id.onboardingFragment,
                R.id.logInFragment,
                R.id.signUpFragment
                -> true // Bu ID'lerde göster
                else -> false // Diğerlerinde gösterme
            }

            isAnimatedBgActiveForCurrentDest = shouldShowAnimatedBackground // Durumu güncelle

            if (shouldShowAnimatedBackground) {
                binding.root.background = animatedBackground // Ana layout'a ata
                animatedBackground?.startAnimation() // Animasyonu başlat
            } else {
                // Animasyonu durdur ve varsayılan statik arkaplanı ata
                animatedBackground?.stopAnimation()
                binding.root.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.Shadowed_Earth_1) // XML'deki varsayılan renk
                )
            }

            // --- Bottom Bar ve FAB görünürlük kontrolü ---
            val shouldHideBottomBar = when (destination.id) {
                R.id.onboardingFragment,
                R.id.logInFragment,
                R.id.signUpFragment,
                R.id.fairyTaleFragment,
                R.id.generationFragment,
                R.id.speechFragment,
                R.id.bookDetailsFragment
                -> true
                else -> false
            }

            if (shouldHideBottomBar) {
                if (bottomAppBar.visibility == View.VISIBLE) { // Sadece görünürse animasyon yap
                    val barHeight = bottomAppBar.height.toFloat().takeIf { it > 0 } ?: 150f // Makul bir varsayılan
                    bottomAppBar.animate().translationY(barHeight).setDuration(200).withEndAction {
                        bottomAppBar.visibility = View.GONE
                    }.start()
                    fab.hide()
                }
            } else {
                if (bottomAppBar.visibility == View.GONE) { // Sadece gizliyse animasyon yap
                    bottomAppBar.visibility = View.VISIBLE
                    bottomAppBar.translationY = bottomAppBar.height.toFloat().takeIf { it > 0 } ?: 150f // Başlangıç pozisyonu
                    bottomAppBar.animate().translationY(0f).setDuration(200).start()
                    fab.show()
                }
            }
            // --- Bitti ---
        }

        binding.bottomNavigationView.setupWithNavController(navController)
        binding.bottomNavigationView.background = null

        binding.fab.setOnClickListener {
            navController.navigate(R.id.action_global_fairyFragment)
        }

        // FAB Animasyonları (İsteğe bağlı)
        try {
            val bounceAnimation = AnimationUtils.loadAnimation(this, R.anim.bounce)
            binding.fab.startAnimation(bounceAnimation)
            val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse)
            binding.fab.startAnimation(pulseAnimation)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Başlangıç seçimi
        // binding.bottomNavigationView.selectedItemId = R.id.idMainPage // NavController hallediyor olabilir
    }

    override fun onResume() {
        super.onResume()
        // Eğer mevcut fragment animasyonlu arkaplanı göstermeliyse animasyonu başlat/devam ettir
        if (isAnimatedBgActiveForCurrentDest) {
            animatedBackground?.startAnimation()
        }
    }

    override fun onPause() {
        super.onPause()
        // Activity durakladığında animasyonu her zaman durdur
        animatedBackground?.stopAnimation()
    }


    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}