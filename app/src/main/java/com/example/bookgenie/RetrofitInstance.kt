package com.example.bookgenie.api

import StoryApiService
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val BASE_URL_AWS = "https://bookgenie.space/"
    private const val BASE_URL_HF = "https://betoyoglu-bookgenie-text-generation.hf.space/"

    // Header interceptor (ngrok uyarısını atlamak için)
    private val headerInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("ngrok-skip-browser-warning", "true")
            .build()
        chain.proceed(request)
    }

    // Client ayarları
    private val client = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .connectTimeout(600, TimeUnit.SECONDS) // bağlantı süresi
        .readTimeout(600, TimeUnit.SECONDS)    // okuma süresi
        .writeTimeout(600, TimeUnit.SECONDS)   // yazma süresi
        .build()

    // Retrofit nesnesi
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_AWS)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: RecommendationApi by lazy {
        retrofit.create(RecommendationApi::class.java)
    }

    //HF
    // OkHttp İstemcisi (Opsiyonel: Timeout ve Logging için)
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor()
        // DEBUG build'lerde logları görmek için BODY seviyesini ayarlayın
        // RELEASE build'lerde NONE yapın
        logging.setLevel(HttpLoggingInterceptor.Level.BODY) // VEYA .NONE

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS) // Bağlantı timeout
            .readTimeout(180, TimeUnit.SECONDS)    // Okuma timeout (model üretimi uzun sürebilir)
            .writeTimeout(60, TimeUnit.SECONDS)   // Yazma timeout
            .build()
    }

    // Retrofit Instance (Lazy Initialization)
    val instance: StoryApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL_HF)
            // OkHttp istemcisini ekle (opsiyonel)
            .client(okHttpClient)
            // JSON dönüştürücü olarak Gson'ı kullan
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        // StoryApiService interface'ini implemente et
        retrofit.create(StoryApiService::class.java)
    }
}
