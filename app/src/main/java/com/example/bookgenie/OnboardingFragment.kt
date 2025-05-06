package com.example.bookgenie

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
// import androidx.core.content.ContextCompat // Gerek kalmadı
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.bookgenie.databinding.FragmentOnboardingBinding
// import com.example.bookgenie.drawable.AnimatedGradientDrawable // Gerek kalmadı

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    // private var animatedBackground: AnimatedGradientDrawable? = null // KALDIRILDI

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Sadece buton listenerları kaldı
        binding.logInButtonId1.setOnClickListener {
            try {
                Navigation.findNavController(it).navigate(R.id.logInAction)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        binding.signUpButtonId1.setOnClickListener {
            try {
                Navigation.findNavController(it).navigate(R.id.signUpAction)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // onResume ve onPause içindeki animasyon kodları KALDIRILDI
    // override fun onResume() { ... }
    // override fun onPause() { ... }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // animatedBackground ile ilgili kodlar KALDIRILDI
    }
}