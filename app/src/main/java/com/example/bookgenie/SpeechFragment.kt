package com.example.bookgenie

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.bookgenie.databinding.FragmentSpeechBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SpeechFragment : Fragment() {

    private lateinit var binding: FragmentSpeechBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: StoryAdapter
    private val storyList = mutableListOf<Story>()
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var selectedStory: Story? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSpeechBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = StoryAdapter(storyList) { story ->
            selectedStory = story
            synthesizeSpeech(story.content)
        }

        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        loadStories()

        binding.btnPlayPause.setOnClickListener {
            if (selectedStory != null) {
                if (isPlaying) pauseAudio() else startAudio()
            } else {
                Toast.makeText(requireContext(), "Please select a story first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRewind.setOnClickListener {
            mediaPlayer?.seekTo(mediaPlayer?.currentPosition?.minus(5000) ?: 0)
        }

        binding.btnForward.setOnClickListener {
            mediaPlayer?.seekTo(mediaPlayer?.currentPosition?.plus(5000) ?: 0)
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    binding.textCurrentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun loadStories() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .document(userId)
            .collection("stories")
            .get()
            .addOnSuccessListener { snapshot ->
                storyList.clear()
                for (doc in snapshot) {
                    val story = doc.toObject(Story::class.java)
                    storyList.add(story)
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun synthesizeSpeech(text: String) {
        val apiKey = "1tTlI9RXj22QqmsthBAbun8RuY7phVfPejlraMfdAmD3C0zsv4zIJQQJ99BEACYeBjFXJ3w3AAAYACOGpaD3"
        val region = "eastus"
        val client = OkHttpClient()
        val url = "https://$region.tts.speech.microsoft.com/cognitiveservices/v1"

        val xmlBody = """
            <speak version='1.0' xml:lang='en-US'>
              <voice xml:lang='en-US' name='en-US-AriaNeural'>$text</voice>
            </speak>
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", apiKey)
            .addHeader("Content-Type", "application/ssml+xml")
            .addHeader("X-Microsoft-OutputFormat", "audio-16khz-32kbitrate-mono-mp3")
            .post(xmlBody.toRequestBody("application/ssml+xml".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AzureTTS", "Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { body ->
                    val audioBytes = body.bytes()
                    requireActivity().runOnUiThread {
                        playAudioFromBytes(audioBytes)
                    }
                }
            }
        })
    }

    private fun playAudioFromBytes(audioBytes: ByteArray) {
        try {
            val tempFile = File.createTempFile("temp_audio", ".mp3", requireContext().cacheDir)
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                binding.btnPlayPause.setImageResource(R.drawable.stop_button_foreground)
            }
            isPlaying = true

            binding.seekBar.max = mediaPlayer!!.duration
            binding.textTotalTime.text = formatTime(mediaPlayer!!.duration)
            updateSeekBar()

            mediaPlayer?.setOnCompletionListener {
                isPlaying = false
                binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
                binding.seekBar.progress = 0
                binding.textCurrentTime.text = formatTime(0)
            }

        } catch (e: Exception) {
            Log.e("AudioPlay", "Playback error: ${e.message}")
            Toast.makeText(requireContext(), "Error playing audio.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAudio() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                isPlaying = true
                binding.btnPlayPause.setImageResource(R.drawable.stop_button_foreground)
                updateSeekBar()
            }
        }
    }

    private fun pauseAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
                binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
            }
        }
    }

    private fun updateSeekBar() {
        mediaPlayer?.let { player ->
            binding.seekBar.progress = player.currentPosition
            binding.textCurrentTime.text = formatTime(player.currentPosition)
            if (player.isPlaying) {
                handler.postDelayed({ updateSeekBar() }, 1000)
            }
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val minutes = (milliseconds / 1000) / 60
        val seconds = (milliseconds / 1000) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        mediaPlayer = null
        handler.removeCallbacksAndMessages(null)
    }
}
