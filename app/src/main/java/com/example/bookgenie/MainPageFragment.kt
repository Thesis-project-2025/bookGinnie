package com.example.bookgenie

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.bookgenie.api.RetrofitInstance // API Instance importu
import com.example.bookgenie.databinding.FragmentMainPageBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth // Auth importu
import com.google.firebase.auth.ktx.auth // Auth importu
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions // Merge için
import com.google.firebase.firestore.ktx.firestore // Firestore importu
import com.google.firebase.firestore.ktx.toObject // Cache nesnesi için
import com.google.firebase.ktx.Firebase // Firebase importu
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

// user_recommendations ve önbellek içindeki recommendation map'leri için ortak kullanılabilir
data class RecommendationEntry(
    val book_id: Long? = null, // Firestore'daki sayıları Long olarak okumak daha güvenli
    val score: Double? = null
) {
    constructor() : this(null, null) // Firestore toObject için boş constructor
}

// user_recommendations koleksiyonundan okumak için
data class UserRecommendations(
    val recommendations: List<RecommendationEntry>? = null, // Artık Map listesi değil, Entry listesi
    val rated_books_count: Long? = null,
    val timestamp: Timestamp? = null,
    // Örnekte görünen ekstra alanları da ekleyelim (isteğe bağlı ama uyarıları önler)
    val user_id: String? = null,
    val type: String? = null
) {
    constructor() : this(null, null, null, null, null)
}

// similar_books_cache koleksiyonundan okumak için
data class SimilarBooksResult(
    val recommendations: List<RecommendationEntry>? = null, // Artık Map listesi değil, Entry listesi
    // Örnekte görünen timestamp ve diğer alanlar (weights, book_id) buraya eklenebilir
    // Eğer client'ta kullanılmayacaksa eklenmese de olur, toObject görmezden gelir.
    val timestamp: Timestamp? = null // Örnekte vardı, ekleyelim
) {
    constructor() : this(null, null)
}

class MainPageFragment : Fragment() {
    private lateinit var binding: FragmentMainPageBinding
    private lateinit var auth: FirebaseAuth // Firebase Auth nesnesi
    private lateinit var firestore: FirebaseFirestore // Firestore nesnesi (lateinit ile)

    // Coroutine Scope yönetimi için
    private val fragmentJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + fragmentJob)

    private val booksPerPage = 5 // Sayfalama için limit

    // --- Search Functionality ---
    private lateinit var searchAdapter: BookAdapter
    private val searchBookList = ArrayList<Books>()
    private var lastVisibleSearchDocument: DocumentSnapshot? = null
    private var isSearchLoading = false
    private var isSearchLastPage = false
    private var searchQuery: String? = null
    private var isInSearchMode = false
    private var searchJob: Job? = null

    // --- Category Adapters and Data ---

    // Kullanılabilir türler listesi (Burayı kendi türlerinle güncelle!)
    private val availableGenres = listOf(
        "Fantasy", "Science Fiction", "Mystery", "Thriller", "Romance",
        "Historical Fiction", "Horror", "Young Adult", "Contemporary", "Adventure"
        // ... daha fazla tür ekle
    )
    private lateinit var selectedRandomGenres: List<String>

    // 1. Top Rated (by rating_count)
    private lateinit var topRatedByCountAdapter: BookCategoryAdapter
    private val topRatedByCountBooks = ArrayList<Books>()
    private var lastVisibleTopRatedByCount: DocumentSnapshot? = null
    private var isTopRatedByCountLoading = false

    // 2. High-Rated Fiction
    private lateinit var highRatedFictionAdapter: BookCategoryAdapter
    private val highRatedFictionBooks = ArrayList<Books>()
    private var lastVisibleHighRatedFiction: DocumentSnapshot? = null
    private var isHighRatedFictionLoading = false

    // 3. High-Rated Nonfiction
    private lateinit var highRatedNonFictionAdapter: BookCategoryAdapter
    private val highRatedNonFictionBooks = ArrayList<Books>()
    private var lastVisibleHighRatedNonFiction: DocumentSnapshot? = null
    private var isHighRatedNonFictionLoading = false

    // 4. Random Genre 1
    private lateinit var randomGenre1Adapter: BookCategoryAdapter
    private val randomGenre1Books = ArrayList<Books>()
    private var lastVisibleRandomGenre1: DocumentSnapshot? = null
    private var isRandomGenre1Loading = false

    // 5. Random Genre 2
    private lateinit var randomGenre2Adapter: BookCategoryAdapter
    private val randomGenre2Books = ArrayList<Books>()
    private var lastVisibleRandomGenre2: DocumentSnapshot? = null
    private var isRandomGenre2Loading = false

    // 6. Random Genre 3
    private lateinit var randomGenre3Adapter: BookCategoryAdapter
    private val randomGenre3Books = ArrayList<Books>()
    private var lastVisibleRandomGenre3: DocumentSnapshot? = null
    private var isRandomGenre3Loading = false

    // --- For You Recommendations (API + Firestore + Cache) ---
    private lateinit var forYouAdapter: BookCategoryAdapter
    private val forYouBooks = ArrayList<Books>()
    private var isForYouLoading = false

    // --- Similar Books Sections (API + Firestore + Cache) ---
    private val MAX_SIMILAR_SECTIONS = 2 // Gösterilecek maksimum benzer kitap bölümü
    private lateinit var similarTo1Adapter: BookCategoryAdapter
    private val similarTo1Books = ArrayList<Books>()
    private var isSimilarTo1Loading = false
    private var originalBookTitle1: String? = null // Sadece başlık

    private lateinit var similarTo2Adapter: BookCategoryAdapter
    private val similarTo2Books = ArrayList<Books>()
    private var isSimilarTo2Loading = false
    private var originalBookTitle2: String? = null

    private var currentSearchOperationJob: Job? = null // Aktif arama işlemini tutar
    private var isLoading = false

    // --- Lifecycle ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        firestore = Firebase.firestore
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMainPageBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectRandomGenres()
        setupSearchView()
        setupBackPressHandler()
        setupRecyclerViews()

        loadTopRatedByCountBooks()
        loadHighRatedFictionBooks()
        loadHighRatedNonFictionBooks()
        loadRandomGenreSections()
        loadForYouRecommendations()
        loadSimilarBooksSections()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fragmentJob.cancel()
        currentSearchOperationJob?.cancel() // Güncellendi
    }

    // --- Setup Functions ---
    private fun selectRandomGenres() {
        selectedRandomGenres = availableGenres.shuffled().take(3)
        Log.d("Genres", "Selected random genres: $selectedRandomGenres")
    }

    // SEARCHFRAGMENT'TAN ALINAN VE UYARLANAN setupSearchView
    private fun setupSearchView() {
        val searchView = binding.searchMainpage // MainPage binding'i kullanılıyor
        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        try {
            searchEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.beige))
            searchEditText.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.beige))
        } catch (e: Exception) {
            Log.w("SetupSearch", "Color resources not found for search. ${e.message}")
        }

        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isInSearchMode) {
                switchToSearchMode()
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                currentSearchOperationJob?.cancel() // Mevcut aramayı iptal et
                if (newText.isBlank()) {
                    if (isInSearchMode) {
                        clearSearchResults()
                        Log.d("MainPageSearch", "Cleared search results on text change.")
                    }
                } else {
                    if (!isInSearchMode) {
                        switchToSearchMode()
                    }
                    currentSearchOperationJob = uiScope.launch {
                        delay(350) // Debounce
                        if (isActive) { // Coroutine hala aktif mi kontrol et
                            clearSearchResults()
                            searchQuery = newText.trim() // Class seviyesindeki searchQuery'yi güncelle
                            Log.d("MainPageSearch", "Debounced search starting for: $searchQuery")
                            searchBooks(searchQuery!!) // searchBooks'u çağır
                        }
                    }
                }
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                currentSearchOperationJob?.cancel() // Mevcut aramayı iptal et
                searchView.clearFocus()
                if (query.isNotBlank()) {
                    if (!isInSearchMode) {
                        switchToSearchMode()
                    }
                    clearSearchResults()
                    searchQuery = query.trim() // Class seviyesindeki searchQuery'yi güncelle
                    Log.d("MainPageSearch", "Submit search starting for: $searchQuery")
                    currentSearchOperationJob = uiScope.launch {
                        searchBooks(searchQuery!!) // searchBooks'u çağır
                    }
                }
                return true
            }
        })
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isInSearchMode) {
                        switchToCategoriesMode()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun setupRecyclerViews() {
        // Search RV
        binding.bookRV.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        searchAdapter = BookAdapter(requireContext(), searchBookList, "main") // fragmentType="main" MainPage için
        binding.bookRV.adapter = searchAdapter
        setupSearchScrollListener() // ARAMA İÇİN KAYDIRMA DİNLEYİCİSİ

        // Category RVs
        setupCategoryRecyclerView(binding.rvTopRatedByCountBooks)
        setupCategoryRecyclerView(binding.rvHighRatedFictionBooks)
        setupCategoryRecyclerView(binding.rvHighRatedNonFictionBooks)
        setupCategoryRecyclerView(binding.rvRandomGenre1)
        setupCategoryRecyclerView(binding.rvRandomGenre2)
        setupCategoryRecyclerView(binding.rvRandomGenre3)
        setupCategoryRecyclerView(binding.rvForYou)
        setupCategoryRecyclerView(binding.rvSimilarTo1)
        setupCategoryRecyclerView(binding.rvSimilarTo2)

        // Initialize Adapters
        topRatedByCountAdapter = BookCategoryAdapter(requireContext(), topRatedByCountBooks) { loadTopRatedByCountBooks() }
        highRatedFictionAdapter = BookCategoryAdapter(requireContext(), highRatedFictionBooks) { loadHighRatedFictionBooks() }
        highRatedNonFictionAdapter = BookCategoryAdapter(requireContext(), highRatedNonFictionBooks) { loadHighRatedNonFictionBooks() }
        randomGenre1Adapter = BookCategoryAdapter(requireContext(), randomGenre1Books) { if (selectedRandomGenres.isNotEmpty()) loadRandomGenreBooks(0) }
        randomGenre2Adapter = BookCategoryAdapter(requireContext(), randomGenre2Books) { if (selectedRandomGenres.size > 1) loadRandomGenreBooks(1) }
        randomGenre3Adapter = BookCategoryAdapter(requireContext(), randomGenre3Books) { if (selectedRandomGenres.size > 2) loadRandomGenreBooks(2) }
        forYouAdapter = BookCategoryAdapter(requireContext(), forYouBooks) {}
        similarTo1Adapter = BookCategoryAdapter(requireContext(), similarTo1Books) {}
        similarTo2Adapter = BookCategoryAdapter(requireContext(), similarTo2Books) {}

        // Set Adapters
        binding.rvTopRatedByCountBooks.adapter = topRatedByCountAdapter
        binding.rvHighRatedFictionBooks.adapter = highRatedFictionAdapter
        binding.rvHighRatedNonFictionBooks.adapter = highRatedNonFictionAdapter
        binding.rvRandomGenre1.adapter = randomGenre1Adapter
        binding.rvRandomGenre2.adapter = randomGenre2Adapter
        binding.rvRandomGenre3.adapter = randomGenre3Adapter
        binding.rvForYou.adapter = forYouAdapter
        binding.rvSimilarTo1.adapter = similarTo1Adapter
        binding.rvSimilarTo2.adapter = similarTo2Adapter

        updateRandomGenreViews()

        hideSection(binding.tvForYou, binding.rvForYou)
        hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
        hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
    }

    private fun updateRandomGenreViews(){
        binding.tvRandomGenre1.visibility = if (selectedRandomGenres.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvRandomGenre1.visibility = if (selectedRandomGenres.isNotEmpty()) View.VISIBLE else View.GONE
        if (selectedRandomGenres.isNotEmpty()) binding.tvRandomGenre1.text = "Best ${selectedRandomGenres[0]} Books"

        binding.tvRandomGenre2.visibility = if (selectedRandomGenres.size > 1) View.VISIBLE else View.GONE
        binding.rvRandomGenre2.visibility = if (selectedRandomGenres.size > 1) View.VISIBLE else View.GONE
        if (selectedRandomGenres.size > 1) binding.tvRandomGenre2.text = "Best ${selectedRandomGenres[1]} Books"

        binding.tvRandomGenre3.visibility = if (selectedRandomGenres.size > 2) View.VISIBLE else View.GONE
        binding.rvRandomGenre3.visibility = if (selectedRandomGenres.size > 2) View.VISIBLE else View.GONE
        if (selectedRandomGenres.size > 2) binding.tvRandomGenre3.text = "Best ${selectedRandomGenres[2]} Books"
    }

    private fun setupCategoryRecyclerView(recyclerView: RecyclerView) {
        context?.let { ctx ->
            recyclerView.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    // SEARCHFRAGMENT'TAN ALINAN setupSearchScrollListener
    private fun setupSearchScrollListener() {
        binding.bookRV.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0 || !isInSearchMode) return // Sadece aşağı kaydırırken ve arama modunda

                val layoutManager = recyclerView.layoutManager as StaggeredGridLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItemPositions = layoutManager.findLastVisibleItemPositions(null)
                val lastVisibleItem = lastVisibleItemPositions.maxOrNull() ?: 0

                // Son elemana 'booksPerPage' kadar mesafe kalınca veya daha azsa yükle
                if (!isSearchLoading && !isSearchLastPage && totalItemCount > 0 &&
                    lastVisibleItem >= totalItemCount - booksPerPage
                ) {
                    searchQuery?.let { currentQuery -> // Global searchQuery'yi kullan
                        Log.d("MainPageSearchScroll", "Loading more search results for: $currentQuery")
                        // searchBooks zaten CoroutineScope içinde değil, bu yüzden burada launch etmeye gerek yok
                        // direkt çağırabiliriz, kendi içinde asenkron listener'ları var.
                        // Ancak, UI güncellemeleri ana thread'de olmalı, searchBooks bunu zaten yapıyor.
                        // Eğer searchBooks'u suspend yaptıysak uiScope.launch gerekir.
                        // Mevcut searchBooks (SearchFragment'tan gelen) suspend değil.
                        searchBooks(currentQuery)
                    }
                }
            }
        })
    }


    // --- Utility to Show/Hide Sections ---
    private fun showSection(textView: TextView, recyclerView: RecyclerView) {
        textView.visibility = View.VISIBLE
        recyclerView.visibility = View.VISIBLE
    }
    private fun hideSection(textView: TextView, recyclerView: RecyclerView) {
        textView.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    // --- Data Loading: Firestore Categories ---
    private fun loadTopRatedByCountBooks() {
        if (isTopRatedByCountLoading || !::topRatedByCountAdapter.isInitialized) return
        isTopRatedByCountLoading = true
        // ... (Geri kalan implementasyon önceki tam koddaki gibi)
        var query = firestore.collection("books_data")
            .orderBy("rating_count", Query.Direction.DESCENDING)
            .limit(booksPerPage.toLong())
        lastVisibleTopRatedByCount?.let { query = query.startAfter(it) }
        query.get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                lastVisibleTopRatedByCount = documents.documents.last()
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                if (newBooks.isNotEmpty()) {
                    val startPosition = topRatedByCountBooks.size
                    topRatedByCountBooks.addAll(newBooks)
                    topRatedByCountAdapter.notifyItemRangeInserted(startPosition, newBooks.size)
                }
            }
            isTopRatedByCountLoading = false
        }.addOnFailureListener { e -> Log.e("Firestore", "Error loadTopRated: ${e.message}"); isTopRatedByCountLoading = false }
    }

    private fun loadHighRatedFictionBooks() {
        if (isHighRatedFictionLoading || !::highRatedFictionAdapter.isInitialized) return
        isHighRatedFictionLoading = true
        // ... (Geri kalan implementasyon önceki tam koddaki gibi)
        var query = firestore.collection("books_data")
            .whereGreaterThan("average_rating", 4.0)
            .whereArrayContains("genres", "Fiction")
            .orderBy("average_rating", Query.Direction.DESCENDING)
            .limit(booksPerPage.toLong())
        lastVisibleHighRatedFiction?.let { query = query.startAfter(it) }
        query.get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                lastVisibleHighRatedFiction = documents.documents.last()
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                if (newBooks.isNotEmpty()) {
                    val startPosition = highRatedFictionBooks.size
                    highRatedFictionBooks.addAll(newBooks)
                    highRatedFictionAdapter.notifyItemRangeInserted(startPosition, newBooks.size)
                }
            }
            isHighRatedFictionLoading = false
        }.addOnFailureListener { e -> Log.e("Firestore", "Error loadFiction: ${e.message}"); isHighRatedFictionLoading = false }
    }

    private fun loadHighRatedNonFictionBooks() {
        if (isHighRatedNonFictionLoading || !::highRatedNonFictionAdapter.isInitialized) return
        isHighRatedNonFictionLoading = true
        // ... (Geri kalan implementasyon önceki tam koddaki gibi)
        var query = firestore.collection("books_data")
            .whereGreaterThan("average_rating", 4.0)
            .whereArrayContains("genres", "Nonfiction")
            .orderBy("average_rating", Query.Direction.DESCENDING)
            .limit(booksPerPage.toLong())
        lastVisibleHighRatedNonFiction?.let { query = query.startAfter(it) }
        query.get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                lastVisibleHighRatedNonFiction = documents.documents.last()
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                if (newBooks.isNotEmpty()) {
                    val startPosition = highRatedNonFictionBooks.size
                    highRatedNonFictionBooks.addAll(newBooks)
                    highRatedNonFictionAdapter.notifyItemRangeInserted(startPosition, newBooks.size)
                }
            }
            isHighRatedNonFictionLoading = false
        }.addOnFailureListener { e -> Log.e("Firestore", "Error loadNonFiction: ${e.message}"); isHighRatedNonFictionLoading = false }
    }

    private fun loadRandomGenreSections() {
        if (selectedRandomGenres.isNotEmpty()) loadRandomGenreBooks(0)
        if (selectedRandomGenres.size > 1) loadRandomGenreBooks(1)
        if (selectedRandomGenres.size > 2) loadRandomGenreBooks(2)
    }

    private fun loadRandomGenreBooks(genreIndex: Int) {
        if (genreIndex < 0 || genreIndex >= selectedRandomGenres.size) return
        val genre = selectedRandomGenres[genreIndex]
        val adapter: BookCategoryAdapter?
        val bookList: ArrayList<Books>
        var lastVisibleDoc: DocumentSnapshot?
        val isLoadingFlag = getLoadingFlag(genreIndex)

        if (isLoadingFlag) return

        when (genreIndex) {
            0 -> { adapter = if(::randomGenre1Adapter.isInitialized) randomGenre1Adapter else null; bookList = randomGenre1Books; lastVisibleDoc = lastVisibleRandomGenre1 }
            1 -> { adapter = if(::randomGenre2Adapter.isInitialized) randomGenre2Adapter else null; bookList = randomGenre2Books; lastVisibleDoc = lastVisibleRandomGenre2 }
            2 -> { adapter = if(::randomGenre3Adapter.isInitialized) randomGenre3Adapter else null; bookList = randomGenre3Books; lastVisibleDoc = lastVisibleRandomGenre3 }
            else -> return
        }
        if (adapter == null) { setLoadingFlag(genreIndex, false); return }

        setLoadingFlag(genreIndex, true)
        // ... (Geri kalan implementasyon önceki tam koddaki gibi)
        var query = firestore.collection("books_data")
            .whereArrayContains("genres", genre)
            .orderBy("average_rating", Query.Direction.DESCENDING)
            .limit(booksPerPage.toLong())
        lastVisibleDoc?.let { query = query.startAfter(it) }
        query.get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                setLastVisible(genreIndex, documents.documents.last())
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                if (newBooks.isNotEmpty()) {
                    val startPosition = bookList.size
                    bookList.addAll(newBooks)
                    adapter.notifyItemRangeInserted(startPosition, newBooks.size)
                }
            }
            setLoadingFlag(genreIndex, false)
        }.addOnFailureListener { e -> Log.e("Firestore", "Error loadRandomGenre $genre: ${e.message}"); setLoadingFlag(genreIndex, false) }
    }
    // --- Helper functions for Random Genre State ---
    // Belirtilen index için yükleme bayrağını döndürür
    private fun getLoadingFlag(index: Int): Boolean {
        return when (index) {
            0 -> isRandomGenre1Loading
            1 -> isRandomGenre2Loading
            2 -> isRandomGenre3Loading
            else -> false // Geçersiz index ise yüklenmiyor
        }
    }

    // Belirtilen index için yükleme bayrağını ayarlar
    private fun setLoadingFlag(index: Int, isLoading: Boolean) {
        when (index) {
            0 -> isRandomGenre1Loading = isLoading
            1 -> isRandomGenre2Loading = isLoading
            2 -> isRandomGenre3Loading = isLoading
            else -> Log.w("SetLoadingFlag", "Attempted to set loading flag for invalid index: $index")
        }
    }

    // Belirtilen index için son görünen belgeyi ayarlar
    private fun setLastVisible(index: Int, document: DocumentSnapshot?) {
        when (index) {
            0 -> lastVisibleRandomGenre1 = document
            1 -> lastVisibleRandomGenre2 = document
            2 -> lastVisibleRandomGenre3 = document
            else -> Log.w("SetLastVisible", "Attempted to set last visible document for invalid index: $index")
        }
    }
    // Belirtilen index için kitap listesini döndürür
    private fun getBookListForGenreIndex(index: Int): ArrayList<Books> {
        return when (index) {
            0 -> randomGenre1Books
            1 -> randomGenre2Books
            2 -> randomGenre3Books
            else -> ArrayList() // Hatalı index için boş liste
        }
    }


    // --- "For You" Recommendations (Cache First Logic - 10 Rating Barajı Eklendi) ---
    private fun loadForYouRecommendations() {
        if (!::forYouAdapter.isInitialized) {
            Log.w("ForYou", "Adapter not initialized for 'For You'.")
            return
        }
        if (isForYouLoading) return
        val userId = auth.currentUser?.uid ?: run {
            Log.w("ForYou", "User not logged in for 'For You'.")
            hideSection(binding.tvForYou, binding.rvForYou)
            return
        }

        isForYouLoading = true
        Log.d("ForYou", "Starting 'For You' recommendations for user $userId...")
        // Önceki verileri temizle ve UI'ı başlangıç durumuna getir
        forYouBooks.clear()
        if (::forYouAdapter.isInitialized) { // Adapter'ın başlatıldığından emin ol
            forYouAdapter.notifyDataSetChanged()
        }
        hideSection(binding.tvForYou, binding.rvForYou)

        uiScope.launch {
            var bookIdsToLoad: List<Long>? = null
            var triggerApi = false

            try {
                // --- YENİ ADIM: Kullanıcının Toplam Rating Sayısını Kontrol Et ---
                val currentTotalRatingCount = getRatingCount(userId) // Artık tek parametre
                Log.d("ForYouLogic", "User $userId current total rating count: $currentTotalRatingCount")

                if (currentTotalRatingCount < 10) {
                    Log.d("ForYouLogic", "User has less than 10 ratings ($currentTotalRatingCount). 'For You' recommendations skipped.")
                    isForYouLoading = false
                    // hideSection zaten çağrıldı, burada tekrar gerek yok.
                    return@launch // Coroutine'dan çık, başka işlem yapma
                }
                // --- YENİ ADIM SONU ---

                // Kullanıcının en az 10 rating'i var, önbellek/API mantığına devam et
                Log.d("ForYouLogic", "User has >= 10 ratings. Proceeding with cache/API logic.")
                val cacheRef = firestore.collection("user_recommendations").document(userId)
                val cacheSnapshot = cacheRef.get().await()
                val cachedData = try { cacheSnapshot.toObject<UserRecommendations>() } catch (e: Exception) {
                    Log.e("ForYouCache", "Error converting 'user_recommendations' snapshot: ${e.message}")
                    null
                }

                if (cachedData == null || cachedData.recommendations.isNullOrEmpty()) {
                    Log.d("ForYouLogic", "Cache miss for user with >=10 ratings. Triggering API.")
                    triggerApi = true
                } else {
                    // Önbellek var, geçerliliğini kontrol et
                    val isStale: Boolean
                    val cacheTimestampObject: Timestamp? = cachedData.timestamp
                    if (cacheTimestampObject == null) {
                        Log.d("ForYouLogic", "Cache timestamp is null. Considering stale.")
                        isStale = true
                    } else {
                        val cacheTimeMillis = cacheTimestampObject.toDate().time
                        val currentTimeMillis = Timestamp.now().toDate().time
                        val oneDayInMillis = TimeUnit.DAYS.toMillis(1)
                        isStale = (currentTimeMillis - cacheTimeMillis) > oneDayInMillis
                    }

                    if (isStale) {
                        Log.d("ForYouLogic", "Cache is stale for user with >=10 ratings. Triggering API.")
                        triggerApi = true
                    } else {
                        // Önbellek taze, `rated_books_count` farkını kontrol et
                        // currentTotalRatingCount zaten yukarıda alındı.
                        val cachedRatedBooksCount = cachedData.rated_books_count ?: 0
                        if (currentTotalRatingCount >= cachedRatedBooksCount + 5) {
                            Log.d("ForYouLogic", "Rating count discrepancy ($currentTotalRatingCount vs $cachedRatedBooksCount) for user with >=10 ratings. Triggering API.")
                            triggerApi = true
                        } else {
                            Log.d("ForYouLogic", "Cache is valid and up-to-date for user with >=10 ratings. Loading from cache.")
                            val recommendationsList = cachedData.recommendations
                            val extractedIds = mutableListOf<Long>()
                            recommendationsList?.forEach { entry -> entry.book_id?.let { extractedIds.add(it) } }
                            bookIdsToLoad = if (extractedIds.isNotEmpty()) extractedIds else null
                        }
                    }
                }

                if (triggerApi) {
                    Log.d("ForYouAPI", "Triggering /recommend-by-genre API for user $userId (has $currentTotalRatingCount ratings)...")
                    try {
                        withContext(Dispatchers.IO) { RetrofitInstance.api.getRecommendationsByGenre(userId) }
                        Log.d("ForYouAPI", "API triggered. Waiting briefly for Firestore update...")
                        delay(3000) // Backend'in Firestore'u güncellemesi için bekleme süresi

                        val updatedSnapshot = cacheRef.get().await()
                        val updatedCacheData = try { updatedSnapshot.toObject<UserRecommendations>() } catch (e: Exception) { null }

                        if (updatedCacheData?.recommendations != null && updatedCacheData.recommendations.isNotEmpty()) {
                            Log.d("ForYouLogic", "Loading recommendations from Firestore after API trigger.")
                            val recommendationsList = updatedCacheData.recommendations
                            val extractedIds = mutableListOf<Long>()
                            recommendationsList.forEach { entry -> entry.book_id?.let { extractedIds.add(it) } }
                            bookIdsToLoad = if (extractedIds.isNotEmpty()) extractedIds else null
                        } else {
                            Log.w("ForYouLogic", "Firestore cache still empty or invalid after API trigger and delay.")
                            bookIdsToLoad = null
                        }
                    } catch (e: Exception) {
                        Log.e("ForYouAPI", "Error triggering /recommend-by-genre API: ${e.message}", e)
                        bookIdsToLoad = null
                    }
                }

                if (!bookIdsToLoad.isNullOrEmpty()) {
                    fetchBookDetailsByIds(
                        bookIds = bookIdsToLoad.map { it.toInt() },
                        targetList = forYouBooks,
                        targetAdapter = forYouAdapter,
                        targetTextView = binding.tvForYou,
                        targetRecyclerView = binding.rvForYou,
                        sectionTitle = "For You", // Başlığı doğru ayarla
                        setLoadingFlag = { isForYouLoading = it }
                    )
                } else {
                    Log.w("ForYouLogic", "No valid book IDs found to load details for 'For You'.")
                    isForYouLoading = false
                    hideSection(binding.tvForYou, binding.rvForYou) // ID yoksa bölümü gizle
                }

            } catch (e: Exception) {
                Log.e("ForYou", "Error in loadForYouRecommendations flow: ${e.message}", e)
                isForYouLoading = false
                hideSection(binding.tvForYou, binding.rvForYou) // Hata durumunda gizle
            }
        }
    }

    // Helper to get current total rating count from Firestore using count() aggregation
    private suspend fun getRatingCount(userId: String): Long { // minimumExpected parametresi kaldırıldı
        return try {
            val countQuery = firestore.collection("user_ratings")
                .whereEqualTo("userId", userId) // Firestore'daki alan adının "userId" olduğundan emin ol!
                // .limit() KULLANILMIYOR, çünkü toplam sayıyı istiyoruz
                .count()
                .get(com.google.firebase.firestore.AggregateSource.SERVER)
                .await()
            Log.d("RatingCount", "Fetched rating count for user $userId: ${countQuery.count}")
            countQuery.count
        } catch (e: Exception) {
            Log.e("RatingCount", "Error getting rating count for user $userId: ${e.message}", e)
            -1L // Hata durumunda -1 döndür (kontrol için)
        }
    }


    // --- "Similar Books" Sections Loading (Cache First Logic - Loglamalı) ---
    private fun loadSimilarBooksSections() {
        val userId = auth.currentUser?.uid ?: return // Kullanıcı girişi yoksa çık

        // Adapterların hazır olduğundan emin ol
        if (!::similarTo1Adapter.isInitialized || !::similarTo2Adapter.isInitialized) {
            Log.w("SimilarBooks", "Adapters for similar books not ready yet.")
            return
        }

        uiScope.launch {
            try {
                // Step 1: Find 5-star rated books (book IDs only first)
                Log.d("SimilarBooks", "Finding 5-star ratings for user $userId")
                // Firestore sorgusu (Alan adlarının DB ile eşleştiğini varsayıyoruz)
                val fiveStarQuery = firestore.collection("user_ratings")
                    .whereEqualTo("userId", userId) // Doğru alan adı?
                    .whereEqualTo("rating", 5.0) // Veya 5L? DB'yi kontrol et
                    .limit(MAX_SIMILAR_SECTIONS.toLong()) // En fazla MAX_SIMILAR_SECTIONS kadar al
                    .get().await()

                Log.d("SimilarBooks", "5-star query completed. Found ${fiveStarQuery.size()} documents.")

                // ID'leri al (bookId alan adı ve Long türü varsayımıyla)
                val fiveStarBookIds = fiveStarQuery.documents.mapNotNull { doc ->
                    val bookId = doc.get("bookId") // DB'deki alan adı 'bookId' ise
                    if (bookId is Number) { bookId.toLong() } else { null }
                }.distinct()

                Log.d("SimilarBooks", "Mapped valid Book IDs (Long): $fiveStarBookIds")

                if (fiveStarBookIds.isEmpty()) {
                    Log.d("SimilarBooks", "No valid 5-star ratings with numeric bookId found after mapping.")
                    hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
                    hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
                    return@launch
                }

                // Step 2: Fetch details of these 5-star books (for titles)
                // fetchBookDetailsByIdsInternal Int listesi bekliyor
                val originalBooksDetails = fetchBookDetailsByIdsInternal(fiveStarBookIds.map { it.toInt() })
                Log.d("SimilarBooks", "Fetched details for ${originalBooksDetails.size} original 5-star books.")


                if (originalBooksDetails.isEmpty()){
                    Log.d("SimilarBooks", "Could not fetch details for 5-star rated books. IDs: $fiveStarBookIds")
                    hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
                    hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
                    return@launch
                }

                // Store titles
                originalBookTitle1 = if (originalBooksDetails.isNotEmpty()) originalBooksDetails[0].title else null
                originalBookTitle2 = if (originalBooksDetails.size > 1) originalBooksDetails[1].title else null
                Log.d("SimilarBooks", "Stored original titles. Title1: $originalBookTitle1, Title2: $originalBookTitle2")

                // Step 3: Load sections sequentially (Pass Int IDs)
                loadSimilarBooksForIndex(0, originalBooksDetails.map { it.book_id })

            } catch (e: Exception) {
                Log.e("SimilarBooks", "Error finding/fetching 5-star rated books: ${e.message}", e)
                hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
                hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
            }
        }
    }

    // Recursive/Sequential loader for each "Similar To" section (whereEqualTo ile Cache Arama - Loglamalı)
    private fun loadSimilarBooksForIndex(index: Int, originalBookIds: List<Int>) {
        if (index >= MAX_SIMILAR_SECTIONS || index >= originalBookIds.size) {
            Log.d("SimilarBooksSeq", "Finished loading all requested similar book sections.")
            return // Base case
        }

        val originalBookId = originalBookIds[index] // Int ID
        // originalBookIdStr artık cacheRef için kullanılmıyor ama loglama için kalabilir
        // val originalBookIdStr = originalBookId.toString()

        // Determine target UI and state
        val isLoading: Boolean
        val setLoadingFlag: (Boolean) -> Unit
        val targetList: ArrayList<Books>
        val targetAdapter: BookCategoryAdapter?
        val targetTextView: TextView
        val targetRecyclerView: RecyclerView
        val originalTitle: String?

        when (index) {
            0 -> {
                isLoading = isSimilarTo1Loading
                setLoadingFlag = { isSimilarTo1Loading = it }
                targetList = similarTo1Books
                targetAdapter = if(::similarTo1Adapter.isInitialized) similarTo1Adapter else null
                targetTextView = binding.tvSimilarTo1
                targetRecyclerView = binding.rvSimilarTo1
                originalTitle = originalBookTitle1
            }
            1 -> {
                isLoading = isSimilarTo2Loading
                setLoadingFlag = { isSimilarTo2Loading = it }
                targetList = similarTo2Books
                targetAdapter = if(::similarTo2Adapter.isInitialized) similarTo2Adapter else null
                targetTextView = binding.tvSimilarTo2
                targetRecyclerView = binding.rvSimilarTo2
                originalTitle = originalBookTitle2
            }
            else -> { loadSimilarBooksForIndex(index + 1, originalBookIds); return }
        }

        if (isLoading) {
            Log.d("SimilarBooksSeq", "Section $index is already loading. Triggering next.")
            loadSimilarBooksForIndex(index + 1, originalBookIds)
            return
        }
        if (targetAdapter == null) {
            Log.e("SimilarBooksSeq", "Adapter for section $index not initialized. Triggering next.")
            loadSimilarBooksForIndex(index + 1, originalBookIds)
            return
        }

        setLoadingFlag(true)
        val sectionLogPrefix = "SimilarBooksSeq[${index}, OrigID:${originalBookId}]" // Log prefix OrigID ile
        Log.d(sectionLogPrefix, "Loading section...")
        hideSection(targetTextView, targetRecyclerView)
        targetList.clear()
        targetAdapter.notifyDataSetChanged()

        uiScope.launch {
            var bookIdsToLoad: List<Long>? = null // Long IDs from cache
            var triggerApi = false
            val sectionDisplayTitle = "Similar to ${originalTitle ?: "Book ID $originalBookId"}"

            try {
                // --- DEĞİŞİKLİK: Cache Kontrolü whereEqualTo ile ---
                Log.d(sectionLogPrefix, "Querying cache where book_id == $originalBookId")
                val cacheQuery = firestore.collection("similar_books_cache")
                    .whereEqualTo("book_id", originalBookId.toLong()) // Long ile sorgula (Firestore'da Number ise)
                    .limit(1)
                    .get()
                    .await() // QuerySnapshot döndürür

                Log.d(sectionLogPrefix, "Cache query completed. Found ${cacheQuery.size()} documents.")

                var cachedData: SimilarBooksResult? = null
                var cacheSnapshot: DocumentSnapshot? = null
                if (!cacheQuery.isEmpty) {
                    cacheSnapshot = cacheQuery.documents[0] // İlk (ve tek) dokümanı al
                    Log.d(sectionLogPrefix, "Cache snapshot exists: true (Doc ID: ${cacheSnapshot.id})") // Dokümanın kendi ID'sini logla
                    cachedData = try { cacheSnapshot.toObject<SimilarBooksResult>() }
                    catch (e:Exception){ Log.e(sectionLogPrefix, "!!! Error converting cache snapshot: ${e.message}", e); null }
                } else {
                    Log.d(sectionLogPrefix, "Cache snapshot exists: false")
                }
                // --- DEĞİŞİKLİK SONU ---

                Log.d(sectionLogPrefix, "Parsed cachedData object: ${cachedData}")
                Log.d(sectionLogPrefix, "Parsed cachedData recommendations count: ${cachedData?.recommendations?.size}")

                // Cache Check (Güvenli null kontrolü ile)
                val cachedRecommendations = cachedData?.recommendations
                val isCacheValid = cachedRecommendations != null && cachedRecommendations.isNotEmpty()
                Log.d(sectionLogPrefix, "Is cache considered valid? $isCacheValid")


                if (isCacheValid) {
                    Log.d(sectionLogPrefix, "Cache hit and valid. Extracting IDs...")
                    val extractedIds = mutableListOf<Long>()
                    // cachedRecommendations null olamaz (isCacheValid true ise)
                    for (entry in cachedRecommendations!!) { // entry is RecommendationEntry
                        entry.book_id?.let { id -> extractedIds.add(id) }
                    }
                    bookIdsToLoad = if (extractedIds.isNotEmpty()) extractedIds else null
                    Log.d(sectionLogPrefix, "Extracted IDs from cache: $bookIdsToLoad")
                    triggerApi = false
                } else {
                    Log.d(sectionLogPrefix, "Cache miss or invalid. Triggering API.")
                    triggerApi = true
                }

                // Step 2: Trigger API if needed
                if (triggerApi) {
                    Log.d(sectionLogPrefix, "Triggering /similar-books API...")
                    try {
                        withContext(Dispatchers.IO) {
                            // API çağrısı hala parametreleri kullanabilir
                            RetrofitInstance.api.getSimilarBooks(
                                bookId = originalBookId,
                                topN = 10, // Sabitleri kullan
                                wLatent = 0.6,
                                wGenre = 0.25,
                                wAuthor = 0.1,
                                wRating = 0.05
                            )
                        }
                        Log.d(sectionLogPrefix, "API triggered. Waiting for Firestore update...")
                        delay(3000)

                        // --- DEĞİŞİKLİK: Cache Tekrar Okuma whereEqualTo ile ---
                        Log.d(sectionLogPrefix, "Re-querying cache where book_id == $originalBookId after API trigger")
                        val updatedQuery = firestore.collection("similar_books_cache")
                            .whereEqualTo("book_id", originalBookId.toLong()) // Long ile sorgula
                            .limit(1)
                            .get()
                            .await()

                        Log.d(sectionLogPrefix, "Updated cache query completed. Found ${updatedQuery.size()} documents.")

                        var updatedCacheData: SimilarBooksResult? = null
                        var updatedSnapshot: DocumentSnapshot? = null
                        if (!updatedQuery.isEmpty) {
                            updatedSnapshot = updatedQuery.documents[0]
                            Log.d(sectionLogPrefix, "Updated cache snapshot exists: true (Doc ID: ${updatedSnapshot.id})")
                            updatedCacheData = try { updatedSnapshot.toObject<SimilarBooksResult>() }
                            catch(e:Exception){ Log.e(sectionLogPrefix, "!!! Error converting UPDATED cache snapshot: ${e.message}", e); null }
                        } else {
                            Log.d(sectionLogPrefix, "Updated cache snapshot exists: false")
                        }
                        // --- DEĞİŞİKLİK SONU ---

                        Log.d(sectionLogPrefix, "Parsed updatedCacheData object: ${updatedCacheData}")
                        Log.d(sectionLogPrefix, "Parsed updatedCacheData recommendations count: ${updatedCacheData?.recommendations?.size}")


                        val updatedRecommendations = updatedCacheData?.recommendations // Güvenli kontrol
                        if (updatedRecommendations != null && updatedRecommendations.isNotEmpty()) {
                            Log.d(sectionLogPrefix, "Loading recommendations from Firestore after API trigger.")
                            val extractedIds = mutableListOf<Long>()
                            for (entry in updatedRecommendations) {
                                entry.book_id?.let { id -> extractedIds.add(id) }
                            }
                            bookIdsToLoad = if (extractedIds.isNotEmpty()) extractedIds else null
                            Log.d(sectionLogPrefix, "Extracted IDs after API trigger: $bookIdsToLoad")
                        } else {
                            Log.w(sectionLogPrefix, "Firestore cache still not populated or has no recommendations after API trigger.")
                            bookIdsToLoad = null
                        }
                    } catch (e: Exception) {
                        Log.e(sectionLogPrefix, "Error triggering /similar-books API or reading cache after: ${e.message}", e)
                        bookIdsToLoad = null
                    }
                }

                // Step 4: Load book details if IDs were found
                if (!bookIdsToLoad.isNullOrEmpty()) {
                    fetchBookDetailsByIds(
                        bookIds = bookIdsToLoad.map { it.toInt() }, // Long -> Int
                        targetList = targetList,
                        targetAdapter = targetAdapter,
                        targetTextView = targetTextView,
                        targetRecyclerView = targetRecyclerView,
                        sectionTitle = sectionDisplayTitle,
                        setLoadingFlag = setLoadingFlag
                    )
                } else {
                    Log.w(sectionLogPrefix, "No valid book IDs found to load details. Hiding section.")
                    setLoadingFlag(false)
                    hideSection(targetTextView, targetRecyclerView)
                }

            } catch (e: Exception) {
                Log.e(sectionLogPrefix, "Error in loadSimilarBooksForIndex flow: ${e.message}", e)
                setLoadingFlag(false)
                hideSection(targetTextView, targetRecyclerView)
            } finally {
                withContext(Dispatchers.Main) {
                    loadSimilarBooksForIndex(index + 1, originalBookIds)
                }
            }
        }
    }


    // --- Generic Book Detail Fetching (Loglamalı) ---
    private fun fetchBookDetailsByIds(
        bookIds: List<Int>,
        targetList: ArrayList<Books>,
        targetAdapter: BookCategoryAdapter,
        targetTextView: TextView,
        targetRecyclerView: RecyclerView,
        sectionTitle: String,
        setLoadingFlag: (Boolean) -> Unit
    ) {
        // *** YENİ LOG ***
        Log.d("FetchDetails", "Fetching for '$sectionTitle' with IDs: $bookIds")

        if (bookIds.isEmpty()) {
            Log.w("FetchDetails", "Book ID list empty for '$sectionTitle'. Hiding section.")
            hideSection(targetTextView, targetRecyclerView)
            setLoadingFlag(false)
            return
        }

        // Firestore whereIn sınırı
        val queryBookIds = if (bookIds.size > 30) bookIds.take(30) else bookIds

        firestore.collection("books_data")
            .whereIn("book_id", queryBookIds) // book_id'nin Number olduğunu varsayıyoruz
            .get()
            .addOnSuccessListener { documents ->
                // *** YENİ LOG ***
                Log.d("FetchDetails", "Firestore query success for '$sectionTitle'. Found ${documents.size()} raw documents.")
                val fetchedBooks = documents.mapNotNull { parseDocumentToBook(it) }
                // *** YENİ LOG ***
                Log.d("FetchDetails", "Parsed ${fetchedBooks.size} valid books for '$sectionTitle'.")


                targetList.clear()

                if (fetchedBooks.isNotEmpty()) {
                    targetList.addAll(fetchedBooks)
                    targetAdapter.notifyDataSetChanged()
                    targetTextView.text = sectionTitle
                    // *** YENİ LOG ***
                    Log.d("FetchDetails", ">>> Calling showSection for '$sectionTitle'")
                    showSection(targetTextView, targetRecyclerView)
                    // *** YENİ LOG ***
                    Log.d("FetchDetails", "<<< Called showSection for '$sectionTitle'")

                    Log.d("FetchDetails", "Updated adapter for '$sectionTitle' with ${targetList.size} books.")
                } else {
                    Log.d("FetchDetails", "No valid book details found for '$sectionTitle'. Hiding section.")
                    hideSection(targetTextView, targetRecyclerView)
                }
                setLoadingFlag(false) // Başarılı/Başarısız (detay bulunamadı) -> Yükleme bitti
            }
            .addOnFailureListener { exception ->
                Log.e("FetchDetails", "Error fetching details for '$sectionTitle': ${exception.message}", exception)
                hideSection(targetTextView, targetRecyclerView)
                setLoadingFlag(false) // Hata -> Yükleme bitti
            }
    }

    // Internal helper to get book details without UI updates (Loglamalı)
    private suspend fun fetchBookDetailsByIdsInternal(bookIds: List<Int>): List<Books> {
        if (bookIds.isEmpty()) {
            Log.d("FetchDetailsInternal", "Input bookId list is empty.")
            return emptyList()
        }
        return try {
            val queryBookIds = if (bookIds.size > 30) bookIds.take(30) else bookIds
            Log.d("FetchDetailsInternal", "Querying 'books_data' for book_ids: $queryBookIds")
            val documents = firestore.collection("books_data")
                .whereIn("book_id", queryBookIds) // book_id Number varsayımı
                .get().await()
            Log.d("FetchDetailsInternal", "Found ${documents.size()} documents.")
            val parsedBooks = documents.mapNotNull { parseDocumentToBook(it) }
            Log.d("FetchDetailsInternal", "Parsed ${parsedBooks.size} books.")
            parsedBooks
        } catch (e: Exception) {
            Log.e("FetchDetailsInternal", "Error fetching internal details: ${e.message}", e)
            emptyList()
        }
    }


    // --- Search Functions (SearchFragment'tan alınan ve MainPageFragment'a uyarlanan) ---
    private fun switchToSearchMode() {
        if (!isInSearchMode) {
            isInSearchMode = true
            binding.scrollViewCategories.visibility = View.GONE // Kategorileri gizle
            binding.bookRV.visibility = View.VISIBLE       // Arama sonuçlarını göster             // FAB'ı gizle
            Log.d("MainPageSearch", "Switched to search mode")
        }
    }

    private fun switchToCategoriesMode() {
        if (isInSearchMode) {
            isInSearchMode = false
            searchQuery = null // Aktif aramayı temizle
            binding.searchMainpage.setQuery("", false) // SearchView içini temizle
            binding.searchMainpage.clearFocus()       // Focus'u kaldır

            binding.scrollViewCategories.visibility = View.VISIBLE // Kategorileri göster
            binding.bookRV.visibility = View.GONE          // Arama sonuçlarını gizle
            clearSearchResults() // Mod değiştirince sonuçları temizle
            Log.d("MainPageSearch", "Switched to categories mode")
        }
    }

    // SearchFragment'tan alınan clearSearchResults
    private fun clearSearchResults() {
        if (searchBookList.isNotEmpty()) {
            val previousSize = searchBookList.size
            searchBookList.clear()
            if (::searchAdapter.isInitialized) {
                searchAdapter.notifyItemRangeRemoved(0, previousSize)
            }
        }
        lastVisibleSearchDocument = null
        isSearchLastPage = false
        Log.d("MainPageSearch", "Search results cleared.")
    }

    // MainPageFragment içinde
    private fun searchBooks(query: String) { // Artık suspend değil, Firebase listener'ları kullanıyor
        if (isSearchLoading) {
            Log.d("MainPageSearch", "Already loading search results, skipping for: $query")
            return
        }
        isSearchLoading = true
        // this.searchQuery zaten onQueryTextChange/Submit içinde ayarlandı, parametre 'query' kullanılmalı.

        // lastVisibleSearchDocument null ise (yani yeni bir arama veya ilk sayfa),
        // liste zaten clearSearchResults içinde temizleniyor.
        // Tekrar temizlemeye gerek yok, clearSearchResults'ün çağrıldığından emin olalım.
        // (Bu, setupSearchView içindeki onQueryTextChange ve onQueryTextSubmit'te yapılıyor.)

        binding.progressBar.visibility = View.VISIBLE

        val formattedQueryForFirestore = query.lowercase().trim() // Firestore sorgusu için
        Log.d("MainPageSearch", "Searching (Firestore query) for: $formattedQueryForFirestore, lastVisible: ${lastVisibleSearchDocument?.id}")

        var firestoreQuery: Query = firestore.collection("books_data")
            .orderBy("title_lowercase") // title_lowercase alanına göre sırala (index gerekli)
            .startAt(formattedQueryForFirestore)
            .endAt(formattedQueryForFirestore + "\uf8ff") // Prefix eşleşmesi
            .limit(booksPerPage.toLong()) // booksPerPage (örneğin 10)

        lastVisibleSearchDocument?.let {
            firestoreQuery = firestoreQuery.startAfter(it)
        }

        firestoreQuery.get()
            .addOnSuccessListener { documents ->
                Log.d("SearchFunc", "Search success, Document count: ${documents.size()}") // Log eklendi
                val newBooks = ArrayList<Books>()
                val formattedQueryForFilter = query.lowercase().trim() // Filtre için de aynı formatlanmış sorgu

                if (!documents.isEmpty) {
                    lastVisibleSearchDocument = documents.documents.last()

                    for (document in documents) {
                        val titleLowercase = document.getString("title_lowercase") ?: ""

                        // --- İSTEMCİ TARAFLI FİLTRELEME (Test için basitleştirilmiş/loglu) ---
                        // Orijinal katı filtre (yorumda):
                        // if (formattedQueryForFilter.split(" ").all { word -> titleLowercase.contains(word) }) {

                        // Geçici olarak daha basit bir filtre:
                        // Sadece arama teriminin başlıkta herhangi bir yerde geçip geçmediğini kontrol et
                        if (titleLowercase.contains(formattedQueryForFilter)) {
                            parseDocumentToBook(document)?.let { book -> // Sağlam parser kullanılıyor
                                newBooks.add(book)
                                Log.d("MainPageSearch", "FİLTREYİ GEÇTİ & PARSE EDİLDİ: ${book.title} (orijinal başlık: $titleLowercase)")
                            }
                        } else {
                            // Bu log, hangi kitapların neden filtrelendiğini gösterir
                            Log.w("MainPageSearch", "FİLTRELENDİ: '$titleLowercase' -- arama: '$formattedQueryForFilter'")
                        }
                        // --- FİLTRELEME TESTİ SONU ---
                    }

                    Log.d("SearchFunc", "Client-side filter complete. newBooks.size = ${newBooks.size}")
                    if (newBooks.isEmpty() && !documents.isEmpty) {
                        Log.w("SearchFunc", "Firestore returned ${documents.size()} docs, but client filter removed all of them for query: '$formattedQueryForFilter'. First doc title_lowercase from Firestore: ${documents.documents[0].getString("title_lowercase")}")
                    }

                    if (newBooks.isNotEmpty()) {
                        val currentSize = searchBookList.size
                        searchBookList.addAll(newBooks)
                        if (::searchAdapter.isInitialized) { // Adapter başlatıldı mı kontrolü
                            searchAdapter.notifyItemRangeInserted(currentSize, newBooks.size)
                        }
                        isSearchLastPage = documents.size() < booksPerPage
                    } else {
                        // newBooks boşsa ama Firestore'dan doküman geldiyse, yine de son sayfa olmayabilir
                        // Sadece Firestore'dan hiç doküman gelmediyse veya gelen < limit ise son sayfa olmalı
                        isSearchLastPage = documents.size() < booksPerPage || documents.isEmpty
                        if (documents.isEmpty) {
                            Log.d("MainPageSearch", "No results from Firestore for '$formattedQueryForFilter' after pagination/filtering.")
                        }
                    }
                } else {
                    Log.d("MainPageSearch", "No more results from Firestore for '$formattedQueryForFilter'")
                    isSearchLastPage = true
                }

                isSearchLoading = false
                binding.progressBar.visibility = View.GONE
                Log.d("SearchFunc", "ProgressBar hidden in success listener.") // Log eklendi
            }
            .addOnFailureListener { exception ->
                Log.e("MainPageSearch", "Error searching books: ${exception.message}", exception)
                isSearchLoading = false
                binding.progressBar.visibility = View.GONE
            }
    }

    // --- Helper Functions ---

    // String alanı güvenli alma
    private fun safeGetString(value: Any?): String {
        return when (value) {
            is String -> value
            is Number -> value.toString() // Sayıyı metne çevir
            else -> "" // Diğer türler veya null için boş metin
        }
    }

    // Int alanı güvenli alma
    private fun convertToInt(value: Any?): Int {
        return when (value) {
            is Number -> value.toInt() // Number ise Int'e çevir
            is String -> value.toIntOrNull() ?: 0 // String ise çevirmeyi dene, olmazsa 0
            else -> 0 // Diğer türler veya null için 0
        }
    }

    // Double alanı güvenli alma
    private fun convertToDouble(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble() // Number ise Double'a çevir
            is String -> value.toDoubleOrNull() ?: 0.0 // String ise çevirmeyi dene, olmazsa 0.0
            else -> 0.0 // Diğer türler veya null için 0.0
        }
    }

    // Belgeyi Books nesnesine çevirme
    @Suppress("UNCHECKED_CAST")
    private fun parseDocumentToBook(document: DocumentSnapshot): Books? {
        try {
            // Firestore'dan sayısal ve boolean alanları güvenli al
            val authors = convertToInt(document.get("authors"))
            val averageRating = convertToDouble(document.get("average_rating"))
            // book_id'nin Long veya Int olabileceğini varsayalım, Int'e çevirelim
            val bookId = convertToInt(document.get("book_id"))
            val isEbook = document.getBoolean("is_ebook") ?: false
            val numPages = convertToInt(document.get("num_pages"))
            val publicationMonth = convertToInt(document.get("publication_month"))
            val publicationYear = convertToInt(document.get("publication_year"))
            val ratingCount = convertToInt(document.get("rating_count"))

            // Firestore'dan String alanları güvenli al
            val authorName = safeGetString(document.get("author_name"))
            val description = safeGetString(document.get("description"))
            val format = safeGetString(document.get("format"))
            // Firestore'daki alan adının 'image_url' olduğundan emin ol
            val imageUrl = safeGetString(document.get("image_url"))
            val isbn = safeGetString(document.get("isbn"))
            val isbn13 = safeGetString(document.get("isbn13"))
            val kindleAsin = safeGetString(document.get("kindle_asin"))
            val languageCode = safeGetString(document.get("language_code"))
            val publisher = safeGetString(document.get("publisher"))
            val title = safeGetString(document.get("title"))

            // Firestore'dan Liste alanlarını güvenli al
            // 'genres' alanının List<String> olduğunu varsayıyoruz
            val genres = (document.get("genres") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            // 'publication_day' alanının List<String> veya List<Number> olabileceğini varsayalım
            val publicationDayRaw = document.get("publication_day")
            val publicationDay = when (publicationDayRaw) {
                is List<*> -> publicationDayRaw.mapNotNull { it?.toString() } // Elemanları String'e çevir
                else -> emptyList() // Liste değilse veya null ise boş liste
            }


            // Temel doğrulama (Başlık ve ID olmalı)
            if (title.isBlank() || bookId == 0) {
                Log.w("ParseBook", "Skipping document ${document.id}: Missing title or valid book_id (ID: $bookId).")
                return null // Eksik bilgi varsa null dön
            }

            // Books nesnesini oluştur ve döndür
            return Books(
                authors = authors,
                author_name = authorName,
                average_rating = averageRating,
                book_id = bookId, // Artık Int
                description = description,
                format = format,
                genres = genres,
                imageUrl = imageUrl,
                is_ebook = isEbook,
                isbn = isbn,
                isbn13 = isbn13,
                kindle_asin = kindleAsin,
                language_code = languageCode,
                num_pages = numPages,
                publication_day = publicationDay, // Artık List<String>
                publication_month = publicationMonth,
                publication_year = publicationYear,
                publisher = publisher,
                title = title,
                rating_count = ratingCount
            )
        } catch (e: Exception) {
            // Herhangi bir hata olursa logla ve null dön
            Log.e("ParseBook", "Critical error parsing document ${document.id}: ${e.message}", e)
            return null
        }
    }

} // End of MainPageFragment class