// Örnek: network/StoryApiService.kt
import com.example.bookgenie.model.StoryApiRequest
import com.example.bookgenie.model.StoryApiResponse
import retrofit2.Response // Detaylı cevap almak için
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface StoryApiService {

    // İstek başlıklarını tanımla
    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
        // Eğer token gerekiyorsa buraya veya Interceptor ile eklenebilir
        // "Authorization: Bearer YOUR_TOKEN"
    )
    // POST isteği ve endpoint yolu (/generate)
    @POST("generate")
    suspend fun generateStory(
        // İstek gövdesini (@Body) StoryApiRequest objesi olarak gönder
        @Body request: StoryApiRequest
        // Cevabı Response<StoryApiResponse> olarak al (suspend fonksiyon Coroutine içinde çağrılacak)
    ): Response<StoryApiResponse>
}