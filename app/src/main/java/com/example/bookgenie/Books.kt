package com.example.bookgenie

import android.os.Parcelable
import com.google.firebase.firestore.PropertyName // Import PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Books(
    // Assuming 'authors' in Firestore is an Int. If it's a list or complex object, adjust accordingly.
    val authors: Int = 0,

    @get:PropertyName("author_name") @set:PropertyName("author_name")
    var author_name: String = "",

    @get:PropertyName("average_rating") @set:PropertyName("average_rating")
    var average_rating: Double = 0.0,

    @get:PropertyName("book_id") @set:PropertyName("book_id")
    var book_id: Int = 0, // If book_id in Firestore is Long, consider changing this to Long for consistency

    val description: String = "",
    val format: String = "",
    val genres: List<String> = emptyList(),

    @get:PropertyName("image_url") @set:PropertyName("image_url") // Handles "image_url" warning
    var imageUrl: String = "",

    @get:PropertyName("is_ebook") @set:PropertyName("is_ebook")
    var is_ebook: Boolean = false,

    // For isbn and isbn13, manual parsing (safeGetString) will handle type mismatches.
    // If they are consistently strings in Firestore, no @PropertyName is strictly needed if names match.
    var isbn: String = "",
    var isbn13: String = "", // This will be populated by safeGetString in manual parsers

    @get:PropertyName("kindle_asin") @set:PropertyName("kindle_asin")
    var kindle_asin: String = "",

    @get:PropertyName("language_code") @set:PropertyName("language_code")
    var language_code: String = "",

    @get:PropertyName("num_pages") @set:PropertyName("num_pages")
    var num_pages: Int = 0,

    @get:PropertyName("publication_day") @set:PropertyName("publication_day")
    var publication_day: List<String> = emptyList(), // Assuming it's a list of strings

    @get:PropertyName("publication_month") @set:PropertyName("publication_month")
    var publication_month: Int = 0,

    @get:PropertyName("publication_year") @set:PropertyName("publication_year")
    var publication_year: Int = 0,

    val publisher: String = "",
    val title: String = "",

    @get:PropertyName("rating_count") @set:PropertyName("rating_count")
    var rating_count: Int = 0,

    // If 'title_lowercase' is a field in Firestore and you want to map it directly:
    @get:PropertyName("title_lowercase") @set:PropertyName("title_lowercase")
    var title_lowercase: String = "" // Will be populated from Firestore if field exists
    // Defaulting to empty; manual parser can compute if not present.
    // The original default `title.lowercase()` is removed as we expect it from Firestore or compute in parser.

    // Fields mentioned in warnings but not in your class: 'similar_books', 'series'
    // If you need them, add them here with @PropertyName if names differ. Example:
    // @get:PropertyName("similar_books") @set:PropertyName("similar_books")
    // var similar_books: List<String> = emptyList(), // Adjust type as needed
) : Parcelable {
    constructor() : this(
        authors = 0,
        author_name = "",
        average_rating = 0.0,
        book_id = 0,
        description = "",
        format = "",
        genres = emptyList(),
        imageUrl = "",
        is_ebook = false,
        isbn = "",
        isbn13 = "",
        kindle_asin = "",
        language_code = "",
        num_pages = 0,
        publication_day = emptyList(),
        publication_month = 0,
        publication_year = 0,
        publisher = "",
        title = "",
        rating_count = 0,
        title_lowercase = "" // Default to empty for the no-arg constructor
        // similar_books = emptyList() // If added
    )
}
