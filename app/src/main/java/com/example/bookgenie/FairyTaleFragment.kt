package com.example.bookgenie

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator // EKLENDİ
import android.animation.PropertyValuesHolder // EKLENDİ
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.bookgenie.databinding.FragmentFairyTaleBinding
import kotlin.math.hypot

class FairyTaleFragment : Fragment() {

    private var _binding: FragmentFairyTaleBinding? = null
    private val binding get() = _binding!!

    private var selectedCard: Int = 0
    // private var clickSound: MediaPlayer? = null // ESKİ
    private var cardSelectSound: MediaPlayer? = null // YENİ: Kart seçimi için
    private var navigationSound: MediaPlayer? = null // YENİ: Navigasyon/Buton için

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFairyTaleBinding.inflate(inflater, container, false)
        binding.root.visibility = View.INVISIBLE
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- MediaPlayer Kurulumu ---
        try {
            //cardSelectSound = MediaPlayer.create(requireContext(), R.raw.sparkle_sound) // @raw/sparkle_sound dosyanız olmalı
            navigationSound = MediaPlayer.create(requireContext(), R.raw.click_sound) // @raw/magic_transition_sound dosyanız olmalı
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ses dosyaları yüklenirken hata oluştu.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
        // --- Bitti ---

        // --- Circular Reveal Animasyonu ---
        view.post {
            try {
                // Alternatif başlangıç noktası (imageView4'ün merkezi)
                // val genieRect = android.graphics.Rect()
                // binding.imageView4.getGlobalVisibleRect(genieRect)
                // val cx = genieRect.centerX()
                // val cy = genieRect.centerY()

                // Mevcut başlangıç noktası (alt-orta)
                val cx = view.width / 2
                val cy = view.height

                val finalRadius = hypot(cx.toDouble(), cy.toDouble()).toFloat()
                val anim = ViewAnimationUtils.createCircularReveal(view, cx, cy, 0f, finalRadius)
                anim.duration = 700 // Biraz daha yavaş ve büyülü
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        // Açılış animasyonu bittikten sonra diğer animasyonları başlat
                        startIdleAnimations()
                    }
                })
                view.visibility = View.VISIBLE
                anim.start()
            } catch (e: Exception) {
                view.visibility = View.VISIBLE
                e.printStackTrace()
                startIdleAnimations() // Hata durumunda da idle animasyonları başlat
            }
        }
        // --- Animasyon Bitti ---

        binding.card1.setOnClickListener { selectCard(1) }
        binding.card2.setOnClickListener { selectCard(2) }

        binding.goToPageButton.setOnClickListener {
            // Ses çalma (navigasyon sesi)
            playSound(navigationSound)

            /// Lottie Animasyonunu Oynat (Eğer bir animasyon yüklenmişse)
            if (binding.buttonSparkleLottie.composition != null) { // DÜZELTİLMİŞ KONTROL
                binding.buttonSparkleLottie.visibility = View.VISIBLE
                binding.buttonSparkleLottie.playAnimation()
                binding.buttonSparkleLottie.addAnimatorListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        binding.buttonSparkleLottie.visibility = View.GONE
                        proceedToNavigate(it)
                    }
                    override fun onAnimationCancel(animation: Animator) {
                        super.onAnimationCancel(animation)
                        binding.buttonSparkleLottie.visibility = View.GONE
                        proceedToNavigate(it)
                    }
                })
            } else { // Lottie animasyonu yüklenmemişse veya bulunmuyorsa, basit scale animasyonu
                it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(150).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(150).withEndAction {
                        proceedToNavigate(it)
                    }.start()
                }.start()
            }
        }
        updateCardAppearance()
    }

    private fun startIdleAnimations() {
        // ImageView4 için "Nefes Alma" Animasyonu
        try { // imageView4 görünür değilse veya başka bir sorun olursa diye try-catch
            val pulseAnim = ObjectAnimator.ofPropertyValuesHolder(
                binding.imageView4,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.03f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.03f, 1f)
            ).apply {
                duration = 2500
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            pulseAnim.start()

            // Lottie Yıldızları için de benzer bir "hafif parlama" animasyonu eklenebilir.
            // Örneğin alpha veya scale ile oynayarak.
            // ObjectAnimator.ofFloat(binding.lottieAnimationView, View.ALPHA, 0.7f, 1f, 0.7f).apply {
            //     duration = 3000
            //     repeatCount = ObjectAnimator.INFINITE
            //     repeatMode = ObjectAnimator.REVERSE
            // }.start()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun playSound(mediaPlayer: MediaPlayer?) {
        mediaPlayer?.let { sound ->
            if (sound.isPlaying) {
                sound.seekTo(0)
            } else { // Sadece çalmıyorsa baştan başlat, çalıyorsa zaten devam ediyor olabilir (seekTo(0) ile kesildi)
                try {
                    sound.start()
                } catch (e: IllegalStateException) {
                    e.printStackTrace() // MediaPlayer henüz hazır olmayabilir
                }
            }
        }
    }

    private fun selectCard(cardNumber: Int) {
        playSound(cardSelectSound) // Kart seçim sesi
        selectedCard = cardNumber
        updateCardAppearance()
    }

    private fun updateCardAppearance() {
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.selectedColor) // colors.xml'den güncellendi
        val deselectedColor = ContextCompat.getColor(requireContext(), R.color.deselectedColor) // colors.xml'den güncellendi

        val selectedElevationValue = 24f // Daha belirgin
        val deselectedElevationValue = 8f // Eski değer 4f idi, biraz artırdık
        val selectedScaleValue = 1.08f // Biraz daha büyük
        val deselectedScaleValue = 1f
        val selectedRotationValue = 3f // Hafif eğim
        val deselectedRotationValue = 0f
        val animDuration = 200L // Biraz daha yumuşak geçiş

        // Card 1
        val card1Selected = selectedCard == 1
        binding.card1.setCardBackgroundColor(if (card1Selected) selectedColor else deselectedColor)
        binding.card1.cardElevation = if (card1Selected) selectedElevationValue else deselectedElevationValue
        // MaterialCardView ise stroke ekleyebiliriz:
        // if (binding.card1 is com.google.android.material.card.MaterialCardView) {
        //     (binding.card1 as com.google.android.material.card.MaterialCardView).strokeWidth = if (card1Selected) 4 else 0
        //     (binding.card1 as com.google.android.material.card.MaterialCardView).strokeColor = ContextCompat.getColor(requireContext(), R.color.card_selected_stroke_magic)
        // }
        binding.card1.animate()
            .scaleX(if (card1Selected) selectedScaleValue else deselectedScaleValue)
            .scaleY(if (card1Selected) selectedScaleValue else deselectedScaleValue)
            .rotation(if (card1Selected) selectedRotationValue else deselectedRotationValue)
            .setDuration(animDuration).start()

        // Card 2
        val card2Selected = selectedCard == 2
        binding.card2.setCardBackgroundColor(if (card2Selected) selectedColor else deselectedColor)
        binding.card2.cardElevation = if (card2Selected) selectedElevationValue else deselectedElevationValue
        // if (binding.card2 is com.google.android.material.card.MaterialCardView) {
        //     (binding.card2 as com.google.android.material.card.MaterialCardView).strokeWidth = if (card2Selected) 4 else 0
        //     (binding.card2 as com.google.android.material.card.MaterialCardView).strokeColor = ContextCompat.getColor(requireContext(), R.color.card_selected_stroke_magic)
        // }
        binding.card2.animate()
            .scaleX(if (card2Selected) selectedScaleValue else deselectedScaleValue)
            .scaleY(if (card2Selected) selectedScaleValue else deselectedScaleValue)
            .rotation(if (card2Selected) -selectedRotationValue else deselectedRotationValue) // Diğer yöne eğim
            .setDuration(animDuration).start()
    }

    private fun proceedToNavigate(view: View) { // goToPageButton onClick içeriği buraya taşındı
        when (selectedCard) {
            1 -> navigateToPage1(view)
            2 -> navigateToPage2(view)
            else -> Toast.makeText(requireContext(), "Lütfen bir kart seçin!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToPage1(view: View) {
        try {
            // Navigasyon animasyonları için nav_graph.xml'i düzenlemeyi unutmayın!
            Navigation.findNavController(view).navigate(R.id.fairyToGeneration)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun navigateToPage2(view: View) {
        try {
            // Navigasyon animasyonları için nav_graph.xml'i düzenlemeyi unutmayın!
            Navigation.findNavController(view).navigate(R.id.fairyToSpeech)
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cardSelectSound?.release()
        cardSelectSound = null
        navigationSound?.release()
        navigationSound = null
        _binding = null
    }
}