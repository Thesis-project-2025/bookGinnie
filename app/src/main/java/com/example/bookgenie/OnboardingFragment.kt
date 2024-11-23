package com.example.bookgenie

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.Navigation
import com.example.bookgenie.databinding.FragmentOnboardingBinding


class OnboardingFragment : Fragment() {
private lateinit var  binding: FragmentOnboardingBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        binding.logInButtonId1.setOnClickListener{
            Navigation.findNavController(it).navigate(R.id.logInAction)
        }
        binding.signUpButtonId1.setOnClickListener{
            Navigation.findNavController(it).navigate(R.id.signUpAction)
        }
        return binding.root
    }



}