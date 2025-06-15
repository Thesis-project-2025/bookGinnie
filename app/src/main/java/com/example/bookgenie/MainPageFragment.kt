package com.example.bookgenie

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.example.bookgenie.api.RetrofitInstance
import com.example.bookgenie.databinding.FragmentMainPageBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.math.abs


// --- Data Sınıfları (Mevcut haliyle korunuyor) ---

data class RecommendationEntry(
    val book_id: Long? = null,
    val score: Double? = null
) {
    constructor() : this(null, null)
}

data class UserRecommendations(
    val recommendations: List<RecommendationEntry>? = null,
    val rated_books_count: Long? = null,
    val timestamp: Timestamp? = null,
    val user_id: String? = null,
    val type: String? = null
) {
    constructor() : this(null, null, null, null, null)
}

data class SimilarBooksResult(
    val recommendations: List<RecommendationEntry>? = null,
    val timestamp: Timestamp? = null
) {
    constructor() : this(null, null)
}

// --- YENİ: Modüler kategori yönetimi için Helper Data Class ---

private data class DynamicCategorySection(
    val titleTextView: TextView,
    val recyclerView: RecyclerView,
    var adapter: BookCategoryAdapter,
    val books: ArrayList<Books> = ArrayList(),
    var isLoading: Boolean = false,
    var lastVisibleDoc: DocumentSnapshot? = null,
    val sectionQuery: Query,
    var isEnabled: Boolean = true,
    var dynamicTitle: String? = null,
    var isLastPage: Boolean = false // EKLENDİ: Sayfalama durumunu takip etmek için
)


// Helper data class for loadSimilarBooksForIndex
private data class SimilarSectionComponents(
    val isLoading: Boolean,
    val setLoadingFlag: (Boolean) -> Unit,
    val targetList: ArrayList<Books>,
    val adapter: BookCategoryAdapter?,
    val textView: TextView,
    val recyclerView: RecyclerView,
    val originalTitle: String?
)

class MainPageFragment : Fragment(), FilterBottomSheetDialogFragment.FilterDialogListener {
    private var _binding: FragmentMainPageBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private val fragmentJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + fragmentJob)

    private val booksPerPage = 10
    private val categoryBooksPerPage = 10 // Kategori bölümleri için sayfa başına kitap sayısı artırıldı

    // --- Carousel (Mevcut haliyle korunuyor) ---
    private lateinit var carouselAdapter: CarouselBookAdapter
    private val carouselBookList = ArrayList<Books>()
    private var carouselScrollJob: Job? = null
    private val CAROUSEL_AUTO_SCROLL_DELAY_MS = 5000L
    private val CAROUSEL_FETCH_LIMIT = 20L
    private val CAROUSEL_DISPLAY_COUNT = 7
    private var isCarouselManuallyPaused: Boolean = false


    // --- Search Functionality (Mevcut haliyle korunuyor) ---
    private lateinit var searchAdapter: BookAdapter
    private val searchBookList = ArrayList<Books>()
    private var lastVisibleSearchDocument: DocumentSnapshot? = null
    private var isSearchLoading = false
    private var isSearchLastPage = false
    private var searchQuery: String? = null
    private var isInSearchMode = false
    private var currentFilters: BookFilters = BookFilters()
    private var currentSearchOperationJob: Job? = null


    // --- YENİ: Dinamik ve Modüler Kategori Yönetimi ---
    private val dynamicCategorySections = mutableListOf<DynamicCategorySection>()
    private val NUM_RANDOM_GENRES = 8 // Rastgele tür sayısı artırıldı
    private val availableGenres = listOf(
        "Fiction", "Romance", "Nonfiction", "Fantasy", "Contemporary", "Mystery",
        "Audiobook", "Young Adult", "Historical Fiction", "Childrens", "Historical",
        "Classics", "Science Fiction", "History", "Thriller", "Paranormal", "Crime",
        "Novels", "Literature", "Humor", "Contemporary Romance", "Biography",
        "Adventure", "Adult", "Short Stories", "Graphic Novels", "Mystery Thriller",
        "Horror", "Comics", "Suspense", "Middle Grade", "Chick Lit", "Picture Books",
        "Memoir", "Philosophy", "Magic", "Christian", "Urban Fantasy", "Erotica",
        "Animals", "Science Fiction Fantasy", "Politics", "Religion", "Paranormal Romance",
        "M M Romance", "Literary Fiction", "Reference", "LGBT", "Self Help", "Psychology",
        "Science", "Historical Romance", "Poetry", "Realistic Fiction", "Vampires",
        "Dystopia", "British Literature", "War", "School", "Manga", "Graphic Novels Comics",
        "Spirituality", "France", "Family", "Art", "Biography Memoir", "New Adult",
        "Christian Fiction", "Business", "Military Fiction", "Adult Fiction", "Travel",
        "Supernatural", "High Fantasy", "Teen", "American", "Comic Book", "Drama", "Essays"
    ).distinct().sorted()


    // --- "For You" ve "Similar Books" (Mevcut haliyle korunuyor) ---
    private lateinit var forYouAdapter: BookCategoryAdapter
    private val forYouBooks = ArrayList<Books>()
    private var isForYouLoading = false

    private val MAX_SIMILAR_SECTIONS = 2
    private lateinit var similarTo1Adapter: BookCategoryAdapter
    private val similarTo1Books = ArrayList<Books>()
    private var isSimilarTo1Loading = false
    private var originalBookTitle1: String? = null

    private lateinit var similarTo2Adapter: BookCategoryAdapter
    private val similarTo2Books = ArrayList<Books>()
    private var isSimilarTo2Loading = false
    private var originalBookTitle2: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        firestore = Firebase.firestore
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Mevcut kurulumlar
        setupCarousel()
        setupSearchView()
        setupBackPressHandler()
        // RecyclerView kurulumu artık dinamik bölümleri de içerecek
        setupRecyclerViewsAndSections()

        // Mevcut veri yüklemeleri
        loadCarouselBooks()
        loadForYouRecommendations()
        loadSimilarBooksSections()

        // YENİ: Dinamik bölümleri yükle
        loadAllDynamicSections()

        // Başlangıç UI durumları
        binding.filterButton.visibility = View.GONE
        binding.tvNoResultsSearch?.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (!isCarouselManuallyPaused && carouselBookList.isNotEmpty() && _binding != null) {
            startCarouselAutoScroll()
        }
    }

    override fun onPause() {
        super.onPause()
        stopCarouselAutoScrollInternally()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fragmentJob.cancel()
        currentSearchOperationJob?.cancel()
        stopCarouselAutoScrollInternally()
        _binding = null
    }

    // --- YENİ: Dinamik Bölüm Kurulum ve Yükleme Mantığı ---

    private fun setupRecyclerViewsAndSections() {
        if (_binding == null) return

        // 1. Arama RecyclerView Kurulumu (Mevcut)
        binding.bookRV.layoutManager = LinearLayoutManager(requireContext())
        searchAdapter = BookAdapter(requireContext(), searchBookList, "main")
        binding.bookRV.adapter = searchAdapter
        setupSearchScrollListener()

        // 2. "For You" ve "Similar Books" Kurulumu (Mevcut)
        setupStaticCategoryRecyclerView(binding.rvForYou)
        forYouAdapter = BookCategoryAdapter(requireContext(), forYouBooks) { }
        binding.rvForYou.adapter = forYouAdapter
        hideSection(binding.tvForYou, binding.rvForYou)

        setupStaticCategoryRecyclerView(binding.rvSimilarTo1)
        similarTo1Adapter = BookCategoryAdapter(requireContext(), similarTo1Books) { }
        binding.rvSimilarTo1.adapter = similarTo1Adapter
        hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)

        setupStaticCategoryRecyclerView(binding.rvSimilarTo2)
        similarTo2Adapter = BookCategoryAdapter(requireContext(), similarTo2Books) { }
        binding.rvSimilarTo2.adapter = similarTo2Adapter
        hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)


        // 3. YENİ: Tüm dinamik kategorileri oluştur ve UI'a ekle
        createDynamicSections()
    }

    private fun createDynamicSections() {
        // Önceki dinamik bölümleri temizle (eğer varsa)
        binding.dynamicCategoriesContainer.removeAllViews()
        dynamicCategorySections.clear()

        // Kategori listesi
        val sectionsToCreate = mutableListOf<Pair<String, Query>>()

        // En çok oy alanlar
        sectionsToCreate.add("Top Rated by Vote Count" to firestore.collection("books_data")
            .orderBy("rating_count", Query.Direction.DESCENDING)
            .limit(categoryBooksPerPage.toLong()))

        // Yüksek puanlı Kurgu
        sectionsToCreate.add("High-Rated Fiction" to firestore.collection("books_data")
            .whereArrayContains("genres", "Fiction")
            .whereGreaterThan("average_rating", 3.99)
            .orderBy("average_rating", Query.Direction.DESCENDING)
            .limit(categoryBooksPerPage.toLong()))

        // Yüksek puanlı Kurgu-Dışı
        sectionsToCreate.add("High-Rated Non-Fiction" to firestore.collection("books_data")
            .whereArrayContains("genres", "Nonfiction")
            .whereGreaterThan("average_rating", 3.99)
            .orderBy("average_rating", Query.Direction.DESCENDING)
            .limit(categoryBooksPerPage.toLong()))

        // Yeni Çıkan Hit Kitaplar
        sectionsToCreate.add("Recent Hits" to firestore.collection("books_data")
            .whereGreaterThan("publication_year", 2022)
            .orderBy("publication_year", Query.Direction.DESCENDING)
            .orderBy("rating_count", Query.Direction.DESCENDING)
            .limit(categoryBooksPerPage.toLong()))

        // 2000'li Yılların Popüler Kitapları
        sectionsToCreate.add("Popular from the 2000s" to firestore.collection("books_data")
            .whereGreaterThanOrEqualTo("publication_year", 2000)
            .whereLessThanOrEqualTo("publication_year", 2009)
            .orderBy("publication_year", Query.Direction.DESCENDING)
            .orderBy("rating_count", Query.Direction.DESCENDING)
            .limit(categoryBooksPerPage.toLong()))

        // Popüler E-Kitaplar
        sectionsToCreate.add("Popular eBooks" to firestore.collection("books_data")
            .whereEqualTo("is_ebook", true)
            .orderBy("rating_count", Query.Direction.DESCENDING)
            .limit(categoryBooksPerPage.toLong()))

        // Hızlı Okunanlar (200 sayfadan az)
        sectionsToCreate.add("Quick Reads" to firestore.collection("books_data")
            .whereLessThan("num_pages", 200)
            .whereGreaterThan("num_pages", 50) // Çok kısa olanları ele
            .orderBy("num_pages", Query.Direction.ASCENDING)
            .orderBy("rating_count", Query.Direction.DESCENDING)
            .limit(categoryBooksPerPage.toLong()))

        // Popüler İngilizce Kitaplar
        sectionsToCreate.add("Popular in English" to firestore.collection("books_data")
            .whereEqualTo("language_code", "eng")
            .orderBy("rating_count", Query.Direction.DESCENDING)
            .limit(categoryBooksPerPage.toLong()))

        // Rastgele Türler
        val selectedGenres = availableGenres.shuffled().take(NUM_RANDOM_GENRES)
        selectedGenres.forEach { genre ->
            sectionsToCreate.add("Best of $genre" to firestore.collection("books_data")
                .whereArrayContains("genres", genre)
                .orderBy("average_rating", Query.Direction.DESCENDING)
                .limit(categoryBooksPerPage.toLong()))
        }

        // Dinamik olarak UI oluştur
        sectionsToCreate.forEach { (title, query) ->
            val section = createAndAddSectionView(title, query)
            dynamicCategorySections.add(section)
        }

        // Favori Yazar bölümünü oluştur (sorgusu daha sonra belirlenecek)
        val authorQuery = firestore.collection("books_data").limit(categoryBooksPerPage.toLong()) // Geçici sorgu
        val authorSection = createAndAddSectionView("From a Favorite Author", authorQuery)
        authorSection.isEnabled = false // Başlangıçta devre dışı
        dynamicCategorySections.add(authorSection)
    }


    private fun createAndAddSectionView(title: String, query: Query): DynamicCategorySection {
        val inflater = LayoutInflater.from(requireContext())
        val sectionView = inflater.inflate(R.layout.dynamic_category_section, binding.dynamicCategoriesContainer, false)

        val titleTextView = sectionView.findViewById<TextView>(R.id.tv_dynamic_category_title)
        val recyclerView = sectionView.findViewById<RecyclerView>(R.id.rv_dynamic_category_books)

        titleTextView.text = title
        val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        recyclerView.layoutManager = layoutManager

        val bookList = ArrayList<Books>()
        val adapter = BookCategoryAdapter(requireContext(), bookList) {
            // Bu lambda artık kullanılmıyor, OnScrollListener ile yönetiliyor.
        }
        recyclerView.adapter = adapter

        binding.dynamicCategoriesContainer.addView(sectionView)
        sectionView.visibility = View.GONE

        val section = DynamicCategorySection(
            titleTextView = titleTextView,
            recyclerView = recyclerView,
            adapter = adapter,
            books = bookList,
            sectionQuery = query
        )

        // --- YENİ: SCROLL LISTENER EKLENDİ ---
        recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // Sadece yatayda sağa doğru kaydırıldığında çalış
                if (dx > 0) {
                    val totalItemCount = layoutManager.itemCount
                    val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                    // Listenin sonuna yaklaşıldı mı ve şu an bir yükleme yapılıyor mu kontrol et
                    val threshold = 5 // Son 5 eleman kala yüklemeyi tetikle
                    if (!section.isLoading && !section.isLastPage && totalItemCount > 0 && lastVisibleItemPosition >= totalItemCount - threshold) {
                        Log.d("Pagination", "Loading more for '${section.titleTextView.text}'")
                        // Daha fazla veri yükle
                        loadDynamicSectionData(section, loadMore = true)
                    }
                }
            }
        })
        // --- SCROLL LISTENER BİTTİ ---

        return section
    }

    private fun loadAllDynamicSections() {
        // Statik sorgusu olan bölümleri yükle
        dynamicCategorySections.filter { it.isEnabled }.forEach { section ->
            loadDynamicSectionData(section)
        }
        // Özel mantık gerektiren bölümleri yükle
        loadFavoriteAuthorSection()
    }

    private fun loadDynamicSectionData(section: DynamicCategorySection, loadMore: Boolean = false) {
        if (section.isLoading) return
        section.isLoading = true

        var query = section.sectionQuery

        if (loadMore && section.lastVisibleDoc != null) {
            query = query.startAfter(section.lastVisibleDoc!!)
        } else if (!loadMore) {
            section.books.clear()
            section.lastVisibleDoc = null
            section.isLastPage = false // GÜNCELLENDİ: Yeni yüklemede sayfalama durumunu sıfırla
            section.adapter.notifyDataSetChanged()
        }

        query.get().addOnSuccessListener { documents ->
            if (_binding == null || !isAdded) {
                section.isLoading = false
                return@addOnSuccessListener
            }

            if (documents.isEmpty) {
                section.isLastPage = true // GÜNCELLENDİ: Dönen döküman yoksa bu son sayfadır.
            } else {
                section.lastVisibleDoc = documents.documents.lastOrNull()
                // GÜNCELLENDİ: Gelen kitap sayısı limitten az ise, bu son sayfadır.
                section.isLastPage = documents.size() < categoryBooksPerPage

                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }

                if (newBooks.isNotEmpty()) {
                    val startPos = section.books.size
                    section.books.addAll(newBooks)
                    if (startPos == 0) section.adapter.notifyDataSetChanged()
                    else section.adapter.notifyItemRangeInserted(startPos, newBooks.size)
                }
            }

            // UI'ı güncelle
            if (section.books.isNotEmpty()) {
                (section.titleTextView.parent as? View)?.visibility = View.VISIBLE
                if (section.dynamicTitle != null) {
                    section.titleTextView.text = section.dynamicTitle
                }
            } else {
                if(!loadMore) { // Sadece ilk yüklemede hiç kitap yoksa bölümü gizle
                    (section.titleTextView.parent as? View)?.visibility = View.GONE
                }
            }
            section.isLoading = false
        }.addOnFailureListener { e ->
            Log.e("DynamicSection", "Error loading '${section.titleTextView.text}': ${e.message}")
            (section.titleTextView.parent as? View)?.visibility = View.GONE
            section.isLoading = false
        }
    }

    private fun loadFavoriteAuthorSection() {
        val userId = auth.currentUser?.uid ?: return
        val authorSection = dynamicCategorySections.find { it.titleTextView.text.contains("Favorite Author") } ?: return

        uiScope.launch {
            try {
                // Kullanıcının 4+ puan verdiği son kitapları bul
                val highRatedBooksQuery = firestore.collection("user_ratings")
                    .whereEqualTo("userId", userId)
                    .whereGreaterThanOrEqualTo("rating", 4.0)
                    .orderBy("rating", Query.Direction.DESCENDING)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(10)
                    .get().await()

                if(highRatedBooksQuery.isEmpty) {
                    authorSection.isEnabled = false
                    return@launch
                }

                // Bu kitapların detaylarını alarak yazar adlarını bul
                val bookIds = highRatedBooksQuery.documents.mapNotNull { it.getLong("bookId")?.toInt() }
                val bookDetails = fetchBookDetailsByIdsInternal(bookIds)

                // En sık oy verilen yazarı bul
                val favoriteAuthor = bookDetails.map { it.author_name }
                    .filter { it.isNotBlank() }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }?.key

                if (favoriteAuthor != null) {
                    Log.d("AuthorSection", "Found favorite author: $favoriteAuthor")
                    // Bu yazara ait kitapları getirecek yeni sorguyu oluştur
                    val newQuery = firestore.collection("books_data")
                        .whereEqualTo("author_name", favoriteAuthor)
                        .orderBy("average_rating", Query.Direction.DESCENDING)
                        .limit(categoryBooksPerPage.toLong())

                    // Bölümün sorgusunu ve başlığını güncelle
                    authorSection.sectionQuery.firestore.runTransaction {}.addOnSuccessListener {
                        authorSection.dynamicTitle = "More from $favoriteAuthor"
                        authorSection.isEnabled = true
                        // Verileri yükle
                        loadDynamicSectionData(authorSection.copy(sectionQuery = newQuery))
                    }
                } else {
                    authorSection.isEnabled = false
                }
            } catch(e: Exception) {
                if (e is CancellationException) throw e
                Log.e("AuthorSection", "Error loading favorite author section", e)
                authorSection.isEnabled = false
            } finally {
                if(!authorSection.isEnabled) {
                    if(_binding != null) (authorSection.titleTextView.parent as? View)?.visibility = View.GONE
                }
            }
        }
    }


    // --- Mevcut Fonksiyonlar (Gerekli Değişiklikler Yapıldı) ---

    private fun setupStaticCategoryRecyclerView(recyclerView: RecyclerView) {
        context?.let { ctx ->
            recyclerView.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    // `loadTopRatedByCountBooks`, `loadHighRatedFictionBooks` vb. fonksiyonlar artık
    // `loadAllDynamicSections` ve `loadDynamicSectionData` tarafından yönetildiği için kaldırılabilir.

    // Diğer mevcut fonksiyonlar (Carousel, Search, ForYou, Similar, vb.) burada yer alacak...
    // Bu fonksiyonlarda bir değişiklik yapılmadığı için tekrar eklenmemiştir.
    // Lütfen bu bölümün altına kendi mevcut `setupCarousel`, `setupSearchView`, `performSearch`,
    // `loadForYouRecommendations` vb. fonksiyonlarınızı yapıştırın.
    // Aşağıya sadece referans olması için bazılarını ekliyorum.

    private fun setupCarousel() {
        if (_binding == null) return // Binding null ise işlem yapma
        carouselAdapter = CarouselBookAdapter(requireContext(), carouselBookList)
        binding.viewPagerCarousel.adapter = carouselAdapter
        binding.viewPagerCarousel.offscreenPageLimit = 3
        binding.viewPagerCarousel.clipToPadding = false
        binding.viewPagerCarousel.clipChildren = false

        val compositePageTransformer = CompositePageTransformer()
        val carouselMargin = try {
            resources.getDimensionPixelOffset(R.dimen.carousel_margin)
        } catch (e: android.content.res.Resources.NotFoundException) {
            Log.w("SetupCarousel", "R.dimen.carousel_margin not found. Using default 40dp equivalent.")
            (40 * resources.displayMetrics.density).toInt() // Fallback to 40dp in pixels
        }
        compositePageTransformer.addTransformer(MarginPageTransformer(carouselMargin))
        compositePageTransformer.addTransformer { page, position ->
            val r = 1 - abs(position)
            page.scaleY = 0.85f + r * 0.15f
            page.scaleX = 0.85f + r * 0.10f // Hafif genişlik ölçeklemesi
            page.alpha = 0.5f + r * 0.5f   // Ortadaki daha belirgin
        }
        binding.viewPagerCarousel.setPageTransformer(compositePageTransformer)

        // Kullanıcı etkileşiminde oto-kaydırmayı yönet
        binding.viewPagerCarousel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        if (carouselScrollJob?.isActive == true) {
                            isCarouselManuallyPaused = true
                            stopCarouselAutoScrollInternally()
                        }
                    }
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        if (isCarouselManuallyPaused) {
                            // Kullanıcı sürüklemeyi bitirdi, bir süre sonra otomatik kaydırmayı yeniden başlat
                            uiScope.launch {
                                delay(CAROUSEL_AUTO_SCROLL_DELAY_MS) // Tam bekleme süresi
                                if (isActive && isCarouselManuallyPaused && isResumed && _binding != null) {
                                    isCarouselManuallyPaused = false
                                    startCarouselAutoScroll()
                                }
                            }
                        }
                    }
                }
            }
        })
    }
    private fun setupSearchView() {
        if (_binding == null) return
        val searchView = binding.searchMainpage
        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        try {
            searchEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black)) // Renklerinizi kontrol edin
            val hintColor = try {
                ContextCompat.getColor(requireContext(), R.color.siyahimsi) // Renklerinizi kontrol edin
            } catch (e: android.content.res.Resources.NotFoundException) {
                ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
            }
            searchEditText.setHintTextColor(hintColor)
        } catch (e: Exception) {
            Log.w("SetupSearch", "Error setting search text/hint colors. ${e.message}")
        }

        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isInSearchMode) {
                switchToSearchMode()
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                currentSearchOperationJob?.cancel()
                searchQuery = newText.trim() // searchQuery'yi burada güncelle
                if (searchQuery!!.isBlank() && currentFilters == BookFilters()) {
                    if (isInSearchMode) {
                        clearSearchResults()
                    }
                } else {
                    if (!isInSearchMode) {
                        switchToSearchMode()
                    }
                    currentSearchOperationJob = uiScope.launch {
                        delay(400) // Debounce
                        if (isActive) {
                            performSearch() // performSearch searchQuery'yi ve currentFilters'ı kullanacak
                        }
                    }
                }
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                currentSearchOperationJob?.cancel()
                searchView.clearFocus() // Klavyeyi gizle
                searchQuery = query.trim() // searchQuery'yi burada güncelle
                if (searchQuery!!.isNotBlank() || currentFilters != BookFilters()) {
                    if (!isInSearchMode) {
                        switchToSearchMode()
                    }
                    performSearch() // performSearch searchQuery'yi ve currentFilters'ı kullanacak
                }
                return true
            }
        })

        try {
            binding.filterButton.setOnClickListener {
                if (isInSearchMode) {
                    val filterDialog = FilterBottomSheetDialogFragment.newInstance(availableGenres, currentFilters)
                    filterDialog.setFilterDialogListener(this@MainPageFragment)
                    filterDialog.show(childFragmentManager, FilterBottomSheetDialogFragment.TAG)
                }
            }
        } catch (e: Exception) {
            Log.e("FilterButton", "Filter button setup failed. ${e.message}")
        }
    }
    override fun onFiltersApplied(filters: BookFilters) {
        currentFilters = filters
        Log.d("MainPageFragment", "Filters applied from dialog: $filters")
        if (isInSearchMode) {
            performSearch() // Filtreler değiştiğinde aramayı yeniden yap (searchQuery'yi de kullanarak)
        }
    }
    private fun performSearch() {
        if (_binding == null) {
            Log.w("PerformSearch", "Binding is null, cannot perform search.")
            return
        }
        clearSearchResults() // Her yeni arama/filtreleme öncesi eski sonuçları temizle

        if (searchQuery.isNullOrBlank() && currentFilters == BookFilters()) {
            Log.d("MainPageSearch", "Search query and filters are empty. Not performing search.")
            binding.tvNoResultsSearch?.text = "Type to search or apply filters."
            binding.tvNoResultsSearch?.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            return
        }
        binding.tvNoResultsSearch?.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        // searchBooks fonksiyonu hem searchQuery'yi hem de currentFilters'ı kullanacak
        searchBooks(searchQuery, currentFilters) // currentFilters da gönderiliyor
    }
    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isInSearchMode) {
                        switchToCategoriesMode()
                    } else {
                        isEnabled = false // Callback'i devre dışı bırak ve varsayılan davranışı uygula
                        if (!findNavController().popBackStack()) {
                            // requireActivity().finish() // Eğer pop edilecek bir şey yoksa aktiviteyi kapatmayı düşünün
                        }
                    }
                }
            }
        )
    }
    private fun setupSearchScrollListener() {
        if (_binding == null) return
        binding.bookRV.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0 || !isInSearchMode) return

                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return // StaggeredGrid için de uyumlu hale getirilebilir
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                val threshold = booksPerPage / 2
                if (!isSearchLoading && !isSearchLastPage && totalItemCount > 0 &&
                    lastVisibleItemPosition >= totalItemCount - 1 - threshold
                ) {
                    if (!searchQuery.isNullOrBlank() || currentFilters != BookFilters()) {
                        Log.d("SearchScroll", "Loading more search results. Query: '$searchQuery', Filters: $currentFilters")
                        searchBooks(searchQuery, currentFilters)
                    }
                }
            }
        })
    }
    private fun loadCarouselBooks() {
        _binding?.let { it.viewPagerCarousel.visibility = View.GONE } ?: return

        uiScope.launch {
            try {
                val query = firestore.collection("books_data")
                    .orderBy("rating_count", Query.Direction.DESCENDING)
                    .limit(CAROUSEL_FETCH_LIMIT)
                val documents = query.get().await()

                if (!isActive || _binding == null) return@launch

                val fetchedBooks = documents.mapNotNull { parseDocumentToBook(it) }

                if (fetchedBooks.isNotEmpty()) {
                    val shuffledBooks = fetchedBooks.shuffled().take(CAROUSEL_DISPLAY_COUNT)
                    carouselBookList.clear()
                    carouselBookList.addAll(shuffledBooks)
                    carouselAdapter.notifyDataSetChanged()
                    binding.viewPagerCarousel.visibility = View.VISIBLE
                    if(isResumed && !isCarouselManuallyPaused) startCarouselAutoScroll()
                } else {
                    binding.viewPagerCarousel.visibility = View.GONE
                }
            } catch (ce: CancellationException) {
                Log.i("Carousel", "loadCarouselBooks coroutine was cancelled.")
            } catch (e: Exception) {
                Log.e("Carousel", "Error loading carousel books: ${e.message}", e)
                if (isActive && _binding != null) {
                    binding.viewPagerCarousel.visibility = View.GONE
                }
            }
        }
    }
    private fun startCarouselAutoScroll() {
        if (carouselBookList.size <= 1 || carouselScrollJob?.isActive == true || !isResumed || isCarouselManuallyPaused) return
        if (!isAdded || _binding == null) return

        carouselScrollJob = uiScope.launch {
            try {
                while (isActive) {
                    delay(CAROUSEL_AUTO_SCROLL_DELAY_MS)
                    if (isActive && isResumed && !isCarouselManuallyPaused && _binding != null && carouselBookList.isNotEmpty() && binding.viewPagerCarousel.adapter?.itemCount ?: 0 > 0) {
                        val currentItem = binding.viewPagerCarousel.currentItem
                        val itemCount = binding.viewPagerCarousel.adapter!!.itemCount
                        val nextItem = (currentItem + 1) % itemCount
                        binding.viewPagerCarousel.setCurrentItem(nextItem, true)
                    } else if (!isActive || !isResumed || isCarouselManuallyPaused || _binding == null) {
                        break
                    }
                }
            } catch (ce: CancellationException) { /* Normal cancellation */ }
            catch (e: Exception) { Log.e("Carousel", "Exception in auto-scroll: ${e.message}", e) }
        }
    }
    private fun stopCarouselAutoScrollInternally() {
        carouselScrollJob?.cancel()
        carouselScrollJob = null
    }
    private fun showSection(textView: TextView, recyclerView: RecyclerView) {
        _binding?.let {
            textView.visibility = View.VISIBLE
            recyclerView.visibility = View.VISIBLE
        }
    }
    private fun hideSection(textView: TextView, recyclerView: RecyclerView) {
        _binding?.let {
            textView.visibility = View.GONE
            recyclerView.visibility = View.GONE
        }
    }
    private fun loadForYouRecommendations() {
        if (_binding == null || !::forYouAdapter.isInitialized) { // Binding ve adapter kontrolü
            Log.w("ForYou", "Binding null or Adapter not initialized for 'For You'.")
            _binding?.let { hideSection(it.tvForYou, it.rvForYou) }
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
        forYouBooks.clear()
        forYouAdapter.notifyDataSetChanged() // Clear UI
        hideSection(binding.tvForYou, binding.rvForYou) // Hide section initially

        uiScope.launch {
            var bookIdsToLoad: List<Long>? = null
            var triggerApi = false

            try {
                val currentTotalRatingCount = getRatingCount(userId)
                if (!isActive) return@launch
                Log.d("ForYouLogic", "User $userId current total rating count: $currentTotalRatingCount")

                if (currentTotalRatingCount < 10) {
                    Log.d("ForYouLogic", "User has less than 10 ratings ($currentTotalRatingCount). 'For You' recommendations skipped.")
                    isForYouLoading = false
                    if (isActive && _binding != null && isAdded) hideSection(binding.tvForYou, binding.rvForYou)
                    return@launch
                }
                Log.d("ForYouLogic", "User has >= 10 ratings. Proceeding with cache/API logic.")

                val cacheRef = firestore.collection("user_recommendations").document(userId)
                val cacheSnapshot = cacheRef.get().await()
                if (!isActive) return@launch

                val cachedData = try { cacheSnapshot.toObject<UserRecommendations>() } catch (e: Exception) {
                    Log.e("ForYouCache", "Error converting 'user_recommendations' snapshot: ${e.message}", e)
                    null
                }

                if (cachedData == null || cachedData.recommendations.isNullOrEmpty()) {
                    Log.d("ForYouLogic", "Cache miss or recommendations empty. Triggering API.")
                    triggerApi = true
                } else {
                    val cacheTimestamp = cachedData.timestamp?.toDate()?.time ?: 0L
                    val isStale = (Timestamp.now().toDate().time - cacheTimestamp) > TimeUnit.DAYS.toMillis(1)
                    val cachedRatedBooksCount = cachedData.rated_books_count ?: 0

                    if (isStale || currentTotalRatingCount >= cachedRatedBooksCount + 5) {
                        Log.d("ForYouLogic", "Cache stale or significant new ratings. Triggering API. Stale: $isStale, NewRatings: ${currentTotalRatingCount >= cachedRatedBooksCount + 5}")
                        triggerApi = true
                    } else {
                        Log.d("ForYouLogic", "Using fresh cache.")
                        bookIdsToLoad = cachedData.recommendations.mapNotNull { it.book_id }
                    }
                }

                if (triggerApi) {
                    Log.d("ForYouAPI", "Triggering /recommend-by-genre API for user $userId (ratings: $currentTotalRatingCount)...")
                    try {
                        withContext(Dispatchers.IO) {
                            RetrofitInstance.api.getRecommendationsByGenre(userId)
                        }
                        Log.d("ForYouAPI", "API for getRecommendationsByGenre triggered. Waiting for Firestore update...")
                        delay(3000)

                        val updatedSnapshot = cacheRef.get().await()
                        if (!isActive) return@launch
                        val updatedCacheData = try { updatedSnapshot.toObject<UserRecommendations>() } catch (e: Exception) { null }
                        bookIdsToLoad = updatedCacheData?.recommendations?.mapNotNull { it.book_id }
                        Log.d("ForYouAPI", "Cache re-read. Updated Book IDs: $bookIdsToLoad")
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("ForYouAPI", "Error in API call or subsequent cache read: ${e.message}", e)
                        bookIdsToLoad = null
                    }
                }

                if (!isActive || _binding == null) return@launch

                if (!bookIdsToLoad.isNullOrEmpty()) {
                    fetchBookDetailsByIds(
                        bookIds = bookIdsToLoad.map { it.toInt() },
                        targetList = forYouBooks, targetAdapter = forYouAdapter,
                        targetTextView = binding.tvForYou, targetRecyclerView = binding.rvForYou,
                        sectionTitle = "Recommended For You",
                        setLoadingFlag = { isForYouLoading = it },
                        limit = categoryBooksPerPage
                    )
                } else {
                    isForYouLoading = false
                    hideSection(binding.tvForYou, binding.rvForYou)
                    Log.d("ForYouLogic", "No book IDs to load for 'For You' section.")
                }
            } catch (ce: CancellationException) {
                Log.i("ForYou", "loadForYouRecommendations coroutine was cancelled.")
                isForYouLoading = false
            } catch (e: Exception) {
                Log.e("ForYou", "Error in loadForYouRecommendations flow: ${e.message}", e)
                isForYouLoading = false
                if (isActive && _binding != null && isAdded) hideSection(binding.tvForYou, binding.rvForYou)
            }
        }
    }
    private suspend fun getRatingCount(userId: String): Long {
        return try {
            val countQuery = firestore.collection("user_ratings")
                .whereEqualTo("userId", userId)
                .count()
                .get(com.google.firebase.firestore.AggregateSource.SERVER)
                .await()
            countQuery.count
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("RatingCount", "Error getting rating count for user $userId: ${e.message}", e)
            -1L
        }
    }
    private fun loadSimilarBooksSections() {
        if (_binding == null || !::similarTo1Adapter.isInitialized || !::similarTo2Adapter.isInitialized) {
            Log.w("SimilarBooks", "Binding null or Adapters for similar books not ready.")
            _binding?.let {
                hideSection(it.tvSimilarTo1, it.rvSimilarTo1)
                hideSection(it.tvSimilarTo2, it.rvSimilarTo2)
            }
            return
        }
        val userId = auth.currentUser?.uid ?: run {
            Log.w("SimilarBooks", "User not logged in for Similar Books.")
            hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
            hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
            return
        }

        hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
        hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)

        uiScope.launch {
            try {
                Log.d("SimilarBooks", "Finding 5-star ratings for user $userId")
                val fiveStarQuery = firestore.collection("user_ratings")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("rating", 5.0)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(MAX_SIMILAR_SECTIONS.toLong() * 2)
                    .get().await()
                if (!isActive) return@launch

                Log.d("SimilarBooks", "5-star query completed. Found ${fiveStarQuery.size()} documents.")
                val fiveStarBookIds = fiveStarQuery.documents.mapNotNull { doc ->
                    doc.getLong("bookId")
                }.distinct().take(MAX_SIMILAR_SECTIONS)

                if (!isActive || _binding == null || fiveStarBookIds.isEmpty()) {
                    Log.d("SimilarBooks", "No 5-star book IDs found or coroutine/binding invalid.")
                    hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
                    hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
                    return@launch
                }
                Log.d("SimilarBooks", "Found 5-star book IDs (Long): $fiveStarBookIds")

                val originalBooksDetails = fetchBookDetailsByIdsInternal(fiveStarBookIds.map { it.toInt() })
                if (!isActive || _binding == null || originalBooksDetails.isEmpty()) {
                    Log.d("SimilarBooks", "Could not fetch details for 5-star books. IDs: $fiveStarBookIds")
                    hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
                    hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
                    return@launch
                }
                Log.d("SimilarBooks", "Fetched details for original 5-star books: ${originalBooksDetails.map { it.title }}")

                originalBookTitle1 = originalBooksDetails.getOrNull(0)?.title
                originalBookTitle2 = originalBooksDetails.getOrNull(1)?.title
                Log.d("SimilarBooks", "Stored original titles. T1: $originalBookTitle1, T2: $originalBookTitle2")

                loadSimilarBooksForIndex(0, originalBooksDetails.mapNotNull { it.book_id })

            } catch (ce: CancellationException) {
                Log.i("SimilarBooks", "loadSimilarBooksSections coroutine was cancelled.")
            } catch (e: Exception) {
                Log.e("SimilarBooks", "Error finding/fetching 5-star rated books: ${e.message}", e)
                if (isActive && _binding != null) {
                    hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
                    hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
                }
            }
        }
    }
    private fun loadSimilarBooksForIndex(index: Int, originalBookIds: List<Int>) {
        if (_binding == null || index >= MAX_SIMILAR_SECTIONS || index >= originalBookIds.size) {
            return
        }

        val originalBookId = originalBookIds[index]

        val components = when (index) {
            0 -> SimilarSectionComponents(isSimilarTo1Loading, {b -> isSimilarTo1Loading=b}, similarTo1Books, if(::similarTo1Adapter.isInitialized) similarTo1Adapter else null, binding.tvSimilarTo1, binding.rvSimilarTo1, originalBookTitle1)
            1 -> SimilarSectionComponents(isSimilarTo2Loading, {b -> isSimilarTo2Loading=b}, similarTo2Books, if(::similarTo2Adapter.isInitialized) similarTo2Adapter else null, binding.tvSimilarTo2, binding.rvSimilarTo2, originalBookTitle2)
            else -> { loadSimilarBooksForIndex(index + 1, originalBookIds); return }
        }

        if (components.isLoading || components.adapter == null) {
            Log.d("SimilarBooksSeq", "Section $index for original book ID $originalBookId is already loading or adapter is null.")
            components.setLoadingFlag(false)
            if(components.adapter == null && _binding != null) hideSection(components.textView, components.recyclerView)
            if (!components.isLoading && _binding != null) uiScope.launch { loadSimilarBooksForIndex(index + 1, originalBookIds) }
            return
        }

        components.setLoadingFlag(true)
        components.targetList.clear()
        components.adapter.notifyDataSetChanged()
        hideSection(components.textView, components.recyclerView)
        val sectionDisplayTitle = "Similar to ${components.originalTitle ?: "Book ID $originalBookId"}"
        val sectionLogPrefix = "SimilarBooks[Idx:$index,OrigID:$originalBookId]"
        Log.d(sectionLogPrefix, "Loading similar books for: ${components.originalTitle ?: originalBookId}")

        uiScope.launch {
            var bookIdsToLoad: List<Long>? = null
            var triggerApi = false
            try {
                Log.d(sectionLogPrefix, "Querying 'similar_books_cache' where book_id == ${originalBookId.toLong()}")
                val cacheQuery = firestore.collection("similar_books_cache")
                    .whereEqualTo("book_id", originalBookId.toLong())
                    .limit(1).get().await()
                if (!isActive) return@launch

                Log.d(sectionLogPrefix, "Cache query completed. Found ${cacheQuery.size()} documents.")
                val cachedData = if (!cacheQuery.isEmpty) try { cacheQuery.documents[0].toObject<SimilarBooksResult>() } catch (e:Exception){ null } else null

                if (cachedData?.recommendations.isNullOrEmpty()) {
                    triggerApi = true
                    Log.d(sectionLogPrefix, "Cache miss or recommendations empty. Triggering API.")
                } else {
                    if (cachedData != null) {
                        bookIdsToLoad = cachedData.recommendations?.mapNotNull { it.book_id }
                    }
                    Log.d(sectionLogPrefix, "Using fresh cache. Book IDs (Long): $bookIdsToLoad")
                }

                if (triggerApi) {
                    Log.d(sectionLogPrefix, "Triggering /similar-books API for book ID $originalBookId...")
                    try {
                        withContext(Dispatchers.IO) {
                            RetrofitInstance.api.getSimilarBooks(
                                bookId = originalBookId,
                                topN = 10,
                                wLatent = 0.6, wGenre = 0.25, wAuthor = 0.1, wRating = 0.05
                            )
                        }
                        Log.d(sectionLogPrefix, "API for getSimilarBooks triggered. Waiting for Firestore update...")
                        delay(3000)

                        val updatedQuery = firestore.collection("similar_books_cache")
                            .whereEqualTo("book_id", originalBookId.toLong())
                            .limit(1).get().await()
                        if (!isActive) return@launch
                        val updatedCacheData = if (!updatedQuery.isEmpty) try { updatedQuery.documents[0].toObject<SimilarBooksResult>() } catch (e:Exception){ null } else null
                        bookIdsToLoad = updatedCacheData?.recommendations?.mapNotNull { it.book_id }
                        Log.d(sectionLogPrefix, "Cache re-read. Updated Book IDs (Long): $bookIdsToLoad")
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(sectionLogPrefix, "Error in API call or subsequent cache read: ${e.message}", e)
                        bookIdsToLoad = null
                    }
                }

                if (!isActive || _binding == null) return@launch

                if (!bookIdsToLoad.isNullOrEmpty()) {
                    val filteredBookIdsToInt = bookIdsToLoad
                        .filter { it.toInt() != originalBookId }
                        .map { it.toInt() }

                    if (filteredBookIdsToInt.isNotEmpty()) {
                        fetchBookDetailsByIds(
                            bookIds = filteredBookIdsToInt, targetList = components.targetList,
                            targetAdapter = components.adapter,
                            targetTextView = components.textView, targetRecyclerView = components.recyclerView,
                            sectionTitle = sectionDisplayTitle, setLoadingFlag = components.setLoadingFlag,
                            limit = 10
                        )
                    } else {
                        components.setLoadingFlag(true)
                        hideSection(components.textView, components.recyclerView)
                        Log.d(sectionLogPrefix, "No similar (and different) books found.")
                    }
                } else {
                    components.setLoadingFlag(true)
                    hideSection(components.textView, components.recyclerView)
                    Log.d(sectionLogPrefix, "No book IDs to load for this similar section.")
                }
            } catch (ce: CancellationException) {
                Log.i(sectionLogPrefix, "Coroutine cancelled.")
                components.setLoadingFlag(true)
            } catch (e: Exception) {
                Log.e(sectionLogPrefix, "Error: ${e.message}", e)
                components.setLoadingFlag(true)
                if(isActive && _binding != null) hideSection(components.textView, components.recyclerView)
            } finally {
                if (isActive && _binding != null) {
                    loadSimilarBooksForIndex(index + 1, originalBookIds)
                }
            }
        }
    }
    private fun fetchBookDetailsByIds(
        bookIds: List<Int>,
        targetList: ArrayList<Books>,
        targetAdapter: BookCategoryAdapter,
        targetTextView: TextView,
        targetRecyclerView: RecyclerView,
        sectionTitle: String,
        setLoadingFlag: (Boolean) -> Unit,
        limit: Int = categoryBooksPerPage
    ) {
        val effectiveLogSection = sectionTitle.take(20)
        Log.d("FetchDetails[$effectiveLogSection]", "Fetching for '$sectionTitle' with ${bookIds.size} IDs (display limit: $limit)")

        if (bookIds.isEmpty()) {
            Log.w("FetchDetails", "Book ID list empty for '$sectionTitle'. Hiding section.")
            if (_binding != null && isAdded) hideSection(targetTextView, targetRecyclerView)
            setLoadingFlag(false)
            return
        }

        val idsToDisplay = bookIds.take(limit)
        Log.d("FetchDetails[$effectiveLogSection]", "IDs to query from Firestore (up to 30 per chunk, for display up to $limit): ${idsToDisplay.size}")


        val queryChunks = idsToDisplay.chunked(30)
        val allFetchedBooks = mutableListOf<Books>()
        var completedChunks = 0
        val totalChunks = queryChunks.size

        if (queryChunks.isEmpty()){
            Log.w("FetchDetails", "No chunks to process for '$sectionTitle'.")
            if (_binding != null && isAdded) hideSection(targetTextView, targetRecyclerView)
            setLoadingFlag(false)
            return
        }

        queryChunks.forEachIndexed { chunkIndex, chunkOfIds ->
            if (chunkOfIds.isEmpty()) {
                completedChunks++
                if (completedChunks == totalChunks) {
                    if (_binding != null && isAdded) {
                        handleFetchedBookResults(allFetchedBooks, targetList, targetAdapter, targetTextView, targetRecyclerView, sectionTitle, setLoadingFlag, effectiveLogSection)
                    } else {
                        setLoadingFlag(false)
                    }
                }
                return@forEachIndexed
            }
            Log.d("FetchDetails[$effectiveLogSection]", "Fetching chunk ${chunkIndex + 1}/$totalChunks with ${chunkOfIds.size} IDs.")
            firestore.collection("books_data")
                .whereIn("book_id", chunkOfIds)
                .get()
                .addOnSuccessListener { documents ->
                    if (_binding == null || !isAdded) {
                        Log.w("FetchDetails", "View gone or fragment detached while fetching for $sectionTitle.")
                        completedChunks++
                        if (completedChunks == totalChunks) setLoadingFlag(false)
                        return@addOnSuccessListener
                    }
                    Log.d("FetchDetails[$effectiveLogSection]", "Firestore query success for chunk ${chunkIndex + 1}. Found ${documents.size()} raw documents.")
                    val fetchedBooksInChunk = documents.mapNotNull { parseDocumentToBook(it) }
                    allFetchedBooks.addAll(fetchedBooksInChunk)
                    completedChunks++

                    if (completedChunks == totalChunks) {
                        handleFetchedBookResults(allFetchedBooks, targetList, targetAdapter, targetTextView, targetRecyclerView, sectionTitle, setLoadingFlag, effectiveLogSection)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("FetchDetails[$effectiveLogSection]", "Error fetching details chunk ${chunkIndex + 1} for '$sectionTitle': ${exception.message}", exception)
                    completedChunks++
                    if (completedChunks == totalChunks) {
                        if (_binding != null && isAdded) {
                            handleFetchedBookResults(allFetchedBooks, targetList, targetAdapter, targetTextView, targetRecyclerView, sectionTitle, setLoadingFlag, effectiveLogSection)
                        } else {
                            setLoadingFlag(false)
                        }
                    }
                }
        }
    }
    private fun handleFetchedBookResults(
        fetchedBooks: List<Books>, targetList: ArrayList<Books>, targetAdapter: BookCategoryAdapter,
        targetTextView: TextView, targetRecyclerView: RecyclerView, sectionTitle: String,
        setLoadingFlag: (Boolean) -> Unit, logSection: String
    ) {
        if (_binding == null || !isAdded) {
            Log.w("HandleFetchResults", "View gone or fragment detached for $sectionTitle.")
            setLoadingFlag(false)
            return
        }
        Log.d("FetchDetails[$logSection]", "All chunks processed. Total ${fetchedBooks.size} valid books parsed for '$sectionTitle'.")
        targetList.clear()
        if (fetchedBooks.isNotEmpty()) {
            targetList.addAll(fetchedBooks)
            targetTextView.text = sectionTitle
            Log.d("FetchDetails[$logSection]", "Showing section for '$sectionTitle' with ${targetList.size} books.")
            showSection(targetTextView, targetRecyclerView)
        } else {
            Log.d("FetchDetails[$logSection]", "No valid book details found for '$sectionTitle' after all chunks. Hiding section.")
            hideSection(targetTextView, targetRecyclerView)
        }
        targetAdapter.notifyDataSetChanged()
        setLoadingFlag(false)
    }
    private suspend fun fetchBookDetailsByIdsInternal(bookIds: List<Int>): List<Books> {
        if (bookIds.isEmpty()) {
            Log.d("FetchInternal", "Input bookId list is empty.")
            return emptyList()
        }
        val allFetchedBooks = mutableListOf<Books>()
        val queryChunks = bookIds.chunked(30)
        Log.d("FetchInternal", "Fetching details for ${bookIds.size} IDs in ${queryChunks.size} chunks.")

        return try {
            for ((index, chunk) in queryChunks.withIndex()) {
                if (chunk.isEmpty()) continue
                Log.d("FetchInternal", "Querying 'books_data' for chunk ${index + 1}/${queryChunks.size}, IDs: $chunk")
                val documents = firestore.collection("books_data")
                    .whereIn("book_id", chunk)
                    .get().await()
                Log.d("FetchInternal", "Chunk ${index + 1} returned ${documents.size()} documents.")
                val parsedBooksInChunk = documents.mapNotNull { parseDocumentToBook(it) }
                allFetchedBooks.addAll(parsedBooksInChunk)
            }
            Log.d("FetchInternal", "Total parsed ${allFetchedBooks.size} books from all chunks.")
            allFetchedBooks
        } catch (e: Exception) {
            Log.e("FetchInternal", "Error fetching internal details: ${e.message}", e)
            emptyList()
        }
    }
    private fun switchToSearchMode() {
        if (_binding == null || isInSearchMode) return
        isInSearchMode = true
        binding.viewPagerCarousel.visibility = View.GONE
        binding.scrollViewCategories.visibility = View.GONE
        binding.bookRV.visibility = View.VISIBLE
        try { binding.filterButton.visibility = View.VISIBLE } catch (e: Exception) { /* ignore */ }
    }
    private fun switchToCategoriesMode() {
        if (_binding == null || !isInSearchMode) return
        isInSearchMode = false
        binding.searchMainpage.setQuery("", false)
        binding.searchMainpage.clearFocus()
        if (carouselBookList.isNotEmpty()) binding.viewPagerCarousel.visibility = View.VISIBLE
        binding.scrollViewCategories.visibility = View.VISIBLE
        binding.bookRV.visibility = View.GONE
        try { binding.filterButton.visibility = View.GONE } catch (e: Exception) { /* ignore */ }
        clearSearchResultsAndFilters()
        binding.tvNoResultsSearch?.visibility = View.GONE
    }
    private fun clearSearchResults() {
        if (searchBookList.isNotEmpty()) {
            val previousSize = searchBookList.size
            searchBookList.clear()
            if (::searchAdapter.isInitialized) searchAdapter.notifyItemRangeRemoved(0, previousSize)
        }
        lastVisibleSearchDocument = null
        isSearchLastPage = false
    }
    private fun clearSearchResultsAndFilters() {
        clearSearchResults()
        currentFilters = BookFilters()
        searchQuery = null
    }
    private fun searchBooks(queryText: String?, filters: BookFilters) {
        if (_binding == null) {
            isSearchLoading = false
            binding?.progressBar?.visibility = View.GONE
            return
        }
        isSearchLoading = true

        val effectiveQueryText = queryText?.lowercase()?.trim()
        var firestoreQuery: Query = firestore.collection("books_data")
        var applyClientSideTextFilter = false

        if (filters.selectedGenres.isNotEmpty()) {
            firestoreQuery = firestoreQuery.whereArrayContainsAny("genres", filters.selectedGenres.take(10))
        }
        filters.publicationYear?.let {
            firestoreQuery = firestoreQuery.whereEqualTo("publication_year", it)
        }
        filters.minRating?.let {
            firestoreQuery = firestoreQuery.whereGreaterThanOrEqualTo("average_rating", it)
        }

        val canServerSideTextSearch = !effectiveQueryText.isNullOrBlank() && filters.minRating == null

        if (canServerSideTextSearch) {
            firestoreQuery = firestoreQuery.orderBy("title_lowercase")
            when (filters.sortBy) {
                SortOption.RELEVANCE, null -> firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
                SortOption.RATING_DESC -> {
                    firestoreQuery = firestoreQuery.orderBy("average_rating", Query.Direction.DESCENDING)
                    firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
                }
                SortOption.PUBLICATION_YEAR_DESC -> {
                    firestoreQuery = firestoreQuery.orderBy("publication_year", Query.Direction.DESCENDING)
                    firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
                }
            }
        } else {
            if (!effectiveQueryText.isNullOrBlank()) {
                applyClientSideTextFilter = true
            }
            if (filters.minRating != null) {
                firestoreQuery = firestoreQuery.orderBy("average_rating", Query.Direction.DESCENDING)
                when (filters.sortBy) {
                    SortOption.RELEVANCE, SortOption.RATING_DESC, null ->
                        firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
                    SortOption.PUBLICATION_YEAR_DESC -> {
                        firestoreQuery = firestoreQuery.orderBy("publication_year", Query.Direction.DESCENDING)
                        firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
                    }
                }
            } else {
                val effectiveSortBy = filters.sortBy ?: if (applyClientSideTextFilter) SortOption.RELEVANCE else SortOption.RATING_DESC
                when (effectiveSortBy) {
                    SortOption.RATING_DESC -> {
                        firestoreQuery = firestoreQuery.orderBy("average_rating", Query.Direction.DESCENDING)
                        firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
                    }
                    SortOption.PUBLICATION_YEAR_DESC -> {
                        firestoreQuery = firestoreQuery.orderBy("publication_year", Query.Direction.DESCENDING)
                        firestoreQuery = firestoreQuery.orderBy("average_rating", Query.Direction.DESCENDING)
                        firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
                    }
                    SortOption.RELEVANCE -> {
                        firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
                        firestoreQuery = firestoreQuery.orderBy("average_rating", Query.Direction.DESCENDING)
                    }
                }
            }
        }

        if (canServerSideTextSearch) {
            firestoreQuery = firestoreQuery.startAt(effectiveQueryText).endAt(effectiveQueryText!! + "\uf8ff")
        }

        lastVisibleSearchDocument?.let {
            firestoreQuery = firestoreQuery.startAfter(it)
        }

        firestoreQuery = firestoreQuery.limit(booksPerPage.toLong())

        firestoreQuery.get()
            .addOnSuccessListener { documents ->
                if (_binding == null || !isAdded) {
                    isSearchLoading = false; return@addOnSuccessListener
                }
                var newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                val rawFetchedCount = documents.size()
                if (applyClientSideTextFilter && !effectiveQueryText.isNullOrBlank()) {
                    newBooks = newBooks.filter { book -> book.title_lowercase.contains(effectiveQueryText) }
                }
                isSearchLoading = false
                binding.progressBar.visibility = View.GONE
                if (documents.isEmpty) {
                    isSearchLastPage = true
                } else {
                    lastVisibleSearchDocument = documents.documents.last()
                    isSearchLastPage = if (applyClientSideTextFilter && newBooks.isEmpty() && rawFetchedCount.toLong() == booksPerPage.toLong()) {
                        false
                    } else {
                        rawFetchedCount < booksPerPage
                    }
                }
                if (newBooks.isNotEmpty()) {
                    val currentSize = searchBookList.size
                    searchBookList.addAll(newBooks)
                    searchAdapter.notifyItemRangeInserted(currentSize, newBooks.size)
                }
                if (searchBookList.isEmpty()) {
                    binding.tvNoResultsSearch?.text = "No books found matching your criteria."
                    binding.tvNoResultsSearch?.visibility = View.VISIBLE
                } else {
                    binding.tvNoResultsSearch?.visibility = View.GONE
                }
            }
            .addOnFailureListener { exception ->
                Log.e("MainPageSearch", "Error searching: ${exception.message}", exception)
                isSearchLoading = false
                if (_binding != null && isAdded) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Search error: ${exception.message}", Toast.LENGTH_LONG).show()
                    binding.tvNoResultsSearch?.text = "Error during search."
                    binding.tvNoResultsSearch?.visibility = View.VISIBLE
                }
            }
    }
    @Suppress("UNCHECKED_CAST")
    private fun parseDocumentToBook(document: DocumentSnapshot): Books? {
        try {
            val title = safeGetString(document.get("title"))
            val bookIdValue = document.get("book_id")
            val bookId = when (bookIdValue) {
                is Number -> bookIdValue.toInt()
                else -> 0
            }

            if (title.isBlank() || bookId == 0) {
                Log.w("ParseBook", "Skipping doc ${document.id}: Missing title or valid book_id. Title: '$title', ID: $bookId")
                return null
            }
            val titleLowercaseFromDoc = safeGetString(document.get("title_lowercase"))

            return Books(
                authors = convertToInt(document.get("authors")),
                author_name = safeGetString(document.get("author_name")),
                average_rating = convertToDouble(document.get("average_rating")),
                book_id = bookId,
                description = safeGetString(document.get("description")),
                format = safeGetString(document.get("format")),
                genres = (document.get("genres") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                imageUrl = safeGetString(document.get("image_url")),
                is_ebook = document.getBoolean("is_ebook") ?: false,
                isbn = safeGetString(document.get("isbn")),
                isbn13 = safeGetString(document.get("isbn13")),
                kindle_asin = safeGetString(document.get("kindle_asin")),
                language_code = safeGetString(document.get("language_code")),
                num_pages = convertToInt(document.get("num_pages")),
                publication_day = (document.get("publication_day") as? List<*>)?.mapNotNull { it?.toString() } ?:
                (safeGetString(document.get("publication_day")).takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()),
                publication_month = convertToInt(document.get("publication_month")),
                publication_year = convertToInt(document.get("publication_year")),
                publisher = safeGetString(document.get("publisher")),
                title = title,
                rating_count = convertToInt(document.get("rating_count")),
                title_lowercase = if (titleLowercaseFromDoc.isNotBlank()) titleLowercaseFromDoc else title.lowercase()
            )
        } catch (e: Exception) {
            Log.e("ParseBook", "Critical error parsing doc ${document.id}: ${e.message}", e)
            return null
        }
    }
    private fun safeGetString(value: Any?): String {
        return when (value) {
            is String -> value
            is Number -> value.toString()
            else -> ""
        }
    }
    private fun convertToInt(value: Any?): Int {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
    }
    private fun convertToDouble(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}