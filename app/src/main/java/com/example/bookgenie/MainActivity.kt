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
import com.google.firebase.auth.FirebaseAuth // FirebaseAuth importu eklendi
import androidx.navigation.NavOptions

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var animatedBackground: AnimatedGradientDrawable? = null
    private var isAnimatedBgActiveForCurrentDest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Renkleri al (Onboarding'deki gibi)
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

        // --- OTURUM KONTROLÜ ---
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            // Kullanıcı zaten giriş yapmış.
            // Eğer şu anki ekran onboardingFragment ise, MainPageFragment'a yönlendir
            // ve onboardingFragment'ı yığından temizle.
            if (navController.currentDestination?.id == R.id.onboardingFragment) {
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.main_activity_nav, true) // Navigasyon grafiğinin başına kadar temizle
                    .build()
                navController.navigate(R.id.mainPageFragment, null, navOptions)
            }
        }
        // --- OTURUM KONTROLÜ BİTTİ ---


        val bottomAppBar = binding.bottomAppBar
        val fab = binding.fab

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val shouldShowAnimatedBackground = when (destination.id) {
                R.id.onboardingFragment,
                R.id.logInFragment,
                R.id.signUpFragment
                -> true
                else -> false
            }

            isAnimatedBgActiveForCurrentDest = shouldShowAnimatedBackground

            if (shouldShowAnimatedBackground) {
                binding.root.background = animatedBackground
                animatedBackground?.startAnimation()
            } else {
                animatedBackground?.stopAnimation()
                binding.root.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.Shadowed_Earth_1)
                )
            }

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