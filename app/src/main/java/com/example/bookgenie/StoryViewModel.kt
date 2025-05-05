// Örnek: StoryViewModel.kt (veya benzeri bir sınıf)
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookgenie.api.RetrofitInstance
import com.example.bookgenie.model.StoryApiRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StoryViewModel : ViewModel() {

    // UI'ın observe edeceği LiveData'lar
    private val _storyResult = MutableLiveData<String?>()
    val storyResult: LiveData<String?> = _storyResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // API Servisi
    private val apiService = RetrofitInstance.instance

    fun generateStory(idea: String, maxTokens: Int = 250, temp: Float = 0.8f, topP: Float = 0.95f) {
        _isLoading.value = true // Yükleniyor durumunu başlat
        _error.value = null     // Önceki hatayı temizle
        _storyResult.value = null // Önceki sonucu temizle

        // Coroutine başlat (ViewModelScope otomatik olarak ViewModel yok olduğunda iptal eder)
        viewModelScope.launch {
            try {
                // Network isteğini IO dispatcher üzerinde yap (arka plan thread)
                val response = withContext(Dispatchers.IO) {
                    val requestBody = StoryApiRequest(
                        idea = idea,
                        max_new_tokens = maxTokens,
                        temperature = temp,
                        top_p = topP
                    )
                    // API çağrısını yap
                    apiService.generateStory(requestBody)
                }

                // Cevabı kontrol et
                if (response.isSuccessful) {
                    val storyResponse = response.body()
                    // Cevap gövdesi ve içindeki story null değilse LiveData'yı güncelle
                    _storyResult.postValue(storyResponse?.story) // Arka plandan LiveData güncellemek için postValue
                } else {
                    // HTTP hata kodu alındıysa
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    _error.postValue("API Hatası: ${response.code()} - ${errorBody}")
                }

            } catch (e: Exception) {
                // Network hatası veya başka bir exception oluştuysa
                _error.postValue("Bağlantı Hatası: ${e.message}")
                e.printStackTrace() // Logcat'te hatayı görmek için
            } finally {
                // İşlem bitince yükleniyor durumunu kapat
                _isLoading.postValue(false)
            }
        }
    }
}