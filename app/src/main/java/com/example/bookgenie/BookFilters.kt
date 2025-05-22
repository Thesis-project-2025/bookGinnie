package com.example.bookgenie

// Seçilen filtreleri tutmak için data class

data class BookFilters(
    val selectedGenres: List<String> = emptyList(),
    val minRating: Double? = null,
    val sortBy: SortOption = SortOption.RELEVANCE,
    val author: String? = null, // Ekstra filtre: Yazar
    val publicationYear: Int? = null // Ekstra filtre: Yayın Yılı
)

// Sıralama seçenekleri için enum
enum class SortOption(val displayName: String) {
    RELEVANCE("İlgililik (Başlık)"),
    RATING_DESC("Puana Göre (Azalan)"),
    PUBLICATION_YEAR_DESC("Yayın Yılına Göre (Yeni)")
    // İhtiyaç duyulursa daha fazla sıralama seçeneği eklenebilir
}
