package com.example.bookgenie

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
// import android.widget.ImageButton // Zaten import edilmiş olmalı
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
// import androidx.navigation.Navigation // Kullanılmıyorsa kaldırılabilir
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// import androidx.recyclerview.widget.StaggeredGridLayoutManager // Kullanılmıyorsa kaldırılabilir
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.example.bookgenie.api.RetrofitInstance
import com.example.bookgenie.databinding.FragmentMainPageBinding
// import com.google.android.material.bottomnavigation.BottomNavigationView // Kullanılmıyorsa kaldırılabilir
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
// import com.google.firebase.firestore.SetOptions // Kullanılmıyorsa kaldırılabilir
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// Data class'lar (RecommendationEntry, UserRecommendations, SimilarBooksResult) aynı kalır.
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

// Helper data class for loadRandomGenreBooks
private data class GenreSectionComponents(
    val adapter: BookCategoryAdapter?,
    val bookList: ArrayList<Books>,
    val lastVisibleDoc: DocumentSnapshot?,
    val setIsLoading: (Boolean) -> Unit,
    val getIsLoading: () -> Boolean,
    val textView: TextView,
    val recyclerView: RecyclerView
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

class MainPageFragment : Fragment(), FilterBottomSheetDialogFragment.FilterDialogListener { // Listener'ı implemente et
    private var _binding: FragmentMainPageBinding? = null // View binding için nullable yapıldı
    private val binding get() = _binding!! // ve non-null assertion ile erişim

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private val fragmentJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + fragmentJob)

    private val booksPerPage = 10 // Arama ve kategori sayfalama için genel limit

    // --- Carousel ---
    private lateinit var carouselAdapter: CarouselBookAdapter
    private val carouselBookList = ArrayList<Books>()
    private var carouselScrollJob: Job? = null
    private val CAROUSEL_AUTO_SCROLL_DELAY_MS = 5000L
    private val CAROUSEL_FETCH_LIMIT = 20L
    private val CAROUSEL_DISPLAY_COUNT = 7
    private var isCarouselManuallyPaused: Boolean = false

    // --- Search Functionality ---
    private lateinit var searchAdapter: BookAdapter
    private val searchBookList = ArrayList<Books>()
    private var lastVisibleSearchDocument: DocumentSnapshot? = null
    private var isSearchLoading = false
    private var isSearchLastPage = false
    private var searchQuery: String? = null
    private var isInSearchMode = false
    // private var searchJob: Job? = null // currentSearchOperationJob ile birleştirildi
    private var currentFilters: BookFilters = BookFilters() // Aktif filtreleri tut

    // --- Category Adapters and Data ---
    private val availableGenres = listOf(
        "Adventure", "Biography", "Classics", "Contemporary", "Fantasy", "Fiction",
        "Historical", "Historical Fiction", "History", "Horror", "Mystery",
        "Nonfiction", "Paranormal", "Poetry", "Romance", "Science Fiction",
        "Thriller", "Young Adult"
    ).distinct().sorted() // Tekrarları kaldır ve sırala

    private lateinit var selectedRandomGenres: List<String>
    private val categoryBooksPerPage = 7 // Kategoriler için farklı bir limit

    private lateinit var topRatedByCountAdapter: BookCategoryAdapter
    private val topRatedByCountBooks = ArrayList<Books>()
    private var lastVisibleTopRatedByCount: DocumentSnapshot? = null
    private var isTopRatedByCountLoading = false

    private lateinit var highRatedFictionAdapter: BookCategoryAdapter
    private val highRatedFictionBooks = ArrayList<Books>()
    private var lastVisibleHighRatedFiction: DocumentSnapshot? = null
    private var isHighRatedFictionLoading = false

    private lateinit var highRatedNonFictionAdapter: BookCategoryAdapter
    private val highRatedNonFictionBooks = ArrayList<Books>()
    private var lastVisibleHighRatedNonFiction: DocumentSnapshot? = null
    private var isHighRatedNonFictionLoading = false

    private lateinit var randomGenre1Adapter: BookCategoryAdapter
    private val randomGenre1Books = ArrayList<Books>()
    private var lastVisibleRandomGenre1: DocumentSnapshot? = null
    private var isRandomGenre1Loading = false

    private lateinit var randomGenre2Adapter: BookCategoryAdapter
    private val randomGenre2Books = ArrayList<Books>()
    private var lastVisibleRandomGenre2: DocumentSnapshot? = null
    private var isRandomGenre2Loading = false

    private lateinit var randomGenre3Adapter: BookCategoryAdapter
    private val randomGenre3Books = ArrayList<Books>()
    private var lastVisibleRandomGenre3: DocumentSnapshot? = null
    private var isRandomGenre3Loading = false

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


    private var currentSearchOperationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        firestore = Firebase.firestore
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainPageBinding.inflate(inflater, container, false) // _binding'e ata
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectRandomGenres()
        setupCarousel()
        setupSearchView()
        setupBackPressHandler()
        setupRecyclerViews()

        // Veri yükleme işlemleri
        loadCarouselBooks()
        loadTopRatedByCountBooks() // Başlangıçta ilk sayfayı yükle
        loadHighRatedFictionBooks()
        loadHighRatedNonFictionBooks()
        loadRandomGenreSections()
        loadForYouRecommendations()
        loadSimilarBooksSections()

        try {
            binding.filterButton.visibility = View.GONE // Başlangıçta filtre butonu gizli
        } catch (e: Exception) {
            // Bu genellikle binding.filterButton null olduğunda olur, yani XML'de ID yanlışsa veya buton yoksa.
            // XML dosyasında 'filterButton' ID'li bir ImageButton olduğundan emin olun.
            Log.e("FilterButtonInit", "Filter button with ID 'filterButton' not found in layout. Please add it. ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isCarouselManuallyPaused && carouselBookList.isNotEmpty()) { // Sadece kitap varsa ve manuel duraklatılmadıysa başlat
            startCarouselAutoScroll()
        }
    }

    override fun onPause() {
        super.onPause()
        // isCarouselManuallyPaused = false // onPause'da manuel duraklatmayı sıfırlama, kullanıcı etkileşimine bağlı kalsın
        stopCarouselAutoScrollInternally() // Oto-kaydırmayı her zaman durdur
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fragmentJob.cancel() // Coroutine job'larını iptal et
        currentSearchOperationJob?.cancel()
        stopCarouselAutoScrollInternally() // Carousel job'ını da iptal et
        _binding = null // View binding referansını temizle
    }

    private fun setupCarousel() {
        // Ensure binding is not null before accessing its properties
        if (_binding == null) {
            Log.e("SetupCarousel", "Binding is null. Cannot setup carousel.")
            return
        }
        carouselAdapter = CarouselBookAdapter(requireContext(), carouselBookList)
        binding.viewPagerCarousel.adapter = carouselAdapter
        binding.viewPagerCarousel.offscreenPageLimit = 3 // Daha akıcı geçişler için
        binding.viewPagerCarousel.clipToPadding = false
        binding.viewPagerCarousel.clipChildren = false
        // Kenar boşlukları ve ölçekleme efekti
        val compositePageTransformer = CompositePageTransformer()
        // Define R.dimen.carousel_margin in your res/values/dimens.xml
        // (e.g., <dimen name="carousel_margin">40dp</dimen>)
        val carouselMargin = try {
            resources.getDimensionPixelOffset(R.dimen.carousel_margin)
        } catch (e: android.content.res.Resources.NotFoundException) {
            Log.w("SetupCarousel", "R.dimen.carousel_margin not found. Using default 40px (converted from dp). Define it in dimens.xml.")
            (40 * resources.displayMetrics.density).toInt() // Convert 40dp to pixels as a fallback
        }
        compositePageTransformer.addTransformer(MarginPageTransformer(carouselMargin))
        compositePageTransformer.addTransformer { page, position ->
            val r = 1 - abs(position)
            page.scaleY = 0.85f + r * 0.15f
            page.scaleX = 0.85f + r * 0.10f // Genişlik için de hafif ölçekleme
            page.alpha = 0.5f + r * 0.5f // Ortadakini daha belirgin yap
        }
        binding.viewPagerCarousel.setPageTransformer(compositePageTransformer)

        // Kullanıcı etkileşiminde oto-kaydırmayı yönet
        binding.viewPagerCarousel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        if (carouselScrollJob?.isActive == true) {
                            isCarouselManuallyPaused = true
                            stopCarouselAutoScrollInternally()
                            Log.d("Carousel", "User dragging. Auto-scroll paused.")
                        }
                    }
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        if (isCarouselManuallyPaused) {
                            // Kullanıcı sürüklemeyi bitirdi, bir süre sonra otomatik kaydırmayı yeniden başlat
                            uiScope.launch {
                                delay(CAROUSEL_AUTO_SCROLL_DELAY_MS) // Tam bekleme süresi
                                if (isActive && isCarouselManuallyPaused && isResumed) { // Hala manuel duraklatılmışsa ve fragment resumed ise
                                    isCarouselManuallyPaused = false
                                    startCarouselAutoScroll()
                                    Log.d("Carousel", "User interaction ended. Auto-scroll resumed after delay.")
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    private fun selectRandomGenres() {
        selectedRandomGenres = availableGenres.shuffled().take(3)
        Log.d("Genres", "Selected random genres: $selectedRandomGenres")
    }

    private fun setupSearchView() {
        if (_binding == null) return
        val searchView = binding.searchMainpage
        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        try {
            // Renkleri tema attributeları ile veya R.color ile ayarlayın
            searchEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black)) // Örnek renk
            // Define R.color.gray_500 in your res/values/colors.xml
            // (e.g., <color name="gray_500">#9E9E9E</color>)
            val hintColor = try {
                ContextCompat.getColor(requireContext(), R.color.siyahimsi)
            } catch (e: android.content.res.Resources.NotFoundException) {
                Log.w("SetupSearch", "R.color.gray_500 not found. Using fallback android.R.color.darker_gray. Define it in colors.xml.")
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
                currentSearchOperationJob?.cancel() // Önceki arama işlemini iptal et
                searchQuery = newText.trim()
                if (searchQuery!!.isBlank() && currentFilters == BookFilters()) { // Hem sorgu boş hem de filtre yoksa
                    if (isInSearchMode) {
                        clearSearchResults() // Sadece sonuçları temizle, filtreleri değil
                        // Arama modunda kal, "Aramak için yazın" gibi bir placeholder gösterilebilir
                    }
                } else { // Sorgu dolu veya filtre var
                    if (!isInSearchMode) {
                        switchToSearchMode()
                    }
                    // Debounce ile arama
                    currentSearchOperationJob = uiScope.launch {
                        delay(400) // Kullanıcı yazmayı bitirene kadar bekle (debounce)
                        if (isActive) { // Coroutine hala aktifse aramayı yap
                            performSearch()
                        }
                    }
                }
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                currentSearchOperationJob?.cancel() // Debounce'u iptal et (varsa)
                searchView.clearFocus() // Klavyeyi gizle
                searchQuery = query.trim()
                if (searchQuery!!.isNotBlank() || currentFilters != BookFilters()) { // Sorgu dolu veya filtre varsa
                    if (!isInSearchMode) {
                        switchToSearchMode()
                    }
                    performSearch() // Submit edildiğinde hemen ara
                }
                return true
            }
        })

        try {
            binding.filterButton.setOnClickListener {
                if (isInSearchMode) {
                    // childFragmentManager, bir Fragment içindeki FragmentManager'dır.
                    val filterDialog = FilterBottomSheetDialogFragment.newInstance(availableGenres, currentFilters)
                    filterDialog.show(childFragmentManager, FilterBottomSheetDialogFragment.TAG)
                }
            }
        } catch (e: Exception) {
            Log.e("FilterButton", "Filter button setup failed. Ensure it has ID 'filterButton' in XML. ${e.message}")
        }
    }

    // FilterBottomSheetDialogFragment.FilterDialogListener implementasyonu
    override fun onFiltersApplied(filters: BookFilters) {
        currentFilters = filters
        Log.d("MainPageFragment", "Filters applied from dialog: $filters")
        if (isInSearchMode) {
            performSearch() // Filtreler değiştiğinde aramayı yeniden yap
        }
    }

    // Arama işlemini merkezi bir yerden çağırmak için
    private fun performSearch() {
        if (_binding == null) {
            Log.w("PerformSearch", "Binding is null, cannot perform search.")
            return
        }
        clearSearchResults() // Her yeni arama/filtreleme öncesi eski sonuçları temizle
        // searchQuery boş olsa bile filtreler uygulanabilir (tüm kitaplar arasında filtreleme)
        if (searchQuery.isNullOrBlank() && currentFilters == BookFilters()) {
            Log.d("MainPageSearch", "Search query and filters are empty. Not performing search.")
            // İsteğe bağlı: Kullanıcıya bir mesaj gösterilebilir.
            // binding.tvNoResultsSearch.visibility = View.VISIBLE // Eğer böyle bir TextView varsa
            return
        }
        // binding.tvNoResultsSearch.visibility = View.GONE // Eğer varsa, sonuçlar yüklenirken gizle
        searchBooks(searchQuery, currentFilters)
    }


    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, // viewLifecycleOwner kullanmak daha güvenli
            object : OnBackPressedCallback(true) { // true -> başlangıçta aktif
                override fun handleOnBackPressed() {
                    if (isInSearchMode) {
                        switchToCategoriesMode()
                    } else {
                        // Eğer bu fragment back stack'in en altındaysa ve popBackStack() bir şey yapmıyorsa,
                        // aktiviteyi kapatmayı veya kullanıcıya bir uyarı vermeyi düşünebilirsiniz.
                        isEnabled = false // Callback'i devre dışı bırak
                        try {
                            if (!findNavController().popBackStack()) {
                                // Eğer pop edilecek bir şey yoksa (bu fragment backstack'in başındaysa)
                                // requireActivity().finish() // Aktiviteyi kapat
                            }
                        } catch (e: IllegalStateException) {
                            Log.e("BackPress", "Navigation controller not found or not attached: ${e.message}")
                            // requireActivity().finish() // Alternatif olarak aktiviteyi sonlandır
                        }
                    }
                }
            }
        )
    }

    private fun setupRecyclerViews() {
        if (_binding == null) return
        // Search RecyclerView
        binding.bookRV.layoutManager = LinearLayoutManager(requireContext())
        // BookAdapter'ın ikinci parametresi olan "main_search_results" string'i,
        // muhtemelen bir item layout referansıdır. Bu layout'un tek sütunlu liste için
        // uygun olduğundan emin olun. (örn: R.layout.item_book_search_result)
        searchAdapter = BookAdapter(requireContext(), searchBookList, "main_search_results") // "main" yerine arama sonuçları için farklı bir layout olabilir
        binding.bookRV.adapter = searchAdapter
        setupSearchScrollListener()

        // Category RecyclerViews & Adapters
        setupCategoryRecyclerView(binding.rvTopRatedByCountBooks)
        topRatedByCountAdapter = BookCategoryAdapter(requireContext(), topRatedByCountBooks) { loadTopRatedByCountBooks(true) }
        binding.rvTopRatedByCountBooks.adapter = topRatedByCountAdapter
        hideSection(binding.tvTopRatedByCountBooks, binding.rvTopRatedByCountBooks) // Başlangıçta gizle

        setupCategoryRecyclerView(binding.rvHighRatedFictionBooks)
        highRatedFictionAdapter = BookCategoryAdapter(requireContext(), highRatedFictionBooks) { loadHighRatedFictionBooks(true) }
        binding.rvHighRatedFictionBooks.adapter = highRatedFictionAdapter
        hideSection(binding.tvHighRatedFictionBooks, binding.rvHighRatedFictionBooks)

        setupCategoryRecyclerView(binding.rvHighRatedNonFictionBooks)
        highRatedNonFictionAdapter = BookCategoryAdapter(requireContext(), highRatedNonFictionBooks) { loadHighRatedNonFictionBooks(true) }
        binding.rvHighRatedNonFictionBooks.adapter = highRatedNonFictionAdapter
        hideSection(binding.tvHighRatedNonFictionBooks, binding.rvHighRatedNonFictionBooks)

        // Random Genre Adapters
        setupCategoryRecyclerView(binding.rvRandomGenre1)
        randomGenre1Adapter = BookCategoryAdapter(requireContext(), randomGenre1Books) { if (selectedRandomGenres.isNotEmpty()) loadRandomGenreBooks(0, true) }
        binding.rvRandomGenre1.adapter = randomGenre1Adapter

        setupCategoryRecyclerView(binding.rvRandomGenre2)
        randomGenre2Adapter = BookCategoryAdapter(requireContext(), randomGenre2Books) { if (selectedRandomGenres.size > 1) loadRandomGenreBooks(1, true) }
        binding.rvRandomGenre2.adapter = randomGenre2Adapter

        setupCategoryRecyclerView(binding.rvRandomGenre3)
        randomGenre3Adapter = BookCategoryAdapter(requireContext(), randomGenre3Books) { if (selectedRandomGenres.size > 2) loadRandomGenreBooks(2, true) }
        binding.rvRandomGenre3.adapter = randomGenre3Adapter
        updateRandomGenreViews() // Bu, başlıkları ayarlar ve başlangıçta gizler

        // For You Adapter
        setupCategoryRecyclerView(binding.rvForYou)
        forYouAdapter = BookCategoryAdapter(requireContext(), forYouBooks) { /* For You için sayfalama genellikle olmaz */ }
        binding.rvForYou.adapter = forYouAdapter
        hideSection(binding.tvForYou, binding.rvForYou) // Başlangıçta gizle

        // Similar Books Adapters
        setupCategoryRecyclerView(binding.rvSimilarTo1)
        similarTo1Adapter = BookCategoryAdapter(requireContext(), similarTo1Books) { /* Similar için sayfalama genellikle olmaz */ }
        binding.rvSimilarTo1.adapter = similarTo1Adapter
        hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)

        setupCategoryRecyclerView(binding.rvSimilarTo2)
        similarTo2Adapter = BookCategoryAdapter(requireContext(), similarTo2Books) { /* Similar için sayfalama genellikle olmaz */ }
        binding.rvSimilarTo2.adapter = similarTo2Adapter
        hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
    }

    private fun updateRandomGenreViews(){
        if (_binding == null) return
        val genreTextViews = listOf(binding.tvRandomGenre1, binding.tvRandomGenre2, binding.tvRandomGenre3)
        val genreRecyclerViews = listOf(binding.rvRandomGenre1, binding.rvRandomGenre2, binding.rvRandomGenre3)

        for (i in 0..2) {
            if (i < selectedRandomGenres.size) {
                genreTextViews[i].text = "Best ${selectedRandomGenres[i]} Books" // İngilizce metin
                hideSection(genreTextViews[i], genreRecyclerViews[i]) // Başlangıçta gizle
            } else {
                hideSection(genreTextViews[i], genreRecyclerViews[i]) // Tür yoksa gizle
            }
        }
    }

    private fun setupCategoryRecyclerView(recyclerView: RecyclerView) {
        context?.let { ctx ->
            recyclerView.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupSearchScrollListener() {
        if (_binding == null) return
        binding.bookRV.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0 || !isInSearchMode) return // Sadece aşağı kaydırırken ve arama modundaysa

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                // Son birkaç item görünür olduğunda yükle (threshold)
                val threshold = booksPerPage / 2
                if (!isSearchLoading && !isSearchLastPage && totalItemCount > 0 &&
                    lastVisibleItemPosition >= totalItemCount - 1 - threshold // -1 çünkü pozisyon 0'dan başlar
                ) {
                    if (!searchQuery.isNullOrBlank() || currentFilters != BookFilters()) {
                        Log.d("SearchScroll", "Loading more search results. Query: '$searchQuery', Filters: $currentFilters")
                        searchBooks(searchQuery, currentFilters) // Mevcut sorgu ve filtrelerle daha fazla yükle
                    }
                }
            }
        })
    }

    private fun loadCarouselBooks() {
        _binding?.let { it.viewPagerCarousel.visibility = View.GONE }
            ?: run {
                Log.e("Carousel", "loadCarouselBooks: _binding is null at the very start. Aborting.")
                return
            }

        uiScope.launch {
            try {
                val query = firestore.collection("books_data")
                    .orderBy("rating_count", Query.Direction.DESCENDING)
                    .limit(CAROUSEL_FETCH_LIMIT)
                val documents = query.get().await()

                if (!isActive) {
                    Log.w("Carousel", "Coroutine inactive after await. Exiting loadCarouselBooks.")
                    return@launch
                }

                val fetchedBooks = documents.mapNotNull { parseDocumentToBook(it) }

                _binding?.let { currentBinding ->
                    if (fetchedBooks.isNotEmpty()) {
                        val shuffledBooks = fetchedBooks.shuffled().take(CAROUSEL_DISPLAY_COUNT)
                        carouselBookList.clear()
                        carouselBookList.addAll(shuffledBooks)
                        carouselAdapter.notifyDataSetChanged()
                        if (carouselBookList.isNotEmpty()) {
                            currentBinding.viewPagerCarousel.visibility = View.VISIBLE
                            if(isResumed && !isCarouselManuallyPaused) startCarouselAutoScroll()
                        } else {
                            currentBinding.viewPagerCarousel.visibility = View.GONE
                        }
                    } else {
                        currentBinding.viewPagerCarousel.visibility = View.GONE
                    }
                } ?: Log.w("Carousel", "Binding became null before UI update in try block.")

            } catch (ce: CancellationException) {
                Log.i("Carousel", "loadCarouselBooks coroutine was cancelled (expected if fragment is being destroyed): ${ce.message}")
            } catch (e: Exception) {
                Log.e("Carousel", "Unexpected error in loadCarouselBooks coroutine: ${e.message}", e)
                if (isActive && _binding != null) { // Check isActive and _binding before UI interaction in catch
                    try {
                        binding.viewPagerCarousel.visibility = View.GONE
                    } catch (npe: NullPointerException) {
                        Log.e("Carousel", "NPE in catch (other exception) trying to update UI: ${npe.message}")
                    }
                } else {
                    Log.w("Carousel", "Not attempting UI update on error: coroutine inactive or binding null.")
                }
            }
        }
    }


    private fun startCarouselAutoScroll() {
        if (carouselBookList.size <= 1 || carouselScrollJob?.isActive == true || !isResumed || isCarouselManuallyPaused) {
            return
        }
        if (!isAdded || _binding == null) { // Add _binding check
            Log.w("Carousel", "Fragment not attached or binding is null. Cannot start auto-scroll.")
            return
        }

        carouselScrollJob = uiScope.launch {
            Log.d("Carousel", "Auto-scroll coroutine started.")
            try {
                while (isActive) {
                    delay(CAROUSEL_AUTO_SCROLL_DELAY_MS)
                    if (isActive && isResumed && !isCarouselManuallyPaused && _binding != null && carouselBookList.isNotEmpty() && binding.viewPagerCarousel.adapter != null && binding.viewPagerCarousel.adapter!!.itemCount > 0) {
                        val currentItem = binding.viewPagerCarousel.currentItem
                        val itemCount = binding.viewPagerCarousel.adapter!!.itemCount
                        if (itemCount > 0) {
                            val nextItem = (currentItem + 1) % itemCount
                            binding.viewPagerCarousel.setCurrentItem(nextItem, true)
                        }
                    } else if (!isActive || !isResumed || isCarouselManuallyPaused || _binding == null) {
                        Log.d("Carousel", "Auto-scroll conditions no longer met or binding null. Stopping loop.")
                        break
                    }
                }
            } catch (ce: CancellationException) {
                Log.d("Carousel", "Auto-scroll coroutine was cancelled.")
            } catch (e: Exception) {
                Log.e("Carousel", "Exception in auto-scroll coroutine: ${e.message}", e)
            } finally {
                Log.d("Carousel", "Auto-scroll coroutine finished.")
            }
        }
    }

    private fun stopCarouselAutoScrollInternally() {
        if (carouselScrollJob?.isActive == true) {
            Log.d("Carousel", "Stopping auto-scroll job.")
            carouselScrollJob?.cancel()
        }
        carouselScrollJob = null
    }

    private fun showSection(textView: TextView, recyclerView: RecyclerView) {
        if (_binding == null) return
        textView.visibility = View.VISIBLE
        recyclerView.visibility = View.VISIBLE
    }
    private fun hideSection(textView: TextView, recyclerView: RecyclerView) {
        if (_binding == null) return
        textView.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    // --- Data Loading: Firestore Categories ---
    private fun loadTopRatedByCountBooks(loadMore: Boolean = false) {
        if (isTopRatedByCountLoading || !::topRatedByCountAdapter.isInitialized) return
        isTopRatedByCountLoading = true

        var query = firestore.collection("books_data")
            .orderBy("rating_count", Query.Direction.DESCENDING)
            .limit(categoryBooksPerPage.toLong())

        if (loadMore && lastVisibleTopRatedByCount != null) {
            query = query.startAfter(lastVisibleTopRatedByCount!!)
        } else if (!loadMore) {
            topRatedByCountBooks.clear()
            lastVisibleTopRatedByCount = null
        }

        query.get().addOnSuccessListener { documents ->
            if (_binding == null || !isAdded) { // **NPE FIX**
                Log.w("Firestore", "loadTopRatedByCountBooks: View gone, skipping UI update.")
                isTopRatedByCountLoading = false
                return@addOnSuccessListener
            }
            if (!documents.isEmpty) {
                lastVisibleTopRatedByCount = documents.documents.lastOrNull()
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                if (newBooks.isNotEmpty()) {
                    val wasEmpty = topRatedByCountBooks.isEmpty()
                    val startPosition = topRatedByCountBooks.size
                    topRatedByCountBooks.addAll(newBooks)

                    if (!loadMore || wasEmpty) {
                        topRatedByCountAdapter.notifyDataSetChanged()
                    } else {
                        topRatedByCountAdapter.notifyItemRangeInserted(startPosition, newBooks.size)
                    }

                    if (topRatedByCountBooks.isNotEmpty()) {
                        showSection(binding.tvTopRatedByCountBooks, binding.rvTopRatedByCountBooks)
                    }
                }
            } else {
                if (topRatedByCountBooks.isEmpty() && !loadMore) {
                    hideSection(binding.tvTopRatedByCountBooks, binding.rvTopRatedByCountBooks)
                }
            }
            isTopRatedByCountLoading = false
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error loadTopRated: ${e.message}")
            if (_binding != null && isAdded) { // **NPE FIX**
                if (topRatedByCountBooks.isEmpty()) {
                    hideSection(binding.tvTopRatedByCountBooks, binding.rvTopRatedByCountBooks)
                }
            }
            isTopRatedByCountLoading = false
        }
    }

    private fun loadHighRatedFictionBooks(loadMore: Boolean = false) {
        if (isHighRatedFictionLoading || !::highRatedFictionAdapter.isInitialized) return
        isHighRatedFictionLoading = true
        var query = firestore.collection("books_data")
            .whereArrayContains("genres", "Fiction")
            .orderBy("average_rating", Query.Direction.DESCENDING)
            .whereGreaterThan("average_rating", 3.99)
            .limit(categoryBooksPerPage.toLong())

        if (loadMore && lastVisibleHighRatedFiction != null) {
            query = query.startAfter(lastVisibleHighRatedFiction!!)
        } else if (!loadMore) {
            highRatedFictionBooks.clear()
            lastVisibleHighRatedFiction = null
        }

        query.get().addOnSuccessListener { documents ->
            if (_binding == null || !isAdded) { // **NPE FIX**
                Log.w("Firestore", "loadHighRatedFictionBooks: View gone, skipping UI update.")
                isHighRatedFictionLoading = false
                return@addOnSuccessListener
            }
            if (!documents.isEmpty) {
                lastVisibleHighRatedFiction = documents.documents.lastOrNull()
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                if (newBooks.isNotEmpty()) {
                    val wasEmpty = highRatedFictionBooks.isEmpty()
                    val startPosition = highRatedFictionBooks.size
                    highRatedFictionBooks.addAll(newBooks)
                    if (!loadMore || wasEmpty) highRatedFictionAdapter.notifyDataSetChanged()
                    else highRatedFictionAdapter.notifyItemRangeInserted(startPosition, newBooks.size)

                    if (highRatedFictionBooks.isNotEmpty()) {
                        showSection(binding.tvHighRatedFictionBooks, binding.rvHighRatedFictionBooks)
                    }
                }
            } else {
                if (highRatedFictionBooks.isEmpty() && !loadMore) {
                    hideSection(binding.tvHighRatedFictionBooks, binding.rvHighRatedFictionBooks)
                }
            }
            isHighRatedFictionLoading = false
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error loadFiction: ${e.message}")
            if (_binding != null && isAdded) { // **NPE FIX**
                if (highRatedFictionBooks.isEmpty()) {
                    hideSection(binding.tvHighRatedFictionBooks, binding.rvHighRatedFictionBooks)
                }
            }
            isHighRatedFictionLoading = false
        }
    }

    private fun loadHighRatedNonFictionBooks(loadMore: Boolean = false) {
        if (isHighRatedNonFictionLoading || !::highRatedNonFictionAdapter.isInitialized) return
        isHighRatedNonFictionLoading = true
        var query = firestore.collection("books_data")
            .whereArrayContains("genres", "Nonfiction")
            .orderBy("average_rating", Query.Direction.DESCENDING)
            .whereGreaterThan("average_rating", 3.99)
            .limit(categoryBooksPerPage.toLong())

        if (loadMore && lastVisibleHighRatedNonFiction != null) {
            query = query.startAfter(lastVisibleHighRatedNonFiction!!)
        } else if (!loadMore) {
            highRatedNonFictionBooks.clear()
            lastVisibleHighRatedNonFiction = null
        }

        query.get().addOnSuccessListener { documents ->
            if (_binding == null || !isAdded) { // **NPE FIX**
                Log.w("Firestore", "loadHighRatedNonFictionBooks: View gone, skipping UI update.")
                isHighRatedNonFictionLoading = false
                return@addOnSuccessListener
            }
            if (!documents.isEmpty) {
                lastVisibleHighRatedNonFiction = documents.documents.lastOrNull()
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                if (newBooks.isNotEmpty()) {
                    val wasEmpty = highRatedNonFictionBooks.isEmpty()
                    val startPosition = highRatedNonFictionBooks.size
                    highRatedNonFictionBooks.addAll(newBooks)
                    if (!loadMore || wasEmpty) highRatedNonFictionAdapter.notifyDataSetChanged()
                    else highRatedNonFictionAdapter.notifyItemRangeInserted(startPosition, newBooks.size)

                    if (highRatedNonFictionBooks.isNotEmpty()) {
                        showSection(binding.tvHighRatedNonFictionBooks, binding.rvHighRatedNonFictionBooks)
                    }
                }
            } else {
                if (highRatedNonFictionBooks.isEmpty() && !loadMore) {
                    hideSection(binding.tvHighRatedNonFictionBooks, binding.rvHighRatedNonFictionBooks)
                }
            }
            isHighRatedNonFictionLoading = false
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error loadNonFiction: ${e.message}")
            if (_binding != null && isAdded) { // **NPE FIX**
                if (highRatedNonFictionBooks.isEmpty()) {
                    hideSection(binding.tvHighRatedNonFictionBooks, binding.rvHighRatedNonFictionBooks)
                }
            }
            isHighRatedNonFictionLoading = false
        }
    }

    private fun loadRandomGenreSections() {
        if (selectedRandomGenres.isNotEmpty()) loadRandomGenreBooks(0)
        if (selectedRandomGenres.size > 1) loadRandomGenreBooks(1)
        if (selectedRandomGenres.size > 2) loadRandomGenreBooks(2)
    }

    private fun loadRandomGenreBooks(genreIndex: Int, loadMore: Boolean = false) {
        if (genreIndex < 0 || genreIndex >= selectedRandomGenres.size) return
        val genre = selectedRandomGenres[genreIndex]

        val components = when (genreIndex) {
            0 -> GenreSectionComponents(if(::randomGenre1Adapter.isInitialized) randomGenre1Adapter else null, randomGenre1Books, lastVisibleRandomGenre1, { bool: Boolean -> isRandomGenre1Loading = bool }, {isRandomGenre1Loading}, binding.tvRandomGenre1, binding.rvRandomGenre1)
            1 -> GenreSectionComponents(if(::randomGenre2Adapter.isInitialized) randomGenre2Adapter else null, randomGenre2Books, lastVisibleRandomGenre2, { bool: Boolean -> isRandomGenre2Loading = bool }, {isRandomGenre2Loading}, binding.tvRandomGenre2, binding.rvRandomGenre2)
            2 -> GenreSectionComponents(if(::randomGenre3Adapter.isInitialized) randomGenre3Adapter else null, randomGenre3Books, lastVisibleRandomGenre3, { bool: Boolean -> isRandomGenre3Loading = bool }, {isRandomGenre3Loading}, binding.tvRandomGenre3, binding.rvRandomGenre3)
            else -> return
        }

        if (components.adapter == null || components.getIsLoading()) return
        components.setIsLoading(true)

        var query = firestore.collection("books_data")
            .whereArrayContains("genres", genre)
            .orderBy("average_rating", Query.Direction.DESCENDING)
            .limit(categoryBooksPerPage.toLong())

        if (loadMore && components.lastVisibleDoc != null) {
            query = query.startAfter(components.lastVisibleDoc)
        } else if (!loadMore) {
            components.bookList.clear()
            setLastVisible(genreIndex, null) // lastVisibleDoc'u sıfırla
        }

        query.get().addOnSuccessListener { documents ->
            if (_binding == null || !isAdded) { // **NPE FIX**
                Log.w("Firestore", "loadRandomGenreBooks (Genre: $genre): View gone, skipping UI update.")
                components.setIsLoading(false)
                return@addOnSuccessListener
            }
            if (!documents.isEmpty) {
                setLastVisible(genreIndex, documents.documents.lastOrNull())
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                if (newBooks.isNotEmpty()) {
                    val wasEmpty = components.bookList.isEmpty()
                    val startPosition = components.bookList.size
                    components.bookList.addAll(newBooks)
                    if (!loadMore || wasEmpty) components.adapter.notifyDataSetChanged()
                    else components.adapter.notifyItemRangeInserted(startPosition, newBooks.size)

                    if (components.bookList.isNotEmpty()) { // Sadece kitap varsa göster
                        showSection(components.textView, components.recyclerView)
                    }
                }
            } else {
                if (components.bookList.isEmpty() && !loadMore) {
                    hideSection(components.textView, components.recyclerView)
                }
            }
            components.setIsLoading(false)
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error loadRandomGenre $genre: ${e.message}")
            if (_binding != null && isAdded) { // **NPE FIX**
                if (components.bookList.isEmpty()) {
                    hideSection(components.textView, components.recyclerView)
                }
            }
            components.setIsLoading(false)
        }
    }

    private fun setLastVisible(index: Int, document: DocumentSnapshot?) {
        when (index) {
            0 -> lastVisibleRandomGenre1 = document
            1 -> lastVisibleRandomGenre2 = document
            2 -> lastVisibleRandomGenre3 = document
        }
    }

    private fun loadForYouRecommendations() {
        if (_binding == null) {
            Log.w("ForYou", "Binding is null, cannot load 'For You' recommendations.")
            return
        }
        if (!::forYouAdapter.isInitialized) {
            Log.w("ForYou", "Adapter not initialized for 'For You'.")
            hideSection(binding.tvForYou, binding.rvForYou)
            return
        }
        if (isForYouLoading) return
        val userId = auth.currentUser?.uid ?: run {
            Log.d("ForYou", "User not logged in. Skipping 'For You'.")
            hideSection(binding.tvForYou, binding.rvForYou)
            return
        }

        isForYouLoading = true
        forYouBooks.clear()
        forYouAdapter.notifyDataSetChanged()
        hideSection(binding.tvForYou, binding.rvForYou)
        Log.d("ForYou", "Starting 'For You' recommendations for user $userId...")

        uiScope.launch {
            var bookIdsToLoad: List<Long>? = null
            var triggerApiCall = false

            try {
                val currentTotalRatingCount = getRatingCount(userId) // This might be cancelled
                if (!isActive) return@launch // Check after suspend

                Log.d("ForYouLogic", "User $userId current total rating count: $currentTotalRatingCount")

                if (currentTotalRatingCount < 10) {
                    Log.d("ForYouLogic", "User has less than 10 ratings ($currentTotalRatingCount). 'For You' recommendations skipped.")
                    isForYouLoading = false
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
                    triggerApiCall = true
                } else {
                    val cacheTimestamp = cachedData.timestamp?.toDate()?.time ?: 0L
                    val currentTimeMillis = Timestamp.now().toDate().time
                    val oneDayInMillis = TimeUnit.DAYS.toMillis(1)
                    val isCacheStale = (currentTimeMillis - cacheTimestamp) > oneDayInMillis
                    val cachedRatedBooksCount = cachedData.rated_books_count ?: 0

                    if (isCacheStale || currentTotalRatingCount >= cachedRatedBooksCount + 5) {
                        triggerApiCall = true
                    } else {
                        bookIdsToLoad = cachedData.recommendations?.mapNotNull { it.book_id }
                    }
                }

                if (triggerApiCall) {
                    Log.d("ForYouAPI", "Triggering /recommend-by-genre API for user $userId (has $currentTotalRatingCount ratings)...")
                    try {
                        withContext(Dispatchers.IO) {
                            Log.d("ForYouAPI", "Simulating API call for getRecommendationsByGenre for user $userId")
                            delay(1500)
                        }
                        if (!isActive) return@launch
                        delay(3000)
                        if (!isActive) return@launch

                        val updatedSnapshot = cacheRef.get().await()
                        if (!isActive) return@launch
                        val updatedCacheData = try { updatedSnapshot.toObject<UserRecommendations>() } catch (e: Exception) { null }

                        if (updatedCacheData?.recommendations != null && updatedCacheData.recommendations.isNotEmpty()) {
                            bookIdsToLoad = updatedCacheData.recommendations.mapNotNull { it.book_id }
                        }
                    } catch (e: Exception) { // Catch API call or subsequent cache read errors
                        if (e is CancellationException) throw e // Re-throw cancellation
                        Log.e("ForYouAPI", "Error in API trigger/cache read for user $userId: ${e.message}", e)
                    }
                }

                if (!isActive || _binding == null) return@launch // Final check before UI

                if (!bookIdsToLoad.isNullOrEmpty()) {
                    fetchBookDetailsByIds(
                        bookIds = bookIdsToLoad!!.map { it.toInt() },
                        targetList = forYouBooks,
                        targetAdapter = forYouAdapter,
                        targetTextView = binding.tvForYou,
                        targetRecyclerView = binding.rvForYou,
                        sectionTitle = "Recommended For You",
                        setLoadingFlag = { isForYouLoading = it },
                        limit = bookIdsToLoad!!.size
                    )
                } else {
                    isForYouLoading = false
                    hideSection(binding.tvForYou, binding.rvForYou)
                }

            } catch (ce: CancellationException) {
                Log.i("ForYou", "loadForYouRecommendations coroutine was cancelled for $userId: ${ce.message}")
            } catch (e: Exception) {
                Log.e("ForYou", "Error in loadForYouRecommendations flow for user $userId: ${e.message}", e)
                if (isActive && _binding != null) {
                    isForYouLoading = false
                    hideSection(binding.tvForYou, binding.rvForYou)
                }
            }
        }
    }
    private suspend fun getRatingCount(userId: String): Long {
        // This function is a suspend function, its cancellation will be handled by the calling coroutine's scope.
        // It doesn't interact with UI directly, so no _binding check is needed here.
        try {
            val countResult = firestore.collection("user_ratings")
                .whereEqualTo("userId", userId)
                .count()
                .get(com.google.firebase.firestore.AggregateSource.SERVER)
                .await()
            // Check isActive before logging or returning if this function were part of a UI-updating coroutine directly.
            // Since it's just returning a value, this is fine.
            Log.d("RatingCount", "Fetched rating count for user $userId: ${countResult.count}")
            return countResult.count
        } catch (ce: CancellationException) {
            Log.i("RatingCount", "getRatingCount for user $userId was cancelled: ${ce.message}")
            throw ce // Re-throw so the caller knows it was cancelled
        } catch (e: Exception) {
            Log.e("RatingCount", "Error getting rating count for user $userId: ${e.message}", e)
            return -1L // Hata durumunda negatif bir değer döndür
        }
    }

    private fun loadSimilarBooksSections() {
        if (_binding == null) return
        val userId = auth.currentUser?.uid ?: run {
            Log.d("SimilarBooks", "User not logged in. Skipping similar books.")
            hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
            hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
            return
        }

        if (!::similarTo1Adapter.isInitialized || !::similarTo2Adapter.isInitialized) {
            Log.w("SimilarBooks", "Adapters for similar books not ready yet.")
            hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
            hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
            return
        }

        hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
        hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
        Log.d("SimilarBooks", "Finding 5-star ratings for user $userId")

        uiScope.launch {
            try {
                val fiveStarQuery = firestore.collection("user_ratings")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("rating", 5.0)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(MAX_SIMILAR_SECTIONS.toLong() * 2)
                    .get().await()
                if (!isActive) return@launch

                Log.d("SimilarBooks", "5-star query completed. Found ${fiveStarQuery.size()} documents.")

                val fiveStarBookIdsWithTimestamp = fiveStarQuery.documents.mapNotNull { doc ->
                    val bookId = doc.getLong("bookId")
                    val timestamp = doc.getTimestamp("timestamp")
                    if (bookId != null && timestamp != null) Pair(bookId, timestamp) else null
                }.distinctBy { it.first }
                    .sortedByDescending { it.second }
                    .map { it.first }
                    .take(MAX_SIMILAR_SECTIONS)

                Log.d("SimilarBooks", "Mapped valid Book IDs (Long) for similar sections: $fiveStarBookIdsWithTimestamp")

                if (fiveStarBookIdsWithTimestamp.isEmpty()) {
                    Log.d("SimilarBooks", "No valid 5-star ratings with numeric bookId found after mapping.")
                    return@launch
                }

                if (!isActive || _binding == null) return@launch

                val originalBooksDetails = fetchBookDetailsByIdsInternal(fiveStarBookIdsWithTimestamp.map { it.toInt() })
                if (!isActive || _binding == null) return@launch
                Log.d("SimilarBooks", "Fetched details for ${originalBooksDetails.size} original 5-star books.")

                if (originalBooksDetails.isEmpty()){
                    Log.d("SimilarBooks", "Could not fetch details for 5-star rated books. IDs: $fiveStarBookIdsWithTimestamp")
                    return@launch
                }

                originalBookTitle1 = if (originalBooksDetails.isNotEmpty()) originalBooksDetails[0].title else null
                originalBookTitle2 = if (originalBooksDetails.size > 1) originalBooksDetails[1].title else null
                Log.d("SimilarBooks", "Stored original titles. Title1: $originalBookTitle1, Title2: $originalBookTitle2")

                if (originalBooksDetails.isNotEmpty()) {
                    loadSimilarBooksForIndex(0, originalBooksDetails.mapNotNull { it.book_id })
                }

            } catch (ce: CancellationException) {
                Log.i("SimilarBooks", "loadSimilarBooksSections was cancelled: ${ce.message}")
            }
            catch (e: Exception) {
                Log.e("SimilarBooks", "Error finding/fetching 5-star rated books: ${e.message}", e)
            }
        }
    }

    private fun loadSimilarBooksForIndex(index: Int, originalBookIds: List<Int>) {
        if (_binding == null) {
            Log.w("SimilarBooksSeq", "Binding is null, cannot load similar books for index $index.")
            // Try to load next if applicable, but this state is problematic.
            if (index + 1 < MAX_SIMILAR_SECTIONS && index + 1 < originalBookIds.size) {
                uiScope.launch { loadSimilarBooksForIndex(index + 1, originalBookIds) }
            }
            return
        }
        if (index >= MAX_SIMILAR_SECTIONS || index >= originalBookIds.size) {
            Log.d("SimilarBooksSeq", "Finished loading all requested similar book sections or no more original books.")
            return
        }

        val originalBookId = originalBookIds[index]

        val components = when (index) {
            0 -> SimilarSectionComponents(isSimilarTo1Loading, {b:Boolean -> isSimilarTo1Loading=b}, similarTo1Books, if(::similarTo1Adapter.isInitialized) similarTo1Adapter else null, binding.tvSimilarTo1, binding.rvSimilarTo1, originalBookTitle1)
            1 -> SimilarSectionComponents(isSimilarTo2Loading, {b:Boolean -> isSimilarTo2Loading=b}, similarTo2Books, if(::similarTo2Adapter.isInitialized) similarTo2Adapter else null, binding.tvSimilarTo2, binding.rvSimilarTo2, originalBookTitle2)
            else -> {
                // This case should ideally not be reached if MAX_SIMILAR_SECTIONS is 2.
                // If it is, try to load the next one to prevent getting stuck.
                if (index + 1 < MAX_SIMILAR_SECTIONS && index + 1 < originalBookIds.size) {
                    uiScope.launch { loadSimilarBooksForIndex(index + 1, originalBookIds) }
                }
                return
            }
        }

        if (components.isLoading) {
            Log.d("SimilarBooksSeq", "Section $index for original book ID $originalBookId is already loading.")
            return // Don't queue up the next one if this one is already loading. Let it finish.
        }
        if (components.adapter == null) {
            Log.e("SimilarBooksSeq", "Adapter for section $index (Original ID: $originalBookId) not initialized. Skipping.")
            components.setLoadingFlag(false)
            hideSection(components.textView, components.recyclerView)
            // Launch the next one in a new coroutine to avoid deep recursion if many adapters are null.
            uiScope.launch { loadSimilarBooksForIndex(index + 1, originalBookIds) }
            return
        }

        components.setLoadingFlag(true)
        components.targetList.clear()
        components.adapter.notifyDataSetChanged()
        hideSection(components.textView, components.recyclerView)
        val sectionDisplayTitle = "Similar to ${components.originalTitle ?: "Book ID $originalBookId"}"
        Log.d("SimilarBooksSeq", "Loading section $index for original book ID: $originalBookId (${components.originalTitle})")

        uiScope.launch {
            var bookIdsToLoad: List<Long>? = null
            var triggerApiCall = false
            val sectionLogPrefix = "SimilarBooksSeq[Idx:$index, OrigID:$originalBookId]"

            try {
                Log.d(sectionLogPrefix, "Querying cache 'similar_books_cache' where book_id == $originalBookId (Long)")
                val cacheQuery = firestore.collection("similar_books_cache")
                    .whereEqualTo("book_id", originalBookId.toLong())
                    .limit(1).get().await()
                if (!isActive) return@launch

                Log.d(sectionLogPrefix, "Cache query completed. Found ${cacheQuery.size()} documents.")
                var cachedData: SimilarBooksResult? = null
                if (!cacheQuery.isEmpty) {
                    cachedData = try { cacheQuery.documents[0].toObject<SimilarBooksResult>() }
                    catch (e:Exception){ Log.e(sectionLogPrefix, "Error converting cache snapshot: ${e.message}", e); null }
                }

                if (cachedData?.recommendations.isNullOrEmpty()) {
                    triggerApiCall = true
                } else {
                    bookIdsToLoad = cachedData!!.recommendations!!.mapNotNull { it.book_id }
                }

                if (triggerApiCall) {
                    Log.d(sectionLogPrefix, "Triggering /similar-books API...")
                    try {
                        withContext(Dispatchers.IO) {
                            Log.d(sectionLogPrefix, "Simulating API call for getSimilarBooks for bookId $originalBookId")
                            delay(1500)
                        }
                        if (!isActive) return@launch
                        delay(3000)
                        if (!isActive) return@launch

                        val updatedQuery = firestore.collection("similar_books_cache")
                            .whereEqualTo("book_id", originalBookId.toLong())
                            .limit(1).get().await()
                        if (!isActive) return@launch
                        val updatedCacheData = if (!updatedQuery.isEmpty) {
                            try { updatedQuery.documents[0].toObject<SimilarBooksResult>() }
                            catch (e:Exception){ Log.e(sectionLogPrefix, "Error converting UPDATED cache snapshot: ${e.message}", e); null }
                        } else null

                        if (updatedCacheData?.recommendations != null && updatedCacheData.recommendations.isNotEmpty()) {
                            bookIdsToLoad = updatedCacheData.recommendations.mapNotNull { it.book_id }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(sectionLogPrefix, "Error triggering /similar-books API or reading cache after: ${e.message}", e)
                    }
                }

                if (!isActive || _binding == null) return@launch // Final check before UI

                if (!bookIdsToLoad.isNullOrEmpty()) {
                    val filteredBookIds = bookIdsToLoad!!.filter { it.toInt() != originalBookId }.map { it.toInt() }
                    if (filteredBookIds.isNotEmpty()) {
                        fetchBookDetailsByIds(
                            bookIds = filteredBookIds,
                            targetList = components.targetList,
                            targetAdapter = components.adapter,
                            targetTextView = components.textView,
                            targetRecyclerView = components.recyclerView,
                            sectionTitle = sectionDisplayTitle,
                            setLoadingFlag = components.setLoadingFlag,
                            limit = 10
                        )
                    } else {
                        components.setLoadingFlag(false)
                    }
                } else {
                    components.setLoadingFlag(false)
                }

            } catch (ce: CancellationException) {
                Log.i(sectionLogPrefix, "Coroutine cancelled: ${ce.message}")
                components.setLoadingFlag(false) // Ensure loading flag is reset on cancellation
            } catch (e: Exception) {
                Log.e(sectionLogPrefix, "Error in loadSimilarBooksForIndex flow: ${e.message}", e)
                components.setLoadingFlag(false)
            } finally {
                // Ensure the next section is attempted to load regardless of success/failure of current one,
                // but only if the coroutine wasn't cancelled (which implies fragment destruction).
                if (isActive) {
                    withContext(Dispatchers.Main) { // Ensure it runs on main thread
                        if (_binding != null) { // Check binding again before launching next
                            loadSimilarBooksForIndex(index + 1, originalBookIds)
                        }
                    }
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
        Log.d("FetchDetails[$effectiveLogSection]", "Fetching for '$sectionTitle' with ${bookIds.size} IDs (limit: $limit)")

        if (bookIds.isEmpty()) {
            if (_binding != null) hideSection(targetTextView, targetRecyclerView) // **NPE FIX**
            setLoadingFlag(false)
            return
        }

        val idsToFetch = bookIds.take(limit)
        Log.d("FetchDetails[$effectiveLogSection]", "Effective IDs to fetch: ${idsToFetch.size}")

        val queryChunks = idsToFetch.chunked(30)
        val allFetchedBooks = mutableListOf<Books>()
        var completedChunks = 0
        val totalChunks = queryChunks.size

        if (queryChunks.isEmpty()){
            if (_binding != null) hideSection(targetTextView, targetRecyclerView) // **NPE FIX**
            setLoadingFlag(false)
            return
        }
        Log.d("FetchDetails[$effectiveLogSection]", "Processing $totalChunks chunks.")

        queryChunks.forEachIndexed { chunkIndex, chunk ->
            if (chunk.isEmpty()) {
                completedChunks++
                if (completedChunks == totalChunks) {
                    if (_binding != null) handleFetchedBookResults(allFetchedBooks, targetList, targetAdapter, targetTextView, targetRecyclerView, sectionTitle, setLoadingFlag, effectiveLogSection) // **NPE FIX**
                    else setLoadingFlag(false)
                }
                return@forEachIndexed
            }
            Log.d("FetchDetails[$effectiveLogSection]", "Fetching chunk ${chunkIndex + 1}/$totalChunks with ${chunk.size} IDs.")
            firestore.collection("books_data")
                .whereIn("book_id", chunk)
                .get()
                .addOnSuccessListener { documents ->
                    if (_binding == null || !isAdded) { // **NPE FIX**
                        Log.w("FetchDetails", "fetchBookDetailsByIds (Chunk Success): View gone, skipping UI update for $sectionTitle.")
                        completedChunks++
                        if (completedChunks == totalChunks) setLoadingFlag(false) // Still need to manage loading flag
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
                        if (_binding != null && isAdded) handleFetchedBookResults(allFetchedBooks, targetList, targetAdapter, targetTextView, targetRecyclerView, sectionTitle, setLoadingFlag, effectiveLogSection) // **NPE FIX**
                        else setLoadingFlag(false)
                    }
                }
        }
    }

    private fun handleFetchedBookResults(
        fetchedBooks: List<Books>,
        targetList: ArrayList<Books>,
        targetAdapter: BookCategoryAdapter,
        targetTextView: TextView,
        targetRecyclerView: RecyclerView,
        sectionTitle: String,
        setLoadingFlag: (Boolean) -> Unit,
        logSection: String
    ) {
        if (_binding == null || !isAdded) { // **NPE FIX**
            Log.w("HandleFetch", "handleFetchedBookResults: View gone, skipping UI update for $sectionTitle.")
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

    // --- Search Functions ---
    private fun switchToSearchMode() {
        if (_binding == null) return
        if (!isInSearchMode) {
            isInSearchMode = true
            binding.viewPagerCarousel.visibility = View.GONE
            binding.scrollViewCategories.visibility = View.GONE
            binding.bookRV.visibility = View.VISIBLE
            try {
                binding.filterButton.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.w("FilterButton", "Filter button (ID: filterButton) not found in switchToSearchMode. ${e.message}")
            }
            Log.d("MainPageSearch", "Switched to search mode.")
        }
    }
    private fun switchToCategoriesMode() {
        if (_binding == null) return
        if (isInSearchMode) {
            isInSearchMode = false
            binding.searchMainpage.setQuery("", false)
            binding.searchMainpage.clearFocus()

            if (carouselBookList.isNotEmpty()) {
                binding.viewPagerCarousel.visibility = View.VISIBLE
            }
            binding.scrollViewCategories.visibility = View.VISIBLE
            binding.bookRV.visibility = View.GONE
            try {
                binding.filterButton.visibility = View.GONE
            } catch (e: Exception) {
                Log.w("FilterButton", "Filter button (ID: filterButton) not found in switchToCategoriesMode. ${e.message}")
            }
            clearSearchResultsAndFilters()
            Log.d("MainPageSearch", "Switched to categories mode.")
        }
    }

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
        Log.d("MainPageSearch", "Search results cleared (excluding filters).")
    }
    private fun clearSearchResultsAndFilters() {
        clearSearchResults()
        currentFilters = BookFilters()
        searchQuery = null
        Log.d("MainPageSearch", "Search results and filters completely cleared.")
    }

    private fun searchBooks(queryText: String?, filters: BookFilters) {
        if (_binding == null) {
            Log.w("SearchBooks", "Binding is null, cannot execute search.")
            isSearchLoading = false // Reset loading flag if we can't proceed
            return
        }
        if (isSearchLoading) {
            Log.d("MainPageSearch", "Search already in progress. Skipping.")
            return
        }
        isSearchLoading = true
        binding.progressBar.visibility = View.VISIBLE

        var firestoreQuery: Query = firestore.collection("books_data")
        val effectiveQueryText = queryText?.lowercase()?.trim()

        // 1. Apply all WHERE clauses first
        if (filters.selectedGenres.isNotEmpty()) {
            val genresToFilter = filters.selectedGenres.take(10)
            if (genresToFilter.isNotEmpty()) {
                firestoreQuery = firestoreQuery.whereArrayContainsAny("genres", genresToFilter)
            }
        }
        filters.minRating?.let {
            firestoreQuery = firestoreQuery.whereGreaterThanOrEqualTo("average_rating", it)
        }
        filters.author?.let { authorName ->
            if (authorName.isNotBlank()) {
                firestoreQuery = firestoreQuery.whereEqualTo("author_name_lowercase", authorName.lowercase())
            }
        }
        filters.publicationYear?.let {
            firestoreQuery = firestoreQuery.whereEqualTo("publication_year", it)
        }

        // 2. Apply all ORDER BY clauses
        val needsAverageRatingFirstOrderBy = filters.minRating != null
        val isTextSearchForRelevance = !effectiveQueryText.isNullOrBlank() && filters.sortBy == SortOption.RELEVANCE
        var firstOrderByApplied = false

        if (needsAverageRatingFirstOrderBy) {
            firestoreQuery = firestoreQuery.orderBy("average_rating", Query.Direction.DESCENDING)
            firstOrderByApplied = true
            // Secondary sorts
            if (filters.sortBy == SortOption.PUBLICATION_YEAR_DESC) {
                firestoreQuery = firestoreQuery.orderBy("publication_year", Query.Direction.DESCENDING)
            } else if (filters.sortBy == SortOption.RELEVANCE || filters.sortBy == SortOption.RATING_DESC) {
                firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
            }
            if (isTextSearchForRelevance) {
                Log.w("SearchLogic", "Text search (startAt/endAt on title) cannot be primary due to 'average_rating' inequality. Text filtering will be less precise.")
            }
        } else {
            if (isTextSearchForRelevance) {
                firestoreQuery = firestoreQuery.orderBy("title_lowercase")
                firstOrderByApplied = true
                firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
            } else {
                when (filters.sortBy) {
                    SortOption.RATING_DESC -> {
                        firestoreQuery = firestoreQuery.orderBy("average_rating", Query.Direction.DESCENDING)
                            .orderBy("rating_count", Query.Direction.DESCENDING)
                        firstOrderByApplied = true
                    }
                    SortOption.PUBLICATION_YEAR_DESC -> {
                        firestoreQuery = firestoreQuery.orderBy("publication_year", Query.Direction.DESCENDING)
                            .orderBy("average_rating", Query.Direction.DESCENDING)
                        firstOrderByApplied = true
                    }
                    SortOption.RELEVANCE -> {
                        firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
                        firstOrderByApplied = true
                    }
                }
            }
        }

        if (!firstOrderByApplied) {
            firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
            Log.d("SearchLogic", "Applied default orderBy rating_count as no other primary order was set.")
        }

        // 3. Apply CURSOR methods (startAt/endAt for text search)
        if (isTextSearchForRelevance && !needsAverageRatingFirstOrderBy) {
            firestoreQuery = firestoreQuery.startAt(effectiveQueryText).endAt(effectiveQueryText + "\uf8ff")
        }

        // 4. Apply Pagination CURSOR (startAfter)
        lastVisibleSearchDocument?.let {
            firestoreQuery = firestoreQuery.startAfter(it)
        }

        // 5. Apply LIMIT
        firestoreQuery = firestoreQuery.limit(booksPerPage.toLong())

        Log.d("FirestoreQuery", "Executing final query. SortBy: ${filters.sortBy}, Text: '$effectiveQueryText', MinRating: ${filters.minRating}")

        firestoreQuery.get()
            .addOnSuccessListener { documents ->
                if (_binding == null || !isAdded) { // **NPE FIX**
                    Log.w("Firestore", "searchBooks Success: View gone, skipping UI update.")
                    isSearchLoading = false
                    // If progress bar was visible, it might remain. Consider if it needs hiding even if view is gone.
                    // _binding?.progressBar?.visibility = View.GONE // This would still crash if _binding is null
                    return@addOnSuccessListener
                }
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }

                if (!documents.isEmpty) {
                    lastVisibleSearchDocument = documents.documents.last()
                    isSearchLastPage = documents.size() < booksPerPage
                } else {
                    isSearchLastPage = true
                }

                if (newBooks.isNotEmpty()) {
                    val currentSize = searchBookList.size
                    searchBookList.addAll(newBooks)
                    searchAdapter.notifyItemRangeInserted(currentSize, newBooks.size)
                }

                if (searchBookList.isEmpty()) {
                    Toast.makeText(context, "No books found matching your criteria.", Toast.LENGTH_SHORT).show()
                }

                isSearchLoading = false
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                Log.e("MainPageSearch", "Error searching books with filters: ${exception.message}", exception)
                isSearchLoading = false
                if (_binding != null && isAdded) { // **NPE FIX**
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error during search: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
    }


    // --- Helper Functions ---
    @Suppress("UNCHECKED_CAST")
    private fun parseDocumentToBook(document: DocumentSnapshot): Books? {
        try {
            val title = safeGetString(document.get("title"))
            val bookId = convertToInt(document.get("book_id"))

            if (title.isBlank() || bookId == 0) {
                Log.w("ParseBook", "Skipping document ${document.id}: Missing title or valid book_id (ID: $bookId). Title: '$title'")
                return null
            }

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
                rating_count = convertToInt(document.get("rating_count"))
            )
        } catch (e: Exception) {
            Log.e("ParseBook", "Critical error parsing document ${document.id} to Book object: ${e.message}", e)
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
