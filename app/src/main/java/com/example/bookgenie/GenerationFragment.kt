package com.example.bookgenie

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.bookgenie.databinding.FragmentGenerationBinding


class GenerationFragment : Fragment() {
    private lateinit var binding: FragmentGenerationBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentGenerationBinding.inflate(inflater, container, false)
        return binding.root
    }

}