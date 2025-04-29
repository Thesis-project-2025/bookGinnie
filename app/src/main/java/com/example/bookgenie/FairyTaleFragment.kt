package com.example.bookgenie

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.Navigation
import com.example.bookgenie.databinding.FragmentFairyTaleBinding

class FairyTaleFragment : Fragment() {
    private var selectedCard: Int = 0 // 0 = none, 1 = card1, 2 = card2
    private lateinit var binding: FragmentFairyTaleBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentFairyTaleBinding.inflate(inflater, container, false)

        // Card 1 click
        binding.card1.setOnClickListener {
            // Rengi değiştir
            binding.card1.setCardBackgroundColor(resources.getColor(R.color.selectedColor, null))
            binding.card2.setCardBackgroundColor(resources.getColor(R.color.deselectedColor, null))
            selectedCard = 1
        }

        // Card 2 click
        binding.card2.setOnClickListener {
            // Rengi değiştir
            binding.card2.setCardBackgroundColor(resources.getColor(R.color.selectedColor, null))
            binding.card1.setCardBackgroundColor(resources.getColor(R.color.deselectedColor, null))
            selectedCard = 2
        }

        // Button click
        binding.goToPageButton.setOnClickListener {
            when (selectedCard) {
                1 -> navigateToPage1(it)
                2 -> navigateToPage2(it)
                else -> Toast.makeText(requireContext(), "Lütfen bir kart seçin!", Toast.LENGTH_SHORT).show()
            }
        }

    return binding.root
    }

    private fun navigateToPage1(view: View) {
        // Sayfa 1'e git
        Navigation.findNavController(view).navigate(R.id.fairyToGeneration)
    }

    private fun navigateToPage2(view: View) {
        // Sayfa 2'ye git
        Navigation.findNavController(view).navigate(R.id.fairyToSpeech)
    }

}