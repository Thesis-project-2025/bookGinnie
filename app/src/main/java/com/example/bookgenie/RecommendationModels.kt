package com.example.bookgenie.model

import java.security.Timestamp

data class Recommendation(
    val book_id: Int,
    val score: Double
)

data class RecommendationResponse(
    val user_id: String,
    val recommendations: List<Recommendation>
)

// Örnek: network/request/StoryApiRequest.kt
data class StoryApiRequest(
    val idea: String,
    val max_new_tokens: Int,
    val temperature: Float,
    val top_p: Float
)

// Örnek: network/response/StoryApiResponse.kt
data class StoryApiResponse(
    // API'den dönen JSON'daki "story" anahtarıyla eşleşmeli
    // @SerializedName("story") // Eğer JSON key ile değişken adı farklıysa Gson için kullanılır
    val story: String? // Nullable yapmak, anahtar yoksa veya null dönerse çökmeyi engeller
)

