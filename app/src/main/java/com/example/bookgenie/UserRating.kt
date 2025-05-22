package com.example.bookgenie // veya model paketiniz

import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize
import android.os.Parcelable

// Firestore'daki user_ratings koleksiyonu için data class
// bookId'nin Firestore'da Long olarak saklandığını varsayıyoruz.
data class UserRating(
    val userId: String? = null,
    val bookId: Long? = null, // Firestore'dan Long olarak okunacak
    val rating: Double? = null,
    val timestamp: Timestamp? = null
) {
    constructor() : this(null, null, null, null) // Firestore toObject() için boş constructor
}

// UserRatingsFragment'taki adaptör için kullanılacak data class
@Parcelize
data class RatedBookDisplay(
    val book: Books, // Parcelable Books nesnesi
    val userRating: Float // Kullanıcının verdiği oy (Float olarak RatingBar için)
) : Parcelable
