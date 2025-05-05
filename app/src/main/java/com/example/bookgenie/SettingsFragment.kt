package com.example.bookgenie

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bookgenie.api.RetrofitInstance
import com.example.bookgenie.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private lateinit var binding: FragmentSettingsBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)

        binding.buttonGetRecommendations.setOnClickListener {
            getRecommendations()
        }

        binding.buttonGetGenreRecommendations.setOnClickListener {
            getGenreRecommendations()
        }

        binding.buttonGetSimilarBooks.setOnClickListener {
            getSimilarBooks()
        }

        return binding.root
    }

    private fun getRecommendations() {
        val userId = "0g83tj4a90TDa6zAFPz7TERTvjl2" // test kullanıcı

        // Coroutine içinde API çağrısı
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getRecommendations(userId)
                val resultText = buildString {
                    append("Öneriler:\n\n")
                    response.recommendations.forEach {
                        append("Book ID: ${it.book_id}, Score: ${it.score}\n")
                    }
                }
                binding.textResult.text = resultText
            } catch (e: Exception) {
                binding.textResult.text = "Hata oluştu: ${e.message}"
            }
        }
    }

    private fun getGenreRecommendations() {
        val userId = "0g83tj4a90TDa6zAFPz7TERTvjl2"

        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getRecommendationsByGenre(userId, topN = 10)
                val resultText = buildString {
                    append("Genre Bazlı Öneriler:\n\n")
                    response.recommendations.forEach {
                        append("Book ID: ${it.book_id}, Score: ${it.score}\n")
                    }
                }
                binding.textResult.text = resultText
            } catch (e: Exception) {
                binding.textResult.text = "Hata oluştu: ${e.message}"
            }
        }
    }

    private fun getSimilarBooks() {
        val bookId = 7103 // test için sabit

        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getSimilarBooks(bookId)
                val resultText = buildString {
                    append("Benzer Kitaplar:\n\n")
                    response.recommendations.forEach {
                        append("Book ID: ${it.book_id}, Score: ${it.score}\n")
                    }
                }
                binding.textResult.text = resultText
            } catch (e: Exception) {
                binding.textResult.text = "Hata oluştu: ${e.message}"
            }
        }
    }


}
