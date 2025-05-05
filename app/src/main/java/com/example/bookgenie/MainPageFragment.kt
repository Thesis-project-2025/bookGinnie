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


    // --- Lifecycle ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        firestore = Firebase.firestore
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMainPageBinding.inflate(inflater, container, false)

        binding.fabButton.setOnClickListener {
            Navigation.findNavController(it).navigate(R.id.mainToFairy)
        }

        // Bounce animasyonunu başlat
        val bounceAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.bounce)
        binding.fabButton.startAnimation(bounceAnimation)

        // Pulse animasyonunu başlat
        val pulseAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
        binding.fabButton.startAnimation(pulseAnimation)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectRandomGenres()
        setupSearchView()
        setupBackPressHandler()
        setupRecyclerViews()
        setupBottomNavigation()

        // Initial Data Loading
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
        searchJob?.cancel()
    }

    // --- Setup Functions ---

    private fun selectRandomGenres() {
        // Mevcut tür listesinden rastgele 3 tane seçer
        selectedRandomGenres = availableGenres.shuffled().take(3)
        Log.d("Genres", "Selected random genres: $selectedRandomGenres")
    }

    private fun setupSearchView() {
        val searchView = binding.searchMainpage
        // androidx.appcompat.R.id.search_src_text ID'sinin çalıştığından emin ol
        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)

        // Renkleri kendi projenin renkleriyle değiştir (colors.xml)
        try {
            searchEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.beige)) // colors.xml'de beige rengi olmalı
            searchEditText.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.beige))
        } catch (e: Exception) {
            Log.w("SetupSearch", "Color resources not found? Using default colors. ${e.message}")
        }

        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isInSearchMode) {
                switchToSearchMode()
                Log.d("SearchMode", "Switched to search mode due to focus")
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                searchJob?.cancel() // Önceki arama işini iptal et
                if (newText.isBlank()) {
                    // Metin boşsa ve arama modundaysak sonuçları temizle
                    if (isInSearchMode) {
                        clearSearchResults()
                        Log.d("SearchMode", "Search text cleared, results cleared.")
                    }
                } else {
                    // Arama modunda değilsek geçiş yap
                    if (!isInSearchMode) {
                        switchToSearchMode()
                    }
                    // Debounce ile arama yap (kullanıcı yazmayı bitirince)
                    searchJob = uiScope.launch {
                        delay(350) // 350ms bekle
                        clearSearchResults() // Yeni aramadan önce temizle
                        searchQuery = newText.trim()
                        searchBooks(searchQuery!!) // Arama fonksiyonunu çağır
                        Log.d("SearchMode", "Searching (debounced): $searchQuery")
                    }
                }
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                searchJob?.cancel() // Bekleyen arama işini iptal et
                searchView.clearFocus() // Klavyeyi gizle
                if (query.isNotBlank()) {
                    if (!isInSearchMode) {
                        switchToSearchMode()
                    }
                    clearSearchResults() // Yeni aramadan önce temizle
                    searchQuery = query.trim()
                    uiScope.launch { // Submit işlemini de scope içinde yap
                        searchBooks(searchQuery!!)
                    }
                    Log.d("SearchMode", "Search submitted: $searchQuery")
                }
                return true
            }
        })
    }


    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) { // enabled = true
                override fun handleOnBackPressed() {
                    if (isInSearchMode) {
                        // Arama modundaysa, kategori moduna dön
                        switchToCategoriesMode()
                    } else {
                        // Arama modunda değilse, default davranışı etkinleştir ve geri git
                        isEnabled = false // Callback'i devre dışı bırak
                        requireActivity().onBackPressedDispatcher.onBackPressed() // Sistemin geri tuşu işlemini çağır
                    }
                }
            }
        )
    }

    private fun setupRecyclerViews() {
        // --- Search Results RecyclerView (Vertical Staggered Grid) ---
        binding.bookRV.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        searchAdapter = BookAdapter(requireContext(), searchBookList, "main") // fragmentType="main"
        binding.bookRV.adapter = searchAdapter
        setupSearchScrollListener() // Arama için kaydırma dinleyicisini ayarla

        // --- Category RecyclerViews (Horizontal Linear) ---
        setupCategoryRecyclerView(binding.rvTopRatedByCountBooks)
        setupCategoryRecyclerView(binding.rvHighRatedFictionBooks)
        setupCategoryRecyclerView(binding.rvHighRatedNonFictionBooks)
        setupCategoryRecyclerView(binding.rvRandomGenre1)
        setupCategoryRecyclerView(binding.rvRandomGenre2)
        setupCategoryRecyclerView(binding.rvRandomGenre3)
        setupCategoryRecyclerView(binding.rvForYou)
        setupCategoryRecyclerView(binding.rvSimilarTo1)
        setupCategoryRecyclerView(binding.rvSimilarTo2)

        // --- Initialize Adapters ---
        // Firestore Kategorileri
        topRatedByCountAdapter = BookCategoryAdapter(requireContext(), topRatedByCountBooks) { loadTopRatedByCountBooks() }
        highRatedFictionAdapter = BookCategoryAdapter(requireContext(), highRatedFictionBooks) { loadHighRatedFictionBooks() }
        highRatedNonFictionAdapter = BookCategoryAdapter(requireContext(), highRatedNonFictionBooks) { loadHighRatedNonFictionBooks() }
        // Random Türler
        randomGenre1Adapter = BookCategoryAdapter(requireContext(), randomGenre1Books) { if (selectedRandomGenres.isNotEmpty()) loadRandomGenreBooks(0) }
        randomGenre2Adapter = BookCategoryAdapter(requireContext(), randomGenre2Books) { if (selectedRandomGenres.size > 1) loadRandomGenreBooks(1) }
        randomGenre3Adapter = BookCategoryAdapter(requireContext(), randomGenre3Books) { if (selectedRandomGenres.size > 2) loadRandomGenreBooks(2) }
        // API Bazlı
        forYouAdapter = BookCategoryAdapter(requireContext(), forYouBooks) { /* onLoadMore boş */ }
        similarTo1Adapter = BookCategoryAdapter(requireContext(), similarTo1Books) { /* onLoadMore boş */ }
        similarTo2Adapter = BookCategoryAdapter(requireContext(), similarTo2Books) { /* onLoadMore boş */ }

        // --- Set Adapters ---
        binding.rvTopRatedByCountBooks.adapter = topRatedByCountAdapter
        binding.rvHighRatedFictionBooks.adapter = highRatedFictionAdapter
        binding.rvHighRatedNonFictionBooks.adapter = highRatedNonFictionAdapter
        binding.rvRandomGenre1.adapter = randomGenre1Adapter
        binding.rvRandomGenre2.adapter = randomGenre2Adapter
        binding.rvRandomGenre3.adapter = randomGenre3Adapter
        binding.rvForYou.adapter = forYouAdapter
        binding.rvSimilarTo1.adapter = similarTo1Adapter
        binding.rvSimilarTo2.adapter = similarTo2Adapter

        // --- Update Titles/Visibility for Random Genres ---
        updateRandomGenreViews() // Helper fonksiyon çağrısı

        // --- Initially Hide API-driven sections ---
        hideSection(binding.tvForYou, binding.rvForYou)
        hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
        hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
        // Diğer similar bölümler eklenirse onlar da gizlenmeli
    }

    // Helper to update Random Genre Views after selection
    private fun updateRandomGenreViews(){
        // Adapterların initialize edildiğini kontrol etmek iyi bir pratik olabilir
        if (!::randomGenre1Adapter.isInitialized || !::randomGenre2Adapter.isInitialized || !::randomGenre3Adapter.isInitialized) return

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


    // Helper to setup layout manager for horizontal category lists
    private fun setupCategoryRecyclerView(recyclerView: RecyclerView) {
        // Context null kontrolü eklemek daha güvenli olabilir (fragment detach olmuşsa)
        context?.let { ctx ->
            recyclerView.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        }
    }


    private fun setupSearchScrollListener() {
        binding.bookRV.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // Sadece aşağı kaydırırken ve arama modundayken çalış
                if (dy > 0 && isInSearchMode) {
                    val layoutManager = recyclerView.layoutManager as StaggeredGridLayoutManager
                    val lastVisibleItemPositions = layoutManager.findLastVisibleItemPositions(null)
                    // Pozisyon dizisindeki en büyük değeri bul
                    val lastVisibleItem = lastVisibleItemPositions.maxOrNull() ?: 0
                    val totalItemCount = layoutManager.itemCount

                    // Listenin sonuna yaklaşıldığında ve yükleme yapılmıyorsa
                    // (Threshold: Son elemana `booksPerPage` kadar mesafe kalınca)
                    if (!isSearchLoading && !isSearchLastPage && lastVisibleItem >= totalItemCount - booksPerPage ) {
                        Log.d("SearchScroll", "Reached end of search results (approx.), loading more...")
                        searchQuery?.let { query ->
                            // Coroutine içinde başlat
                            uiScope.launch { searchBooks(query) }
                        }
                    }
                }
            }
        })
    }


    private fun setupBottomNavigation() {
        val bottomNavigationView: BottomNavigationView = binding.bottomNavView
        // Aynı item'a tekrar tıklanmasını engelle
        bottomNavigationView.setOnNavigationItemReselectedListener { /* Do nothing */ }

        bottomNavigationView.setOnNavigationItemSelectedListener { menuItem ->
            // Mevcut fragment'ın ID'sini al
            val currentDestinationId = findNavController().currentDestination?.id

            // Kendi Navigasyon ID'lerini ve Action ID'lerini buraya gir:
            val mainPageId = R.id.mainPageFragment // Ana sayfa fragment ID'si
            val settingsId = R.id.settingsFragment // Settings fragment ID'si (varsa)
            val userInfoId = R.id.userInfoFragment // Profile fragment ID'si (varsa)
            val searchPageId = R.id.searchFragment // Search fragment ID'si (varsa)

            val mainToSettingsAction = R.id.mainPageToSettings // Ana sayfadan Settings'e geçiş Action ID'si
            val mainToUserInfoAction = R.id.mainPageToUserInfo // Ana sayfadan Profile'a geçiş Action ID'si
            val mainToSearchAction = R.id.action_mainPageFragment_to_searchFragment // Ana sayfadan Search'e geçiş Action ID'si


            when (menuItem.itemId) {
                R.id.idMainPage -> {
                    // Ana sayfa ikonuna tıklandı
                    if (isInSearchMode){
                        switchToCategoriesMode() // Arama modundaysa kategoriye dön
                    }
                    true // Başka bir işlem yapma
                }
                R.id.idSettings -> {
                    // Ayarlar ikonuna tıklandı
                    if (currentDestinationId != settingsId) { // Zaten ayarlar sayfasında değilse
                        try { findNavController().navigate(mainToSettingsAction) }
                        catch (e: Exception) { Log.e("NavigationError", "Settings navigation failed: ${e.message}") }
                    }
                    true
                }
                R.id.idProfile -> {
                    // Profil ikonuna tıklandı
                    if (currentDestinationId != userInfoId) { // Zaten profil sayfasında değilse
                        try { findNavController().navigate(mainToUserInfoAction) }
                        catch (e: Exception) { Log.e("NavigationError", "Profile navigation failed: ${e.message}") }
                    }
                    true
                }
                R.id.idSearch -> {
                    // Arama ikonuna tıklandı (Ayrı bir Arama Fragment'ına gidiyorsa)
                    if (currentDestinationId != searchPageId) { // Zaten arama sayfasında değilse
                        try { findNavController().navigate(mainToSearchAction) }
                        catch (e: Exception) { Log.e("NavigationError", "Search navigation failed: ${e.message}") }
                    }
                    true
                }
                else -> false // Tanımsız item ID'si
            }
        }
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


    // --- Category Data Loading Functions (Firestore) ---

    private fun loadTopRatedByCountBooks() {
        if (isTopRatedByCountLoading || !::topRatedByCountAdapter.isInitialized) return
        isTopRatedByCountLoading = true
        Log.d("FirestoreLoad", "Loading Top Rated (by count)... LastVisible: ${lastVisibleTopRatedByCount?.id}")

        var query = firestore.collection("books_data")
            .orderBy("rating_count", Query.Direction.DESCENDING)
            .limit(booksPerPage.toLong())

        lastVisibleTopRatedByCount?.let { query = query.startAfter(it) }

        query.get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    lastVisibleTopRatedByCount = documents.documents.last()
                    val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                    if (newBooks.isNotEmpty()) {
                        val startPosition = topRatedByCountBooks.size
                        topRatedByCountBooks.addAll(newBooks)
                        topRatedByCountAdapter.notifyItemRangeInserted(startPosition, newBooks.size)
                        Log.d("FirestoreLoad", "Loaded ${newBooks.size} Top Rated (by count) books.")
                    }
                } else {
                    Log.d("FirestoreLoad", "No more Top Rated (by count) books found.")
                }
                isTopRatedByCountLoading = false
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error loading top rated (by count) books: ${exception.message}", exception)
                isTopRatedByCountLoading = false
            }
    }

    private fun loadHighRatedFictionBooks() {
        if (isHighRatedFictionLoading || !::highRatedFictionAdapter.isInitialized) return
        isHighRatedFictionLoading = true
        Log.d("FirestoreLoad", "Loading High-Rated Fiction... LastVisible: ${lastVisibleHighRatedFiction?.id}")

        var query = firestore.collection("books_data")
            .whereGreaterThan("average_rating", 4.0)
            .whereArrayContains("genres", "Fiction")
            .orderBy("average_rating", Query.Direction.DESCENDING)
            .limit(booksPerPage.toLong())

        lastVisibleHighRatedFiction?.let { query = query.startAfter(it) }

        query.get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    lastVisibleHighRatedFiction = documents.documents.last()
                    val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                    if (newBooks.isNotEmpty()) {
                        val startPosition = highRatedFictionBooks.size
                        highRatedFictionBooks.addAll(newBooks)
                        highRatedFictionAdapter.notifyItemRangeInserted(startPosition, newBooks.size)
                        Log.d("FirestoreLoad", "Loaded ${newBooks.size} High-Rated Fiction books.")
                    }
                } else {
                    Log.d("FirestoreLoad", "No more High-Rated Fiction books found.")
                }
                isHighRatedFictionLoading = false
            }
            .addOnFailureListener { exception ->
                // Index hatası gelirse sadece logla, çökmeyi engelle
                Log.e("Firestore", "Error loading High-Rated Fiction books: ${exception.message}", exception)
                isHighRatedFictionLoading = false
            }
    }

    private fun loadHighRatedNonFictionBooks() {
        if (isHighRatedNonFictionLoading || !::highRatedNonFictionAdapter.isInitialized) return
        isHighRatedNonFictionLoading = true
        Log.d("FirestoreLoad", "Loading High-Rated NonFiction... LastVisible: ${lastVisibleHighRatedNonFiction?.id}")

        var query = firestore.collection("books_data")
            .whereGreaterThan("average_rating", 4.0)
            .whereArrayContains("genres", "Nonfiction")
            .orderBy("average_rating", Query.Direction.DESCENDING)
            .limit(booksPerPage.toLong())

        lastVisibleHighRatedNonFiction?.let { query = query.startAfter(it) }

        query.get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    lastVisibleHighRatedNonFiction = documents.documents.last()
                    val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                    if (newBooks.isNotEmpty()) {
                        val startPosition = highRatedNonFictionBooks.size
                        highRatedNonFictionBooks.addAll(newBooks)
                        highRatedNonFictionAdapter.notifyItemRangeInserted(startPosition, newBooks.size)
                        Log.d("FirestoreLoad", "Loaded ${newBooks.size} High-Rated NonFiction books.")
                    }
                } else {
                    Log.d("FirestoreLoad", "No more High-Rated NonFiction books found.")
                }
                isHighRatedNonFictionLoading = false
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error loading High-Rated NonFiction books: ${exception.message}", exception)
                isHighRatedNonFictionLoading = false
            }
    }

    // Random Genre Sections Ana Başlatıcı
    private fun loadRandomGenreSections() {
        if (selectedRandomGenres.isNotEmpty()) loadRandomGenreBooks(0)
        if (selectedRandomGenres.size > 1) loadRandomGenreBooks(1)
        if (selectedRandomGenres.size > 2) loadRandomGenreBooks(2)
    }

    // Random Genre yükleme fonksiyonu (index'e göre)
    private fun loadRandomGenreBooks(genreIndex: Int) {
        if (genreIndex < 0 || genreIndex >= selectedRandomGenres.size) {
            Log.w("FirestoreLoad", "Invalid genreIndex for random load: $genreIndex")
            return
        }

        val genre = selectedRandomGenres[genreIndex]
        val adapter: BookCategoryAdapter? // Nullable yapalım
        val bookList: ArrayList<Books>
        var lastVisible: DocumentSnapshot?
        val isLoading = getLoadingFlag(genreIndex) // Önce bayrağı oku

        if (isLoading) {
            Log.d("FirestoreLoad", "Genre $genre (Index $genreIndex) is already loading.")
            return
        }

        // Adapter'ı ve ilgili state'i al
        when (genreIndex) {
            0 -> {
                if (!::randomGenre1Adapter.isInitialized) return
                adapter = randomGenre1Adapter
                bookList = randomGenre1Books
                lastVisible = lastVisibleRandomGenre1
            }
            1 -> {
                if (!::randomGenre2Adapter.isInitialized) return
                adapter = randomGenre2Adapter
                bookList = randomGenre2Books
                lastVisible = lastVisibleRandomGenre2
            }
            2 -> {
                if (!::randomGenre3Adapter.isInitialized) return
                adapter = randomGenre3Adapter
                bookList = randomGenre3Books
                lastVisible = lastVisibleRandomGenre3
            }
            else -> return // Geçersiz index
        }

        setLoadingFlag(genreIndex, true) // Yüklemeyi başlat
        Log.d("FirestoreLoad", "Loading Random Genre '$genre' (Index $genreIndex)... LastVisible: ${lastVisible?.id}")

        var query = firestore.collection("books_data")
            .whereArrayContains("genres", genre)
            .orderBy("average_rating", Query.Direction.DESCENDING) // Örnek sıralama
            .limit(booksPerPage.toLong())

        lastVisible?.let { query = query.startAfter(it) }

        query.get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    setLastVisible(genreIndex, documents.documents.last()) // Helper ile lastVisible'ı güncelle
                    val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                    if (newBooks.isNotEmpty()) {
                        val startPosition = bookList.size
                        bookList.addAll(newBooks)
                        adapter?.notifyItemRangeInserted(startPosition, newBooks.size) // Null check
                        Log.d("FirestoreLoad", "Loaded ${newBooks.size} books for genre '$genre'. Total: ${bookList.size}")
                    }
                } else {
                    Log.d("FirestoreLoad", "No more books found for genre '$genre'.")
                }
                setLoadingFlag(genreIndex, false) // Helper ile bayrağı sıfırla
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error loading books for genre '$genre': ${exception.message}", exception)
                setLoadingFlag(genreIndex, false) // Helper ile bayrağı sıfırla
            }
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


    // --- "For You" Recommendations (Cache First Logic - For Döngüsü ile ID Çıkarma) ---
    private fun loadForYouRecommendations() {
        if (!::forYouAdapter.isInitialized) {
            Log.w("ForYou", "Adapter not initialized yet.")
            return
        }
        if (isForYouLoading) return
        val userId = auth.currentUser?.uid ?: run {
            Log.w("ForYou", "User not logged in.")
            hideSection(binding.tvForYou, binding.rvForYou)
            return
        }

        isForYouLoading = true
        Log.d("ForYou", "Loading 'For You' recommendations for user $userId...")
        hideSection(binding.tvForYou, binding.rvForYou)
        forYouBooks.clear()
        forYouAdapter.notifyDataSetChanged()

        uiScope.launch {
            var bookIdsToLoad: List<Long>? = null // ID'ler Long olacak
            var triggerApi = false

            try {
                // Step 1: Check Cache
                val cacheRef = firestore.collection("user_recommendations").document(userId)
                val cacheSnapshot = cacheRef.get().await()
                val cachedData = try { cacheSnapshot.toObject<UserRecommendations>() } catch(e:Exception){ null }

                // Step 2: Check conditions for API call
                if (cachedData == null || cachedData.recommendations.isNullOrEmpty()) {
                    Log.d("ForYouLogic", "Cache miss (no data or empty recommendations). Triggering API.")
                    triggerApi = true
                } else {
                    val cacheTimestamp = cachedData.timestamp?.seconds ?: 0L
                    val isStale = (Timestamp.now().seconds - cacheTimestamp) > TimeUnit.DAYS.toSeconds(1)

                    if (isStale) {
                        Log.d("ForYouLogic", "Cache is stale. Triggering API.")
                        triggerApi = true
                    } else {
                        val currentRatingCount = getRatingCount(userId, cachedData.rated_books_count ?: 0)
                        if (currentRatingCount < 0) {
                            Log.w("ForYouLogic", "Could not get current rating count. Loading from potentially inaccurate cache.")
                            // ** === ID ÇIKARMA (For Döngüsü) === **
                            val recommendationsList = cachedData.recommendations
                            val extractedIds = mutableListOf<Long>()
                            if (recommendationsList != null) {
                                for (entry in recommendationsList) {
                                    entry.book_id?.let { id -> extractedIds.add(id) }
                                }
                            }
                            bookIdsToLoad = if (extractedIds.isNotEmpty()) extractedIds else null
                            // ** =============================== **
                        } else {
                            val cachedCount = cachedData.rated_books_count ?: 0
                            val isCountDiscrepancy = currentRatingCount >= cachedCount + 5
                            if (isCountDiscrepancy) {
                                Log.d("ForYouLogic", "Rating count discrepancy ($currentRatingCount vs $cachedCount). Triggering API.")
                                triggerApi = true
                            } else {
                                Log.d("ForYouLogic", "Cache is valid. Loading from cache.")
                                // ** === ID ÇIKARMA (For Döngüsü) === **
                                val recommendationsList = cachedData.recommendations
                                val extractedIds = mutableListOf<Long>()
                                if (recommendationsList != null) {
                                    for (entry in recommendationsList) {
                                        entry.book_id?.let { id -> extractedIds.add(id) }
                                    }
                                }
                                bookIdsToLoad = if (extractedIds.isNotEmpty()) extractedIds else null
                                // ** =============================== **
                            }
                        }
                    }
                }

                // Step 3: Trigger API if needed
                if (triggerApi) {
                    Log.d("ForYouAPI", "Triggering /recommend-by-genre API for user $userId...")
                    try {
                        withContext(Dispatchers.IO) { RetrofitInstance.api.getRecommendationsByGenre(userId) }
                        Log.d("ForYouAPI", "API triggered. Waiting briefly for Firestore update...")
                        delay(3000)

                        // Step 4a: Read Firestore AGAIN
                        val updatedSnapshot = cacheRef.get().await()
                        val updatedCacheData = try { updatedSnapshot.toObject<UserRecommendations>() } catch(e:Exception){ null }

                        if (updatedCacheData?.recommendations != null && updatedCacheData.recommendations.isNotEmpty()) {
                            Log.d("ForYouLogic", "Loading recommendations from Firestore after API trigger.")
                            // ** === ID ÇIKARMA (For Döngüsü) === **
                            val recommendationsList = updatedCacheData.recommendations
                            val extractedIds = mutableListOf<Long>()
                            if (recommendationsList != null) {
                                for (entry in recommendationsList) {
                                    entry.book_id?.let { id -> extractedIds.add(id) }
                                }
                            }
                            bookIdsToLoad = if (extractedIds.isNotEmpty()) extractedIds else null
                            // ** =============================== **
                        } else {
                            Log.w("ForYouLogic", "Firestore cache still empty or invalid after API trigger and delay.")
                            bookIdsToLoad = null
                        }
                    } catch (e: Exception) {
                        Log.e("ForYouAPI", "Error triggering /recommend-by-genre API: ${e.message}", e)
                        bookIdsToLoad = null
                    }
                }

                // Step 5: Load book details if IDs were found
                if (!bookIdsToLoad.isNullOrEmpty()) {
                    fetchBookDetailsByIds(
                        bookIds = bookIdsToLoad.map { it.toInt() },
                        targetList = forYouBooks,
                        targetAdapter = forYouAdapter,
                        targetTextView = binding.tvForYou,
                        targetRecyclerView = binding.rvForYou,
                        sectionTitle = "Sizin İçin Önerilenler",
                        setLoadingFlag = { isForYouLoading = it }
                    )
                } else {
                    Log.w("ForYouLogic", "No valid book IDs found to load details.")
                    isForYouLoading = false
                    hideSection(binding.tvForYou, binding.rvForYou)
                }

            } catch (e: Exception) {
                Log.e("ForYou", "Error in loadForYouRecommendations flow: ${e.message}", e)
                isForYouLoading = false
                hideSection(binding.tvForYou, binding.rvForYou)
            }
        }
    }

    // Helper to get current rating count from Firestore using count() aggregation
    private suspend fun getRatingCount(userId: String, minimumExpected: Long): Long {
        return try {
            // Sorguyu optimize etmek için limit ekle (en az beklenen + fark kadar)
            val countQuery = firestore.collection("user_ratings")
                .whereEqualTo("userId", userId) // Alan adını kontrol et
                .limit(minimumExpected + 5) // Daha fazla saymaya gerek yok
                .count() // count() aggregation kullan
                .get(com.google.firebase.firestore.AggregateSource.SERVER) // Sunucudan sayımı al
                .await() // Coroutine ile bekle
            countQuery.count
        } catch (e: Exception) {
            Log.e("RatingCount", "Error getting rating count for user $userId: ${e.message}")
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


    // --- Search Functions ---
    private fun switchToSearchMode() {
        if (!isInSearchMode) {
            isInSearchMode = true
            binding.scrollViewCategories.visibility = View.GONE
            binding.bookRV.visibility = View.VISIBLE
            binding.fabButton.visibility = View.GONE // FAB'ı gizle
            Log.d("SearchMode", "Switched to search mode VIEW")
        }
    }

    private fun switchToCategoriesMode() {
        if (isInSearchMode) {
            isInSearchMode = false
            searchQuery = null // Arama sorgusunu temizle
            binding.searchMainpage.setQuery("", false) // SearchView içini temizle
            binding.searchMainpage.clearFocus() // Klavyeyi gizle
            binding.scrollViewCategories.visibility = View.VISIBLE
            binding.bookRV.visibility = View.GONE
            binding.fabButton.visibility = View.VISIBLE // FAB'ı göster
            clearSearchResults() // Arama sonuçlarını temizle
            Log.d("SearchMode", "Switched to categories mode VIEW")
        }
    }

    private fun clearSearchResults() {
        if (searchBookList.isNotEmpty()) {
            val size = searchBookList.size
            searchBookList.clear()
            // Adapter başlatıldıysa güncelle
            if(::searchAdapter.isInitialized) {
                searchAdapter.notifyItemRangeRemoved(0, size)
            }
        }
        lastVisibleSearchDocument = null // Sayfalamayı sıfırla
        isSearchLastPage = false // Son sayfa bayrağını sıfırla
        Log.d("SearchMode", "Search results cleared.")
    }


    private suspend fun searchBooks(queryText: String) { // suspend fun yapıldı
        if (isSearchLoading || isSearchLastPage) {
            Log.d("Search", "Search aborted. Loading: $isSearchLoading, LastPage: $isSearchLastPage")
            return
        }
        isSearchLoading = true
        // ProgressBar'ı Main thread'de göster
        withContext(Dispatchers.Main) {
            binding.progressBar.visibility = View.VISIBLE
        }
        Log.d("Search", "Executing search for: '$queryText', LastVisible: ${lastVisibleSearchDocument?.id}")

        try {
            val formattedQuery = queryText.lowercase().trim()
            // title_lowercase alanına göre artan sırada arama (index gerekli)
            var firestoreQuery: Query = firestore.collection("books_data")
                .orderBy("title_lowercase")
                .startAt(formattedQuery)
                .endAt(formattedQuery + "\uf8ff") // Prefix eşleşmesi için
                .limit(10L) // Sayfa başına sonuç limiti (ayarlanabilir)

            // Sayfalama için son görünen belgeyi kullan
            lastVisibleSearchDocument?.let {
                firestoreQuery = firestoreQuery.startAfter(it)
                Log.d("Search", "Applying pagination using startAfter.")
            }

            val documents = firestoreQuery.get().await() // Coroutine ile bekle

            // Sonuçları işlemek ve UI güncellemek için Main thread'e dön
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE // ProgressBar'ı gizle
                if (documents.isEmpty) {
                    Log.d("Search", "Firestore returned no documents for '$formattedQuery'.")
                    isSearchLastPage = true // Başka sonuç yok
                    // Eğer liste zaten boşsa "Sonuç bulunamadı" mesajı gösterilebilir
                    // if (searchBookList.isEmpty()) { showNoResultsMessage() }
                } else {
                    Log.d("Search", "Firestore returned ${documents.size()} documents.")
                    lastVisibleSearchDocument = documents.documents.last() // Sonraki sayfa için sakla
                    // Gelen belge sayısı limitten az ise son sayfadır
                    isSearchLastPage = documents.size() < 10 // Limite göre kontrol et

                    // Belgeleri Books nesnesine çevir
                    val newBooks = documents.mapNotNull { parseDocumentToBook(it) }

                    if (newBooks.isNotEmpty()) {
                        // Adapter başlatıldıysa listeye ekle ve güncelle
                        if(::searchAdapter.isInitialized) {
                            val startPosition = searchBookList.size
                            searchBookList.addAll(newBooks)
                            searchAdapter.notifyItemRangeInserted(startPosition, newBooks.size)
                        }
                        Log.d("Search", "Added ${newBooks.size} books. Total: ${searchBookList.size}. isSearchLastPage = $isSearchLastPage")
                    } else {
                        // Gelen belgeler parse edilemediyse veya filtreye takıldıysa
                        Log.d("Search", "No books matched parseDocumentToBook after query.")
                        isSearchLastPage = documents.size() < 10 // Yine de son sayfa olabilir
                    }
                }
                isSearchLoading = false // Yükleme bitti
            }
        } catch (e: Exception) {
            // Hata durumunda logla ve UI'ı güncelle
            Log.e("Firestore", "Error searching books for '$queryText': ${e.message}", e)
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                isSearchLoading = false
                // Hata mesajı gösterilebilir: showErrorToast("Arama sırasında hata oluştu.")
            }
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