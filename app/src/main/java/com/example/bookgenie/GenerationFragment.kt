package com.example.bookgenie

// Gerekli importlar eklendi
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
// ---

import StoryViewModel // ViewModel import'unuzu kontrol edin
import android.content.Context
import android.os.Build
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
import androidx.annotation.RequiresApi
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

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupEditTextListener()
        observeViewModel()
        startDecorativeAnimations() // <<< YENİ EKLENDİ: Dekoratif animasyonları başlat

        // Başlangıçta masal metnini ve kaydet butonunu ayarla
        binding.masalText.text = ""
        binding.btnSaveStory.visibility = View.GONE // Başlangıçta görünmez

        // Kaydetme butonu dinleyicisi
        binding.btnSaveStory.setOnClickListener {
            // <<< YENİ EKLENDİ: Buton tıklama animasyonu >>>
            it.animate()
                .scaleX(0.95f).scaleY(0.95f).setDuration(100)
                .withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction {
                        saveStoryToFirestore() // Animasyon sonrası orijinal fonksiyon çağrısı
                    }.start()
                }.start()
            // <<< Buton tıklama animasyonu sonu >>>
        }
    }

    // <<< YENİ EKLENEN METODLAR: Görsel geliştirmeler için >>>
    private fun startDecorativeAnimations() {
        // XML'deki imageView ID'lerinizin doğru olduğundan emin olun
        // Eğer FragmentGenerationBinding içinde bu ID'ler farklıysa, ona göre güncelleyin
        // Örneğin binding.imageViewStar1, binding.imageViewLion vb.
        try {
            animateViewPulse(binding.imageView10, 3000L, 1.0f, 1.15f)
            animateViewPulse(binding.imageView7, 3500L, 1.0f, 1.1f, 500L)
            animateViewPulse(binding.imageView5, 3200L, 1.0f, 1.12f, 200L)
            animateViewFloat(binding.imageView3, 4000L, -15f) // Yukarı doğru
            animateViewFloat(binding.imageView2, 4500L, 10f, 300L) // Aşağı doğru, gecikmeli
        } catch (e: Exception) {
            // Animasyon başlatılırken bir binding hatası olursa diye (örn: XML'de ID değişmişse)
            // Toast.makeText(requireContext(), "Dekoratif animasyonlar başlatılamadı.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun animateViewPulse(view: View?, duration: Long, fromScale: Float, toScale: Float, startDelay: Long = 0L) {
        view?.let { v ->
            val pulseAnim = ObjectAnimator.ofPropertyValuesHolder(
                v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, fromScale, toScale, fromScale),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, fromScale, toScale, fromScale)
            ).apply {
                this.duration = duration
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
                this.startDelay = startDelay
            }
            pulseAnim.start()
        }
    }

    private fun animateViewFloat(view: View?, duration: Long, translationValue: Float, startDelay: Long = 0L) {
        view?.let { v ->
            val floatAnim = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, translationValue, 0f).apply {
                this.duration = duration
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                this.startDelay = startDelay
            }
            floatAnim.start()
        }
    }

    private fun setLottieVisibility(show: Boolean) {
        val targetAlpha = if (show) 1.0f else 0.0f
        val startAlpha = if (show) 0.0f else 1.0f

        if (binding.loadingLottieView.visibility == View.VISIBLE && show && binding.loadingLottieView.alpha == 1f) return
        if (binding.loadingLottieView.visibility == View.GONE && !show) return


        val alphaAnimation = AlphaAnimation(startAlpha, targetAlpha)
        alphaAnimation.duration = 300 // ms
        alphaAnimation.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {
                if (show) {
                    binding.loadingLottieView.alpha = startAlpha
                    binding.loadingLottieView.visibility = View.VISIBLE
                }
            }
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                if (!show) {
                    binding.loadingLottieView.visibility = View.GONE
                }
                binding.loadingLottieView.alpha = targetAlpha
            }
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
        binding.loadingLottieView.startAnimation(alphaAnimation)
    }
    // <<< YENİ EKLENEN METODLAR SONU >>>

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
                    typewriterHandler?.removeCallbacks(typewriterRunnable ?: Runnable {}) // Varsa eski animasyonu durdur
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner, Observer { isLoading ->
            // binding.loadingLottieView.visibility = if (isLoading) View.VISIBLE else View.GONE // <<< ESKİ YÖNTEM
            setLottieVisibility(isLoading) // <<< YENİ EKLENDİ: Yumuşak geçiş
            binding.promptEditText.isEnabled = !isLoading
            if (isLoading) {
                binding.btnSaveStory.isEnabled = false
            } else {
                val isAnimating = typewriterRunnable != null && typewriterHandler?.hasCallbacks(typewriterRunnable ?: Runnable {}) == true
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
                typewriterHandler?.removeCallbacks(typewriterRunnable ?: Runnable {})
                currentGeneratedStory = ""
                binding.btnSaveStory.visibility = View.GONE
                binding.btnSaveStory.isEnabled = false
                // binding.loadingLottieView.visibility = View.GONE // <<< ESKİ YÖNTEM
                setLottieVisibility(false) // <<< YENİ EKLENDİ: Yumuşak geçiş (Hata durumunda yükleme animasyonunu gizle)
            }
        })
    }

    private fun animateText(fullText: String) {
        currentAnimatingText = fullText
        currentAnimatingIndex = 0
        binding.masalText.text = "" // Önceki metni temizle

        typewriterHandler?.removeCallbacks(typewriterRunnable ?: Runnable {})
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

                    binding.scrollViewMasal.post {
                        binding.scrollViewMasal.fullScroll(View.FOCUS_DOWN)
                    }

                    currentAnimatingIndex++
                    typewriterHandler?.postDelayed(this, TYPEWRITER_DELAY)
                } else {
                    currentGeneratedStory = textToAnimate
                    if (currentGeneratedStory.isNotBlank()) {
                        binding.btnSaveStory.isEnabled = true
                    } else {
                        binding.btnSaveStory.isEnabled = false
                    }
                    binding.scrollViewMasal.post {
                        binding.scrollViewMasal.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
        typewriterHandler?.postDelayed(typewriterRunnable!!, TYPEWRITER_DELAY)
    }


    private fun hideKeyboard() {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // Mevcut odaklanmış view'ı al, yoksa root view'ı kullan
        val currentFocusedView = activity?.currentFocus ?: view
        currentFocusedView?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
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
            // Story data class'ınızda userId ve timestamp gibi alanlar varsa bunları da ekleyin
            // userId = userId,
            // timestamp = com.google.firebase.Timestamp.now(),
            audioBase64 = "" // Şimdilik boş
        )

        binding.btnSaveStory.isEnabled = false
        // binding.loadingLottieView.visibility = View.VISIBLE // <<< ESKİ YÖNTEM
        setLottieVisibility(true) // <<< YENİ EKLENDİ: Yumuşak geçiş

        firestore.collection("users")
            .document(userId)
            .collection("stories")
            .add(storyToSave)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Masal başarıyla kaydedildi!", Toast.LENGTH_SHORT).show()
                binding.btnSaveStory.isEnabled = true
                // binding.loadingLottieView.visibility = View.GONE // <<< ESKİ YÖNTEM
                setLottieVisibility(false) // <<< YENİ EKLENDİ: Yumuşak geçiş
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Masal kaydedilirken hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnSaveStory.isEnabled = true
                // binding.loadingLottieView.visibility = View.GONE // <<< ESKİ YÖNTEM
                setLottieVisibility(false) // <<< YENİ EKLENDİ: Yumuşak geçiş
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        typewriterHandler?.removeCallbacks(typewriterRunnable ?: Runnable {})
        typewriterRunnable = null
        typewriterHandler = null
        _binding = null
    }
}