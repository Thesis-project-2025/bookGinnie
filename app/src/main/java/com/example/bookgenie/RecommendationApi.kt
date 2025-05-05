package com.example.bookgenie.api

import com.example.bookgenie.model.RecommendationResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface RecommendationApi {
    @GET("recommend")
    suspend fun getRecommendations(
        @Query("user_id") userId: String,
        @Query("top_n") topN: Int = 10
    ): RecommendationResponse

    @GET("recommend-by-genre")
    suspend fun getRecommendationsByGenre(
        @Query("user_id") userId: String,
        @Query("top_n") topN: Int = 10
    ): RecommendationResponse

    @GET("similar-books")
    suspend fun getSimilarBooks(
        @Query("book_id") bookId: Int,
        @Query("top_n") topN: Int = 5,
        @Query("latent_weight") wLatent: Double = 0.6,
        @Query("genre_weight") wGenre: Double = 0.3,
        @Query("author_weight") wAuthor: Double = 0.05,
        @Query("rating_weight") wRating: Double = 0.05
    ): RecommendationResponse
}
