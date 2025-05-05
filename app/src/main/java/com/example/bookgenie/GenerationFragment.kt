package com.example.bookgenie

import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.bookgenie.databinding.FragmentGenerationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import java.io.File
import java.io.FileOutputStream
import java.util.UUID


class GenerationFragment : Fragment() {
    private lateinit var binding: FragmentGenerationBinding

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val sampleText = "Once upon a time, there was a little girl..."

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentGenerationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnSaveStory.setOnClickListener {
            saveStoryToFirestore()
        }
    }

    private fun saveStoryToFirestore() {
        val userId = auth.currentUser?.uid ?: return
        val story = Story(
            title = "Girl",
            content = sampleText,
            audioBase64 = ""
        )

        firestore.collection("users")
            .document(userId)
            .collection("stories")
            .add(story)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Fairytale saved!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}