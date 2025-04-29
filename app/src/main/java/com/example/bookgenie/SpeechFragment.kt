package com.example.bookgenie

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bookgenie.databinding.FragmentSpeechBinding

class SpeechFragment : Fragment() {
    private lateinit var binding: FragmentSpeechBinding
    private lateinit var adapter: StoryAdapter
    private var selectedStory: String? = null

    private var isPlaying = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSpeechBinding.inflate(inflater, container, false)

        val storyList = listOf("Küçük Denizkızı", "Ejderha Avı", "Orman Macerası", "Kayıp Krallık")

        adapter = StoryAdapter(storyList) { position ->
            selectedStory = storyList[position]
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.playButton.setOnClickListener {
            if (selectedStory == null) {
                Toast.makeText(requireContext(), "Please pick a fairytale!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isPlaying) {
                // Şu anda oynuyorsa, durdur
                stopStory()
            } else {
                // Başlat
                startStory()
            }
        }


        return binding.root
    }

    private fun animateButtonChange(iconRes: Int, text: String) {
        binding.playButton.animate()
            .alpha(0f)  // Şeffaf yap
            .setDuration(150)  // 150ms sürede
            .withEndAction {
                // İcon ve yazı değiştir
                binding.playButton.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
                binding.playButton.text = text

                // Geri görünür yap
                binding.playButton.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }


    private fun startStory() {
        isPlaying = true
        animateButtonChange(R.drawable.stop_button, "Stop")

        // Masalı başlatma işlemi
        Toast.makeText(requireContext(), "$selectedStory is starting!", Toast.LENGTH_SHORT).show()
    }

    private fun stopStory() {
        isPlaying = false
        animateButtonChange(R.drawable.play_button, "Play")

        // Masalı durdurma işlemi
        Toast.makeText(requireContext(), "Fairytale is stopped", Toast.LENGTH_SHORT).show()
    }


}