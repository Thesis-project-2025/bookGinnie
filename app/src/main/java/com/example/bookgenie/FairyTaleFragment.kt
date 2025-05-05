package com.example.bookgenie

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.navigation.Navigation
import com.example.bookgenie.databinding.FragmentFairyTaleBinding
import android.media.MediaPlayer

class FairyTaleFragment : Fragment() {
    private var selectedCard: Int = 0
    private lateinit var binding: FragmentFairyTaleBinding

    private lateinit var clickSound: MediaPlayer

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFairyTaleBinding.inflate(inflater, container, false)

        binding.card1.setOnClickListener { selectCard(1) }
        binding.card2.setOnClickListener { selectCard(2) }

        binding.goToPageButton.setOnClickListener {
            when (selectedCard) {
                1 -> navigateToPage1(it)
                2 -> navigateToPage2(it)
                else -> Toast.makeText(requireContext(), "Please choose a card!", Toast.LENGTH_SHORT).show()
            }
        }

        clickSound = MediaPlayer.create(requireContext(), R.raw.click_sound)


        return binding.root
    }

    private fun selectCard(cardNumber: Int) {
        if (clickSound.isPlaying) {
            clickSound.seekTo(0)
        }
        clickSound.start()

        selectedCard = cardNumber
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.selectedColor)
        val deselectedColor = ContextCompat.getColor(requireContext(), R.color.deselectedColor)

        val selectedElevation = 16f
        val deselectedElevation = 4f
        val selectedScale = 1.05f
        val deselectedScale = 1f

        val card1Selected = cardNumber == 1
        val card2Selected = cardNumber == 2

        binding.card1.setCardBackgroundColor(if (card1Selected) selectedColor else deselectedColor)
        binding.card2.setCardBackgroundColor(if (card2Selected) selectedColor else deselectedColor)

        binding.card1.cardElevation = if (card1Selected) selectedElevation else deselectedElevation
        binding.card2.cardElevation = if (card2Selected) selectedElevation else deselectedElevation

        binding.card1.animate().scaleX(if (card1Selected) selectedScale else deselectedScale).scaleY(if (card1Selected) selectedScale else deselectedScale).setDuration(150).start()
        binding.card2.animate().scaleX(if (card2Selected) selectedScale else deselectedScale).scaleY(if (card2Selected) selectedScale else deselectedScale).setDuration(150).start()
    }


    private fun navigateToPage1(view: View) {
        Navigation.findNavController(view).navigate(R.id.fairyToGeneration)
    }

    private fun navigateToPage2(view: View) {
        Navigation.findNavController(view).navigate(R.id.fairyToSpeech)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::clickSound.isInitialized) {
            clickSound.release()
        }
    }

}
