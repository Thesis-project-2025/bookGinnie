package com.example.bookgenie

import StoryViewModel // ViewModel import'unuzu kontrol edin
import android.content.Context
import android.os.Bundle
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
import com.example.bookgenie.databinding.FragmentGenerationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// ViewModel sınıfınızın adının StoryViewModel olduğunu varsayıyoruz
// import com.example.bookgenie.ui.story.StoryViewModel // ViewModel'inizin yolunu ekleyin


class GenerationFragment : Fragment() {

    // View Binding
    private var _binding: FragmentGenerationBinding? = null
    private val binding get() = _binding!!

    // ViewModel
    private val viewModel: StoryViewModel by viewModels()

    // Firestore & Auth
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Örnek metin (Firestore kaydı için) - Bunu API'den gelen gerçek metinle değiştirmelisiniz
    private var currentGeneratedStory: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGenerationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Buton tıklama yerine EditText dinleyicisini ayarla
        setupEditTextListener()
        observeViewModel()

        // Başlangıçta metni temizle
        binding.masalText.text = ""

        // Kaydetme butonu dinleyicisi (Eğer layout'ta varsa)
        // XML'de visibility="gone" olarak işaretli, görünür yapmak için:
        // binding.btnSaveStory.visibility = View.VISIBLE
        binding.btnSaveStory.setOnClickListener {
            saveStoryToFirestore()
        }
    }

    // YENİ: EditText için Listener Ayarlama
    private fun setupEditTextListener() {
        binding.promptEditText.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, event ->
            // Klavyedeki "Gönder" (Send, Done, Go) tuşuna basıldığında VEYA
            // Fiziksel klavyede Enter tuşuna basıldığında (event ile kontrol)
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {

                // Yükleme devam ediyorsa işlem yapma
                if (viewModel.isLoading.value == true) {
                    return@OnEditorActionListener true // Event'i tükettik
                }

                val idea = binding.promptEditText.text.toString().trim()
                if (idea.isNotEmpty()) {
                    // API isteğini başlat
                    viewModel.generateStory(idea)
                    // Klavyeyi gizle
                    hideKeyboard()
                } else {
                    Toast.makeText(requireContext(), "Lütfen bir masal fikri girin!", Toast.LENGTH_SHORT).show()
                }
                return@OnEditorActionListener true // Event'i tükettik
            }
            return@OnEditorActionListener false // Diğer action'lar için event'i tüketmedik
        })
    }


    private fun observeViewModel() {
        // Yükleme durumunu gözlemle
        viewModel.isLoading.observe(viewLifecycleOwner, Observer { isLoading ->
            binding.loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Buton olmadığı için butonun isEnabled kontrolünü kaldırıyoruz
            binding.promptEditText.isEnabled = !isLoading
        })

        // Başarılı sonucu gözlemle
        viewModel.storyResult.observe(viewLifecycleOwner, Observer { story ->
            if (story != null) {
                binding.masalText.text = story
                currentGeneratedStory = story // Kaydetme işlemi için güncel metni sakla
                // Kaydet butonunu görünür yap (isteğe bağlı)
                binding.btnSaveStory.visibility = View.VISIBLE
            }
        })

        // Hata durumunu gözlemle
        viewModel.error.observe(viewLifecycleOwner, Observer { error ->
            if (error != null) {
                Toast.makeText(requireContext(), "Hata: $error", Toast.LENGTH_LONG).show()
                binding.masalText.text = ""
                currentGeneratedStory = "" // Hata durumunda metni temizle
                binding.btnSaveStory.visibility = View.GONE // Hata durumunda kaydet butonunu gizle
            }
        })
    }

    // YENİ: Klavyeyi Gizleme Fonksiyonu
    private fun hideKeyboard() {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.promptEditText.windowToken, 0)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun saveStoryToFirestore() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "Please log in to save.", Toast.LENGTH_SHORT).show()
            return
        }
        // Eğer güncel üretilmiş hikaye boşsa kaydetme
        if (currentGeneratedStory.isBlank()){
            Toast.makeText(requireContext(), "No story generated to save.", Toast.LENGTH_SHORT).show()
            return
        }

        // Başlığı prompt'tan veya metinden alabiliriz (örnek: ilk birkaç kelime)
        val title = binding.promptEditText.text.toString().trim().take(30) // İlk 30 karakteri al
        if (title.isBlank()){
            Toast.makeText(requireContext(), "Cannot determine title.", Toast.LENGTH_SHORT).show()
            return // Başlık yoksa kaydetme
        }


        // Story data class'ınızın olduğunu varsayıyoruz
        val story = Story(
            title = title, // Başlığı prompt'tan aldık (veya başka bir yöntem belirleyebilirsiniz)
            content = currentGeneratedStory, // API'den gelen gerçek metin
            audioBase64 = "" // Şimdilik boş
        )

        // Kaydetme işlemi sırasında butonu tekrar devre dışı bırakabiliriz
        binding.btnSaveStory.isEnabled = false

        firestore.collection("users")
            .document(userId)
            .collection("stories")
            .add(story)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Fairytale saved successfully!", Toast.LENGTH_SHORT).show()
                binding.btnSaveStory.isEnabled = true // Başarılı olunca butonu tekrar aktif et
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error saving story: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnSaveStory.isEnabled = true // Hata durumunda da butonu tekrar aktif et
            }
    }
}