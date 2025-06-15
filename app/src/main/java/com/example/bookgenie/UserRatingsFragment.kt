package com.example.bookgenie

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bookgenie.databinding.FragmentUserRatingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot // Import DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject // For UserRating
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserRatingsFragment : Fragment() {

    private var _binding: FragmentUserRatingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentUser: FirebaseUser? = null

    private lateinit var userRatedBooksAdapter: UserRatedBooksAdapter
    private val ratedBooksDisplayList = mutableListOf<RatedBookDisplay>()

    private val fragmentJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + fragmentJob)

    // --- Helper functions for robust parsing ---
    private fun safeGetString(value: Any?): String {
        return when (value) {
            is String -> value
            is Number -> value.toString() // Handles Double, Long, Int converting to String
            else -> ""
        }
    }

    private fun convertToInt(value: Any?, default: Int = 0): Int {
        return when (value) {
            is Number -> value.toInt() // Handles Long, Double, etc. to Int
            is String -> value.toIntOrNull() ?: default
            else -> default
        }
    }

    private fun convertToDouble(value: Any?, default: Double = 0.0): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseBookFromDocument(document: DocumentSnapshot): Books? {
        try {
            val title = safeGetString(document.get("title")) // Düzeltme: document.getString yerine document.get
            val bookIdValue = document.get("book_id")
            Log.d("ParseBook", "Document ${document.id} - book_id raw value: $bookIdValue, type: ${bookIdValue?.javaClass?.simpleName}")

            val bookId = when (bookIdValue) {
                is Number -> bookIdValue.toInt()
                else -> {
                    Log.w("ParseBook", "Document ${document.id} - book_id is not a Number or is null. Value: $bookIdValue")
                    0
                }
            }

            if (title.isBlank() || bookId == 0) {
                Log.w("ParseBook", "Skipping document ${document.id}: Missing title ('$title') or valid book_id ($bookId).")
                return null
            }

            val firestoreTitleLowercase = safeGetString(document.get("title_lowercase")) // Düzeltme

            return Books(
                authors = convertToInt(document.get("authors")),
                author_name = safeGetString(document.get("author_name")), // Düzeltme
                average_rating = document.getDouble("average_rating") ?: 0.0,
                book_id = bookId,
                description = safeGetString(document.get("description")), // Düzeltme
                format = safeGetString(document.get("format")), // DÜZELTME: getString yerine get kullanıldı
                genres = (document.get("genres") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                imageUrl = safeGetString(document.get("image_url")), // DÜZELTME: getString yerine get kullanıldı
                is_ebook = document.getBoolean("is_ebook") ?: false,
                isbn = safeGetString(document.get("isbn")), // Düzeltme
                isbn13 = safeGetString(document.get("isbn13")), // Düzeltme
                kindle_asin = safeGetString(document.get("kindle_asin")), // DÜZELTME: getString yerine get kullanıldı
                language_code = safeGetString(document.get("language_code")), // DÜZELTME: getString yerine get kullanıldı
                num_pages = convertToInt(document.get("num_pages")),
                publication_day = (document.get("publication_day") as? List<*>)?.mapNotNull { it?.toString() } ?:
                (safeGetString(document.get("publication_day")).takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()), // Düzeltme
                publication_month = convertToInt(document.get("publication_month")),
                publication_year = convertToInt(document.get("publication_year")),
                publisher = safeGetString(document.get("publisher")), // Düzeltme
                title = title,
                rating_count = convertToInt(document.get("rating_count")),
                title_lowercase = if (firestoreTitleLowercase.isNotBlank()) firestoreTitleLowercase else title.lowercase()
            )
        } catch (e: Exception) {
            Log.e("ParseBook", "Error parsing book document ${document.id} to Book object: ${e.message}", e)
            return null
        }
    }
    // --- End of Helper functions ---

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserRatingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        currentUser = auth.currentUser

        setupRecyclerView()
        loadUserRatings()
    }

    private fun setupRecyclerView() {
        if (!isAdded) return
        userRatedBooksAdapter = UserRatedBooksAdapter(requireContext(), ratedBooksDisplayList)
        binding.rvUserRatedBooks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userRatedBooksAdapter
        }
    }

    private fun loadUserRatings() {
        currentUser?.uid?.let { userId ->
            binding.progressBarUserRatings.visibility = View.VISIBLE
            binding.tvNoRatingsMessage.visibility = View.GONE
            ratedBooksDisplayList.clear()

            uiScope.launch {
                try {
                    val userRatingsQuery = db.collection("user_ratings")
                        .whereEqualTo("userId", userId)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .get()
                        .await()

                    val userRatingDocs = userRatingsQuery.documents
                    if (!isAdded || _binding == null || !isActive) return@launch
                    binding.tvTotalRatingsCount.text = getString(R.string.total_ratings_count_placeholder, userRatingDocs.size)

                    if (userRatingDocs.isEmpty()) {
                        if (!isAdded || _binding == null || !isActive) return@launch
                        binding.tvNoRatingsMessage.text = getString(R.string.no_books_rated_yet)
                        binding.tvNoRatingsMessage.visibility = View.VISIBLE
                        binding.progressBarUserRatings.visibility = View.GONE
                        userRatedBooksAdapter.notifyDataSetChanged()
                        return@launch
                    }

                    val ratedBookEntries = userRatingDocs.mapNotNull { doc ->
                        doc.toObject<UserRating>()
                    }

                    val bookIdsToFetch: List<Int> = ratedBookEntries.mapNotNull { it.bookId?.toInt() }.distinct()
                    Log.d("UserRatingsFragment", "Book IDs to fetch from books_data (as Int): $bookIdsToFetch")


                    if (bookIdsToFetch.isNotEmpty()) {
                        val fetchedBooksMap = mutableMapOf<Int, Books>()
                        bookIdsToFetch.chunked(30).forEach { chunkOfIntIds ->
                            if (chunkOfIntIds.isNotEmpty()) {
                                Log.d("UserRatingsFragment", "Fetching book details for IDs (Int): $chunkOfIntIds")
                                val booksQuery = db.collection("books_data")
                                    .whereIn("book_id", chunkOfIntIds)
                                    .get()
                                    .await()
                                if (!isActive) return@launch
                                Log.d("UserRatingsFragment", "Found ${booksQuery.size()} books for chunk: $chunkOfIntIds")
                                booksQuery.documents.forEach { bookDoc ->
                                    val book = parseBookFromDocument(bookDoc)
                                    if (book != null) {
                                        fetchedBooksMap[book.book_id] = book
                                        Log.d("UserRatingsFragment", "Fetched and parsed book: ${book.title} (ID: ${book.book_id})")
                                    } else {
                                        Log.w("UserRatingsFragment", "Failed to parse book from document: ${bookDoc.id}. Check 'Skipping document' logs from ParseBook.")
                                    }
                                }
                            }
                        }
                        Log.d("UserRatingsFragment", "Total books fetched and mapped: ${fetchedBooksMap.size}")

                        ratedBookEntries.forEach { userRatingEntry ->
                            userRatingEntry.bookId?.toInt()?.let { bookIdInt ->
                                val bookDetail = fetchedBooksMap[bookIdInt]
                                if (bookDetail != null && userRatingEntry.rating != null) {
                                    ratedBooksDisplayList.add(
                                        RatedBookDisplay(
                                            book = bookDetail,
                                            userRating = userRatingEntry.rating.toFloat()
                                        )
                                    )
                                } else {
                                    Log.w("UserRatingsFragment", "Book detail not found in map or rating is null for rated bookId (Int): $bookIdInt. Book found in map: ${bookDetail != null}, Rating: ${userRatingEntry.rating}")
                                }
                            }
                        }
                    }
                    Log.d("UserRatingsFragment", "Final ratedBooksDisplayList size: ${ratedBooksDisplayList.size}")

                    if (!isAdded || _binding == null || !isActive) return@launch
                    if (ratedBooksDisplayList.isEmpty() && userRatingDocs.isNotEmpty()) {
                        binding.tvNoRatingsMessage.text = getString(R.string.could_not_load_rated_book_details)
                        binding.tvNoRatingsMessage.visibility = View.VISIBLE
                    } else if (ratedBooksDisplayList.isNotEmpty()){
                        binding.tvNoRatingsMessage.visibility = View.GONE
                    }
                    userRatedBooksAdapter.updateList(ratedBooksDisplayList)

                } catch (e: Exception) {
                    Log.e("UserRatingsFragment", "Error loading user ratings: ${e.message}", e)
                    if(isActive && _binding != null && isAdded) {
                        binding.tvNoRatingsMessage.text = getString(R.string.error_loading_ratings)
                        binding.tvNoRatingsMessage.visibility = View.VISIBLE
                    }
                } finally {
                    if(isActive && _binding != null && isAdded) {
                        binding.progressBarUserRatings.visibility = View.GONE
                    }
                }
            }
        } ?: run {
            if(_binding != null && isAdded) {
                binding.progressBarUserRatings.visibility = View.GONE
                binding.tvNoRatingsMessage.text = getString(R.string.please_sign_in_to_see_ratings)
                binding.tvNoRatingsMessage.visibility = View.VISIBLE
                binding.tvTotalRatingsCount.text = getString(R.string.total_ratings_count_placeholder, 0)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fragmentJob.cancel()
        _binding = null
    }
}
