package com.example.bookgenie

import StoryViewModel // ViewModel import'unuzu kontrol edin
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.example.bookgenie.databinding.FragmentGenerationBinding // View Binding import'u
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


// Story data class'ınızın bu şekilde olduğunu varsayıyoruz.
// Projenizde uygun bir pakette tanımlı olmalıdır.
// data class Story(val title: String = "", val content: String = "", val audioBase64: String = "")

class GenerationFragment : Fragment() {

    // View Binding
    private var _binding: FragmentGenerationBinding? = null
    private val binding get() = _binding!!

    // ViewModel
    private val viewModel: StoryViewModel by viewModels() // StoryViewModel import'unuzu kontrol edin

    // Firestore & Auth
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // API'den gelen gerçek metinle güncellenecek ve kaydetmek için kullanılacak
    private var currentGeneratedStory: String = ""

    // Typewriter Animasyonu için değişkenler
    private var typewriterHandler: Handler? = null
    private var typewriterRunnable: Runnable? = null
    private var currentAnimatingText: String? = null
    private var currentAnimatingIndex: Int = 0
    private val TYPEWRITER_DELAY = 25L // Milisaniye cinsinden her harf arası gecikme

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGenerationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupEditTextListener()
        observeViewModel()

        // Başlangıçta masal metnini ve kaydet butonunu ayarla
        binding.masalText.text = ""
        binding.btnSaveStory.visibility = View.GONE // Başlangıçta görünmez

        // Kaydetme butonu dinleyicisi
        binding.btnSaveStory.setOnClickListener {
            saveStoryToFirestore()
        }
    }

    private fun setupEditTextListener() {
        binding.promptEditText.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {

                if (viewModel.isLoading.value == true) {
                    Toast.makeText(requireContext(), "Lütfen mevcut işlemin bitmesini bekleyin.", Toast.LENGTH_SHORT).show()
                    return@OnEditorActionListener true
                }

                val idea = binding.promptEditText.text.toString().trim()
                if (idea.isNotEmpty()) {
                    viewModel.generateStory(idea)
                    hideKeyboard()
                    binding.masalText.text = "" // Yeni istek öncesi eski metni temizle
                    typewriterHandler?.removeCallbacks(typewriterRunnable!!) // Varsa eski animasyonu durdur
                    currentGeneratedStory = "" // Yeni istekte eski hikayeyi temizle
                    binding.btnSaveStory.visibility = View.GONE // Yeni istekte kaydet butonunu gizle
                    binding.btnSaveStory.isEnabled = false // Butonu pasif yap
                } else {
                    Toast.makeText(requireContext(), "Lütfen bir masal fikri girin!", Toast.LENGTH_SHORT).show()
                }
                return@OnEditorActionListener true
            }
            return@OnEditorActionListener false
        })
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner, Observer { isLoading ->
            binding.loadingLottieView.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.promptEditText.isEnabled = !isLoading
            if (isLoading) {
                binding.btnSaveStory.isEnabled = false
            } else {
                // Yükleme bittiğinde, eğer animasyon da bittiyse ve bir hikaye varsa buton aktif edilebilir.
                // Bu, animateText'in sonundaki mantıkla yönetilecek.
                // Eğer currentGeneratedStory doluysa ve animasyon bittiyse (aşağıdaki storyResult gözlemcisi sonrası)
                // buton zaten enable edilmiş olmalı.
                // Aktif bir animasyon olup olmadığını kontrol et
                val isAnimating = typewriterRunnable != null && typewriterHandler?.hasCallbacks(typewriterRunnable!!) == true
                if (currentGeneratedStory.isNotBlank() && !isAnimating) {
                    binding.btnSaveStory.isEnabled = true
                }
            }
        })

        viewModel.storyResult.observe(viewLifecycleOwner, Observer { story ->
            if (story != null) {
                animateText(story)
                binding.btnSaveStory.visibility = View.VISIBLE
                binding.btnSaveStory.isEnabled = false // Animasyon başlayınca pasif, bitince aktif olacak
            } else {
                if(binding.loadingLottieView.visibility == View.GONE && binding.promptEditText.text.toString().isNotBlank()){
                    // İsteğe bağlı: story null geldiğinde bir hata mesajı
                    // Toast.makeText(requireContext(), "Masal oluşturulamadı, tekrar deneyin.", Toast.LENGTH_SHORT).show()
                }
            }
        })

        viewModel.error.observe(viewLifecycleOwner, Observer { error ->
            if (error != null) {
                Toast.makeText(requireContext(), "Hata: $error", Toast.LENGTH_LONG).show()
                binding.masalText.text = "Bir şeyler ters gitti..."
                typewriterHandler?.removeCallbacks(typewriterRunnable!!)
                currentGeneratedStory = ""
                binding.btnSaveStory.visibility = View.GONE
                binding.btnSaveStory.isEnabled = false
                binding.loadingLottieView.visibility = View.GONE // Hata durumunda yükleme animasyonunu gizle
            }
        })
    }

    private fun animateText(fullText: String) {
        currentAnimatingText = fullText
        currentAnimatingIndex = 0
        binding.masalText.text = "" // Önceki metni temizle

        // Mevcut animasyon varsa durdur
        typewriterHandler?.removeCallbacks(typewriterRunnable!!)
        typewriterHandler = Handler(Looper.getMainLooper())

        typewriterRunnable = object : Runnable {
            override fun run() {
                val textToAnimate = currentAnimatingText
                if (textToAnimate == null) {
                    currentGeneratedStory = ""
                    binding.btnSaveStory.isEnabled = false
                    return
                }

                if (currentAnimatingIndex < textToAnimate.length) {
                    binding.masalText.append(textToAnimate[currentAnimatingIndex].toString())

                    // ---- YENİ EKLENEN SATIR ----
                    // ScrollView'u en aşağıya kaydır
                    binding.scrollViewMasal.post { // post ile UI güncellendikten sonra çalışmasını garantile
                        binding.scrollViewMasal.fullScroll(View.FOCUS_DOWN)
                    }
                    // ---- YENİ EKLENEN SATIR ----

                    currentAnimatingIndex++
                    typewriterHandler?.postDelayed(this, TYPEWRITER_DELAY)
                } else {
                    // Animasyon bitti
                    currentGeneratedStory = textToAnimate
                    if (currentGeneratedStory.isNotBlank()) {
                        binding.btnSaveStory.isEnabled = true
                    } else {
                        binding.btnSaveStory.isEnabled = false
                    }
                    // Animasyon bittikten sonra son bir kez daha kaydırmayı garantileyebiliriz
                    binding.scrollViewMasal.post {
                        binding.scrollViewMasal.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
        // Animasyonu doğrudan başlat
        typewriterHandler?.postDelayed(typewriterRunnable!!, TYPEWRITER_DELAY)
    }


    private fun hideKeyboard() {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.promptEditText.windowToken, 0)
    }

    private fun saveStoryToFirestore() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "Kaydetmek için lütfen giriş yapın.", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentGeneratedStory.isBlank()) {
            Toast.makeText(requireContext(), "Kaydedilecek bir masal üretilmedi.", Toast.LENGTH_SHORT).show()
            return
        }

        val titleFromPrompt = binding.promptEditText.text.toString().trim()
        val storyTitle = if (titleFromPrompt.isNotEmpty()) {
            titleFromPrompt.take(50) // Prompt'tan ilk 50 karakteri al
        } else {
            currentGeneratedStory.take(50).replace("\n", " ") + "..." // Masaldan ilk 50 karakter
        }

        if (storyTitle.isBlank()){
            Toast.makeText(requireContext(), "Masal başlığı belirlenemedi.", Toast.LENGTH_SHORT).show()
            return
        }

        // Story data class'ınızın olduğunu ve doğru import edildiğini varsayıyoruz
        // Örnek: import com.example.bookgenie.models.Story
        val storyToSave = Story( // Story sınıfınızın adını ve importunu kontrol edin
            title = storyTitle,
            content = currentGeneratedStory,
            audioBase64 = "" // Şimdilik boş
        )

        binding.btnSaveStory.isEnabled = false
        binding.loadingLottieView.visibility = View.VISIBLE

        firestore.collection("users")
            .document(userId)
            .collection("stories")
            .add(storyToSave)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Masal başarıyla kaydedildi!", Toast.LENGTH_SHORT).show()
                binding.btnSaveStory.isEnabled = true
                binding.loadingLottieView.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Masal kaydedilirken hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnSaveStory.isEnabled = true
                binding.loadingLottieView.visibility = View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        typewriterHandler?.removeCallbacks(typewriterRunnable!!)
        typewriterRunnable = null
        typewriterHandler = null
        _binding = null
    }
}