package com.example.bookgenie

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewAnimationUtils // Circular Reveal için import
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.bookgenie.databinding.FragmentFairyTaleBinding
import kotlin.math.hypot

class FairyTaleFragment : Fragment() {

    // View Binding için güvenli yöntem
    private var _binding: FragmentFairyTaleBinding? = null
    private val binding get() = _binding!!

    private var selectedCard: Int = 0
    private var clickSound: MediaPlayer? = null // Nullable yapıp onViewCreated'da initialize et

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFairyTaleBinding.inflate(inflater, container, false)

        // --- Circular Reveal Başlangıç Görünürlüğü ---
        // Animasyon başlamadan önce view'ı görünmez yapalım ki açılış efekti görünsün
        binding.root.visibility = View.INVISIBLE
        // --- Bitti ---

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- MediaPlayer Kurulumu ---
        // MediaPlayer'ı burada initialize et, requireContext() artık güvenli
        clickSound = MediaPlayer.create(requireContext(), R.raw.click_sound)
        // --- Bitti ---


        // --- Circular Reveal Animasyonu ---
        view.post {
            // View layout'u tamamlandıktan sonra animasyonu başlat
            try { // Animasyon sırasında hata olursa yakalamak için
                // Başlangıç noktası (cx, cy) - Şimdilik alt-orta varsayalım
                val cx = view.width / 2
                val cy = view.height

                val finalRadius = hypot(cx.toDouble(), cy.toDouble()).toFloat()

                val anim = ViewAnimationUtils.createCircularReveal(
                    view, cx, cy, 0f, finalRadius
                )
                anim.duration = 600 // Süreyi ayarla

                // Animasyon bittiğinde ne olacağını dinle (isteğe bağlı)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        // İstersen animasyon bitince ek bir işlem yapabilirsin
                    }
                })

                // View'ı görünür yap ve animasyonu başlat
                view.visibility = View.VISIBLE
                anim.start()

            } catch (e: Exception) {
                // Hata olursa view'ı normal şekilde görünür yap
                view.visibility = View.VISIBLE
                e.printStackTrace()
            }
        }
        // --- Animasyon Bitti ---


        // --- Listener Kurulumları ---
        binding.card1.setOnClickListener { selectCard(1) }
        binding.card2.setOnClickListener { selectCard(2) }

        binding.goToPageButton.setOnClickListener {
            when (selectedCard) {
                1 -> navigateToPage1(it) // it -> goToPageButton view'ı
                2 -> navigateToPage2(it)
                else -> Toast.makeText(requireContext(), "Lütfen bir kart seçin!", Toast.LENGTH_SHORT).show()
            }
        }
        // --- Bitti ---

        // Başlangıçta kartların seçili olmayan görünümünü ayarla (isteğe bağlı)
        updateCardAppearance()
    }

    private fun selectCard(cardNumber: Int) {
        // Ses çalma
        clickSound?.let { sound ->
            if (sound.isPlaying) {
                sound.seekTo(0)
            }
            sound.start()
        }

        selectedCard = cardNumber
        updateCardAppearance() // Kart görünümlerini güncelle
    }

    // Kart görünümlerini güncelleyen yardımcı fonksiyon
    private fun updateCardAppearance() {
        // Renkleri ve diğer değerleri al
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.selectedColor) // Bu renkleri colors.xml'de tanımla
        val deselectedColor = ContextCompat.getColor(requireContext(), R.color.deselectedColor) // Bu renkleri colors.xml'de tanımla
        val selectedElevation = 16f // dp cinsinden olabilir, pixel'e çevirmek gerekebilir
        val deselectedElevation = 4f
        val selectedScale = 1.05f
        val deselectedScale = 1f
        val animDuration = 150L

        // Kart 1 görünümünü ayarla
        val card1Selected = selectedCard == 1
        binding.card1.setCardBackgroundColor(if (card1Selected) selectedColor else deselectedColor)
        binding.card1.cardElevation = if (card1Selected) selectedElevation else deselectedElevation
        binding.card1.animate()
            .scaleX(if (card1Selected) selectedScale else deselectedScale)
            .scaleY(if (card1Selected) selectedScale else deselectedScale)
            .setDuration(animDuration).start()

        // Kart 2 görünümünü ayarla
        val card2Selected = selectedCard == 2
        binding.card2.setCardBackgroundColor(if (card2Selected) selectedColor else deselectedColor)
        binding.card2.cardElevation = if (card2Selected) selectedElevation else deselectedElevation
        binding.card2.animate()
            .scaleX(if (card2Selected) selectedScale else deselectedScale)
            .scaleY(if (card2Selected) selectedScale else deselectedScale)
            .setDuration(animDuration).start()
    }


    private fun navigateToPage1(view: View) {
        try {
            Navigation.findNavController(view).navigate(R.id.fairyToGeneration)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun navigateToPage2(view: View) {
        try {
            Navigation.findNavController(view).navigate(R.id.fairyToSpeech)
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // MediaPlayer'ı serbest bırak
        clickSound?.release()
        clickSound = null
        // Binding referansını temizle
        _binding = null // Memory Leak önlemek için önemli
    }
}