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
        _isLoading.value = true
        _error.value = null
        _storyResult.value = null

        val systemInstruction = """
        You are a creative storytelling assistant. Write a complete, self-contained fairytale 
    based on the following idea. Make sure the story has a clear beginning, middle, and end. 
    Finish the story explicitly with the phrase: "The end."
        
    """.trimIndent()

        // Kullanıcının prompt'una "Tell a fairytale" ekle (başta yoksa)
        val userPrompt = if (idea.lowercase().startsWith("tell a fairytale")) {
            idea
        } else {
            "Tell a fairytale about: $idea"
        }

        // API'ye gönderilecek birleşik prompt
        val finalPrompt = systemInstruction + userPrompt

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val requestBody = StoryApiRequest(
                        idea = finalPrompt,
                        max_new_tokens = maxTokens,
                        temperature = temp,
                        top_p = topP
                    )
                    apiService.generateStory(requestBody)
                }

                if (response.isSuccessful) {
                    val storyResponse = response.body()
                    _storyResult.postValue(storyResponse?.story)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    _error.postValue("API Hatası: ${response.code()} - $errorBody")
                }
            } catch (e: Exception) {
                _error.postValue("Bağlantı Hatası: ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

}
