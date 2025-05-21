package com.example.bookgenie

import android.Manifest
import android.content.pm.PackageManager
// import android.graphics.Color // Artık doğrudan kullanılmıyor
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bookgenie.databinding.FragmentSpeechBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// data class Story(val id: String = "", val title: String = "", val content: String = "") // Story sınıfınızın tanımı


class SpeechFragment : Fragment() {

    private var _binding: FragmentSpeechBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: StoryAdapter
    private val storyList = mutableListOf<Story>()

    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var barVisualizerView: BarVisualizerView? = null // Custom view referansı
    private var isPlaying = false
    private var currentPlayingStory: Story? = null
    private val handler = Handler(Looper.getMainLooper())

    private var isInPlayerMode = false

    private val AZURE_TTS_API_KEY = "1tTlI9RXj22QqmsthBAbun8RuY7phVfPejlraMfdAmD3C0zsv4zIJQQJ99BEACYeBjFXJ3w3AAAYACOGpaD3"
    private val AZURE_TTS_REGION = "eastus"

    companion object {
        private const val TAG = "SpeechFragment"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSpeechBinding.inflate(inflater, container, false)
        Log.d(TAG, "onCreateView called")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        barVisualizerView = binding.audioVisualizerView // XML'deki custom view'a referans al

        setupRecyclerView()
        setupPlayerClickListeners()
        setupBackButtonHandler()

        if (storyList.isEmpty()) {
            loadStories()
        }
        showListView()
    }

    private fun setupRecyclerView() {
        adapter = StoryAdapter(storyList) { story ->
            showPlayerView(story)
        }
        binding.recyclerViewStories.adapter = adapter
        binding.recyclerViewStories.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupPlayerClickListeners() {
        binding.btnPlayPause.setOnClickListener {
            Log.d(TAG, "Play/Pause button clicked. isPlaying: $isPlaying, mediaPlayer: $mediaPlayer, currentStory: $currentPlayingStory")
            if (mediaPlayer == null || currentPlayingStory == null) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Audio not ready or no story selected.", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            if (isPlaying) {
                pauseAudio()
            } else {
                startAudio()
            }
        }

        binding.btnRewind.setOnClickListener {
            mediaPlayer?.let {
                if (it.duration > 0) {
                    try {
                        val newPosition = it.currentPosition - 5000
                        it.seekTo(if (newPosition < 0) 0 else newPosition)
                        updateCurrentTimeDisplay(it.currentPosition)
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "Error seeking rewind: ${e.message}")
                    }
                }
            }
        }

        binding.btnForward.setOnClickListener {
            mediaPlayer?.let {
                if (it.duration > 0) {
                    try {
                        val newPosition = it.currentPosition + 5000
                        if (newPosition < it.duration) {
                            it.seekTo(newPosition)
                        } else {
                            it.seekTo(it.duration)
                        }
                        updateCurrentTimeDisplay(it.currentPosition)
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "Error seeking forward: ${e.message}")
                    }
                }
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer != null && mediaPlayer!!.duration > 0) {
                    try {
                        mediaPlayer?.seekTo(progress)
                        updateCurrentTimeDisplay(progress)
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "Error seeking from seekBar: ${e.message}")
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupBackButtonHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back button pressed. isInPlayerMode: $isInPlayerMode")
                if (isInPlayerMode) {
                    showListView()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun showListView() {
        if (_binding == null) return
        Log.d(TAG, "showListView called")

        binding.listTitle.visibility = View.VISIBLE
        binding.recyclerViewStories.visibility = View.VISIBLE
        binding.playerViewContainer.visibility = View.GONE

        releaseMediaPlayerAndVisualizer()
        isInPlayerMode = false
    }

    private fun showPlayerView(story: Story) {
        if (_binding == null) return
        Log.d(TAG, "showPlayerView called for story: ${story.title}")

        binding.listTitle.visibility = View.GONE
        binding.recyclerViewStories.visibility = View.GONE
        binding.playerViewContainer.visibility = View.VISIBLE

        releaseMediaPlayerAndVisualizer()

        currentPlayingStory = story
        binding.playerStoryTitle.text = story.title

        binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
        binding.btnPlayPause.isEnabled = false
        binding.seekBar.progress = 0
        binding.seekBar.max = 0
        updateCurrentTimeDisplay(0)
        binding.textTotalTime.text = formatTime(0)

        barVisualizerView?.updateVisualizer(null) // Başlangıçta temizle
        Log.d(TAG, "BarVisualizerView cleared in showPlayerView")


        if (story.content.isNotBlank()) {
            synthesizeSpeech(story.content)
        } else {
            Toast.makeText(requireContext(), "Story content is empty.", Toast.LENGTH_SHORT).show()
            binding.btnPlayPause.isEnabled = true
            showListView()
        }
        isInPlayerMode = true
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun synthesizeSpeech(text: String) {
        if (_binding == null) return
        Log.d(TAG, "synthesizeSpeech called")

        binding.btnPlayPause.isEnabled = false
        Toast.makeText(requireContext(), "Generating audio...", Toast.LENGTH_SHORT).show()

        val client = OkHttpClient()
        val url = "https://$AZURE_TTS_REGION.tts.speech.microsoft.com/cognitiveservices/v1"
        val xmlBody = """<speak version='1.0' xml:lang='en-US'><voice xml:lang='en-US' name='en-US-AriaNeural'>${escapeXml(text)}</voice></speak>""".trimIndent()
        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", AZURE_TTS_API_KEY)
            .addHeader("Content-Type", "application/ssml+xml")
            .addHeader("X-Microsoft-OutputFormat", "audio-16khz-32kbitrate-mono-mp3")
            .post(xmlBody.toRequestBody("application/ssml+xml".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "TTS Synthesis Error: ${e.message}", e)
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    Toast.makeText(requireContext(), "TTS Synthesis Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnPlayPause.isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "TTS Synthesis Unexpected code ${response.code}: $errorBody")
                    activity?.runOnUiThread {
                        if (_binding == null) return@runOnUiThread
                        Toast.makeText(requireContext(), "TTS Synthesis Error: ${response.code}", Toast.LENGTH_LONG).show()
                        binding.btnPlayPause.isEnabled = true
                    }
                    response.close()
                    return
                }
                response.body?.use { body ->
                    try {
                        val audioBytes = body.bytes()
                        Log.d(TAG, "TTS Synthesis successful, audio bytes received: ${audioBytes.size}")
                        activity?.runOnUiThread {
                            if (_binding == null) return@runOnUiThread
                            playAudioFromBytes(audioBytes)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error reading audio bytes: ${e.message}", e)
                        activity?.runOnUiThread {
                            if (_binding == null) return@runOnUiThread
                            Toast.makeText(requireContext(), "Error processing audio.", Toast.LENGTH_SHORT).show()
                            binding.btnPlayPause.isEnabled = true
                        }
                    }
                } ?: run {
                    Log.e(TAG, "TTS Response body is null")
                    activity?.runOnUiThread {
                        if (_binding == null) return@runOnUiThread
                        Toast.makeText(requireContext(), "TTS received no data.", Toast.LENGTH_SHORT).show()
                        binding.btnPlayPause.isEnabled = true
                    }
                }
            }
        })
    }

    private fun playAudioFromBytes(audioBytes: ByteArray) {
        if (_binding == null) return
        Log.d(TAG, "playAudioFromBytes called")

        try {
            val tempFile = File.createTempFile("temp_audio", ".mp3", requireContext().cacheDir).apply {
                deleteOnExit()
            }
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            releaseMediaPlayerAndVisualizer()
            mediaPlayer = MediaPlayer()

            mediaPlayer?.apply {
                setDataSource(tempFile.absolutePath)

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer Error: what $what, extra $extra")
                    if (_binding == null) return@setOnErrorListener true
                    Toast.makeText(requireContext(), "Playback Error ($what, $extra)", Toast.LENGTH_LONG).show()
                    releaseMediaPlayerAndVisualizer()
                    if (_binding != null) {
                        binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
                        binding.btnPlayPause.isEnabled = true
                        binding.seekBar.progress = 0
                        binding.seekBar.max = 0
                        updateCurrentTimeDisplay(0)
                        binding.textTotalTime.text = formatTime(0)
                        barVisualizerView?.updateVisualizer(null)
                        Log.d(TAG, "BarVisualizerView cleared on MediaPlayer error")
                    }
                    true
                }

                setOnCompletionListener {
                    if (_binding == null) return@setOnCompletionListener
                    Log.d(TAG, "MediaPlayer onCompletion")
                    this@SpeechFragment.isPlaying = false
                    visualizer?.enabled = false
                    if (_binding != null) {
                        binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
                        binding.seekBar.progress = mediaPlayer?.duration ?: 0
                        updateCurrentTimeDisplay(mediaPlayer?.duration ?: 0)
                        barVisualizerView?.updateVisualizer(null)
                        Log.d(TAG, "BarVisualizerView cleared on completion")
                    }
                    handler.removeCallbacksAndMessages(null)
                }

                prepare()
                Log.d(TAG, "MediaPlayer prepared. Audio Session ID: $audioSessionId")
                start()
                this@SpeechFragment.isPlaying = true
                Log.d(TAG, "MediaPlayer started. isPlaying: ${this@SpeechFragment.isPlaying}")


                if (_binding != null) {
                    binding.btnPlayPause.setImageResource(R.drawable.stop_button_foreground)
                    binding.btnPlayPause.isEnabled = true
                    binding.seekBar.max = duration
                    binding.textTotalTime.text = formatTime(duration)
                }
                updateSeekBar()
                setupVisualizer()
            }
        } catch (e: IOException) {
            Log.e(TAG, "File or Network error in playAudioFromBytes: ${e.message}", e)
            handlePlaybackError()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MediaPlayer state error in playAudioFromBytes: ${e.message}", e)
            handlePlaybackError()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in playAudioFromBytes: ${e.message}", e)
            handlePlaybackError()
        }
    }

    private fun handlePlaybackError() {
        Log.e(TAG, "handlePlaybackError called")
        if (_binding != null) {
            Toast.makeText(requireContext(), "Error playing audio.", Toast.LENGTH_SHORT).show()
            binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
            binding.btnPlayPause.isEnabled = true
            barVisualizerView?.updateVisualizer(null)
            Log.d(TAG, "BarVisualizerView cleared in handlePlaybackError")
        }
        isPlaying = false
        releaseMediaPlayerAndVisualizer()
    }


    private fun setupVisualizer() {
        if (_binding == null) return

        val audioSessionId = mediaPlayer?.audioSessionId
        Log.d(TAG, "setupVisualizer called. MediaPlayer valid: ${mediaPlayer != null}, AudioSessionId: $audioSessionId")

        if (mediaPlayer == null || audioSessionId == null || audioSessionId == -38 || audioSessionId == 0) {
            Log.e(TAG, "Cannot setup Visualizer: MediaPlayer not ready or no valid audio session. Current ID: $audioSessionId")
            if (audioSessionId == -38) {
                Log.e(TAG, "Audio Session ID is MediaPlayer.ERROR_INVALID_OPERATION.")
            }
            return
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted. Requesting permission.")
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
            return
        }

        try {
            visualizer = Visualizer(audioSessionId)
            visualizer?.captureSize = Visualizer.getCaptureSizeRange()[1]
            // visualizer?.measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS

            val captureRate = Visualizer.getMaxCaptureRate()
            Log.d(TAG, "Setting Visualizer capture rate to: $captureRate")

            visualizer?.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (_binding == null || !isPlaying) {
                            barVisualizerView?.updateVisualizer(null)
                            return
                        }
                        barVisualizerView?.updateVisualizer(waveform)
                    }

                    override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        // FFT data
                    }
                },
                captureRate,
                true,  // Waveform
                false  // FFT
            )

            val status = visualizer?.setEnabled(true)
            Log.d(TAG, "Visualizer setEnabled(true) status: $status. Current enabled state after set: ${visualizer?.enabled}")
            if (status != Visualizer.SUCCESS) {
                Log.e(TAG, "Visualizer setEnabled(true) FAILED with status: $status")
            } else if (visualizer?.enabled == true) {
                Log.i(TAG, "Visualizer successfully enabled and active.")
            } else {
                Log.w(TAG, "Visualizer setEnabled(true) status SUCCESS, but visualizer.getEnabled() is false.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing or enabling Visualizer: ${e.message}", e)
            visualizer?.release()
            visualizer = null
        }
    }

    private fun releaseMediaPlayerAndVisualizer() {
        Log.d(TAG, "releaseMediaPlayerAndVisualizer called")
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null

        try {
            mediaPlayer?.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Error stopping mediaPlayer during release: ${e.message}")
        }
        mediaPlayer?.release()
        mediaPlayer = null

        isPlaying = false
        handler.removeCallbacksAndMessages(null)
        if (_binding != null) {
            barVisualizerView?.updateVisualizer(null)
            Log.d(TAG, "BarVisualizerView cleared in releaseMediaPlayerAndVisualizer")
        }
    }

    private fun startAudio() {
        if (_binding == null || mediaPlayer == null) return
        Log.d(TAG, "startAudio called")

        mediaPlayer?.let {
            if (!it.isPlaying) {
                try {
                    it.start()
                    isPlaying = true
                    if (visualizer != null && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            val status = visualizer?.setEnabled(true)
                            Log.d(TAG, "Visualizer re-enabled in startAudio. Status: $status, Enabled: ${visualizer?.enabled}")
                        } catch (e: IllegalStateException) {
                            Log.e(TAG, "Error re-enabling visualizer in startAudio: ${e.message}")
                        }
                    } else if (visualizer == null && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "Visualizer was null in startAudio, attempting to set up again.")
                        setupVisualizer()
                    }
                    Log.d(TAG, "MediaPlayer started, visualizer should be active if setup correctly. isEnabled: ${visualizer?.enabled}")
                    binding.btnPlayPause.setImageResource(R.drawable.stop_button_foreground)
                    updateSeekBar()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "MediaPlayer start failed: ${e.message}")
                    Toast.makeText(requireContext(), "Could not start audio.", Toast.LENGTH_SHORT).show()
                    isPlaying = false
                    visualizer?.enabled = false
                    binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
                }
            }
        }
    }

    private fun pauseAudio() {
        Log.d(TAG, "pauseAudio called, isPlaying: $isPlaying, mediaPlayer: $mediaPlayer")
        if (_binding == null) {
            Log.w(TAG, "pauseAudio called with null binding.")
            if (mediaPlayer != null && isPlaying) isPlaying = false
            visualizer?.enabled = false
            handler.removeCallbacksAndMessages(null)
            return
        }

        if (mediaPlayer == null) {
            Log.w(TAG, "pauseAudio called with null mediaPlayer.")
            if (isPlaying) {
                isPlaying = false
                binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
            }
            visualizer?.enabled = false
            handler.removeCallbacksAndMessages(null)
            return
        }

        try {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.pause()
                isPlaying = false
                visualizer?.enabled = false
                Log.d(TAG, "MediaPlayer paused successfully, visualizer enabled: ${visualizer?.enabled}")
                if (_binding != null) {
                    binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
                }
            } else {
                if (isPlaying) {
                    Log.w(TAG, "pauseAudio: MediaPlayer not playing, but isPlaying flag was true. Syncing.")
                    isPlaying = false
                    visualizer?.enabled = false
                    if (_binding != null) {
                        binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
                    }
                } else {
                    Log.d(TAG, "pauseAudio: Called when already paused/stopped.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer pause failed: ${e.message}", e)
            isPlaying = false
            visualizer?.enabled = false
            if (_binding != null) {
                binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
                Toast.makeText(requireContext(), "Error pausing audio.", Toast.LENGTH_SHORT).show()
            }
        } finally {
            handler.removeCallbacksAndMessages(null)
        }
    }


    private fun updateSeekBar() {
        if (_binding != null && mediaPlayer != null && isPlaying) {
            try {
                binding.seekBar.progress = mediaPlayer!!.currentPosition
                updateCurrentTimeDisplay(mediaPlayer!!.currentPosition)
                handler.postDelayed({ updateSeekBar() }, 1000)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "UpdateSeekBar: MediaPlayer not in valid state for getCurrentPosition: ${e.message}")
                isPlaying = false
                visualizer?.enabled = false
                if(_binding != null) {
                    binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
                }
                handler.removeCallbacksAndMessages(null)
            }
        } else {
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun updateCurrentTimeDisplay(milliseconds: Int) {
        if (_binding != null) {
            binding.textCurrentTime.text = formatTime(milliseconds)
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val minutes = (milliseconds / 1000) / 60
        val seconds = (milliseconds / 1000) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called. isPlaying: $isPlaying")
        if (isPlaying) {
            pauseAudio()
        } else {
            visualizer?.enabled = false
            handler.removeCallbacksAndMessages(null)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called. isInPlayerMode: $isInPlayerMode, mediaPlayer: $mediaPlayer, isPlaying: $isPlaying")
        if (_binding != null && isInPlayerMode && mediaPlayer != null) {
            try {
                if (mediaPlayer!!.isPlaying) {
                    if (!isPlaying) {
                        Log.w(TAG, "onResume: mediaPlayer isPlaying, but isPlaying flag was false. Syncing.")
                        isPlaying = true
                    }
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        visualizer?.enabled = true
                        Log.d(TAG, "onResume: Visualizer re-enabled.")
                    } else {
                        Log.w(TAG, "onResume: Visualizer not re-enabled, permission missing.")
                    }
                    binding.btnPlayPause.setImageResource(R.drawable.stop_button_foreground)
                    updateSeekBar()
                } else {
                    if (isPlaying) {
                        Log.w(TAG, "onResume: mediaPlayer is NOT Playing, but isPlaying flag was true. Syncing.")
                        isPlaying = false
                    }
                    visualizer?.enabled = false
                    binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
                    try {
                        if (mediaPlayer!!.duration > 0) {
                            binding.seekBar.progress = mediaPlayer!!.currentPosition
                            updateCurrentTimeDisplay(mediaPlayer!!.currentPosition)
                            binding.textTotalTime.text = formatTime(mediaPlayer!!.duration)
                        } else {
                            binding.seekBar.progress = 0
                            updateCurrentTimeDisplay(0)
                        }
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "onResume: Error accessing duration/position (player might not be prepared): ${e.message}")
                        binding.seekBar.progress = 0
                        updateCurrentTimeDisplay(0)
                    }
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "onResume: Error checking mediaPlayer.isPlaying: ${e.message}")
                releaseMediaPlayerAndVisualizer()
                if (_binding != null) {
                    binding.btnPlayPause.setImageResource(R.drawable.play_button_foreground)
                    binding.seekBar.progress = 0
                    binding.seekBar.max = 0
                    updateCurrentTimeDisplay(0)
                    binding.textTotalTime.text = formatTime(0)
                }
            }
        } else if (_binding != null && !isInPlayerMode && storyList.isEmpty()) {
            // loadStories()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called")
        releaseMediaPlayerAndVisualizer()
        _binding = null
    }

    private fun loadStories() {
        if (_binding == null) return
        val userId = auth.currentUser?.uid ?: return
        Log.d(TAG, "loadStories called for user: $userId")

        firestore.collection("users").document(userId).collection("stories").get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                storyList.clear()
                for (doc in snapshot) {
                    val story = doc.toObject(Story::class.java).copy(id = doc.id)
                    storyList.add(story)
                }
                adapter.notifyDataSetChanged()
                Log.d(TAG, "Stories loaded: ${storyList.size}")
                if (storyList.isEmpty()){
                    // Liste boş mesajı
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                Log.e(TAG, "Failed to load stories: ${it.message}", it)
                Toast.makeText(requireContext(), "Failed to load stories: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult called. requestCode: $requestCode")
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Log.d(TAG, "RECORD_AUDIO permission granted by user. Setting up visualizer if conditions met.")
                if (mediaPlayer != null && isPlaying) {
                    setupVisualizer()
                } else {
                    Log.d(TAG, "Permission granted, but MediaPlayer not ready or not playing. Visualizer setup deferred.")
                }
            } else {
                Log.w(TAG, "RECORD_AUDIO permission denied by user.")
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Permission for audio recording denied. Visualizer will not work.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
