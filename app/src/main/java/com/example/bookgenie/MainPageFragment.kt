package com.example.bookgenie

import android.os.Bundle
import android.os.Parcelable // Parcelable importu
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
// import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
// import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
// import com.example.bookgenie.api.RetrofitInstance
import com.example.bookgenie.databinding.FragmentMainPageBinding
// import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
// import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// Data class'lar (RecommendationEntry, UserRecommendations, SimilarBooksResult) aynı kalır.
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

private data class GenreSectionComponents(
    val adapter: BookCategoryAdapter?,
    val bookList: ArrayList<Books>,
    val lastVisibleDoc: DocumentSnapshot?,
    val setIsLoading: (Boolean) -> Unit,
    val getIsLoading: () -> Boolean,
    val textView: TextView,
    val recyclerView: RecyclerView
)

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

    private lateinit var carouselAdapter: CarouselBookAdapter
    private val carouselBookList = ArrayList<Books>()
    private var carouselScrollJob: Job? = null
    private val CAROUSEL_AUTO_SCROLL_DELAY_MS = 5000L
    private val CAROUSEL_FETCH_LIMIT = 20L
    private val CAROUSEL_DISPLAY_COUNT = 7
    private var isCarouselManuallyPaused: Boolean = false

    private lateinit var searchAdapter: BookAdapter // Sizin BookAdapter'ınız kullanılacak
    private val searchBookList = ArrayList<Books>()
    private var lastVisibleSearchDocument: DocumentSnapshot? = null
    private var isSearchLoading = false
    private var isSearchLastPage = false
    private var searchQuery: String? = null
    private var isInSearchMode = false
    private var currentFilters: BookFilters = BookFilters()

    private val availableGenres = listOf(
        "Adventure", "Biography", "Classics", "Contemporary", "Fantasy", "Fiction",
        "Historical", "Historical Fiction", "History", "Horror", "Mystery",
        "Nonfiction", "Paranormal", "Poetry", "Romance", "Science Fiction",
        "Thriller", "Young Adult"
    ).distinct().sorted()

    private lateinit var selectedRandomGenres: List<String>
    private val categoryBooksPerPage = 7

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
        _binding = FragmentMainPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectRandomGenres()
        setupCarousel()
        setupSearchView()
        setupBackPressHandler()
        setupRecyclerViews()

        loadCarouselBooks()
        loadTopRatedByCountBooks()
        loadHighRatedFictionBooks()
        loadHighRatedNonFictionBooks()
        loadRandomGenreSections()
        loadForYouRecommendations()
        loadSimilarBooksSections()

        try {
            binding.filterButton.visibility = View.GONE
            binding.tvNoResultsSearch?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("ViewInit", "Filter button or tvNoResultsSearch not found. ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isCarouselManuallyPaused && carouselBookList.isNotEmpty()) {
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

    private fun setupCarousel() {
        if (_binding == null) {
            Log.e("SetupCarousel", "Binding is null.")
            return
        }
        carouselAdapter = CarouselBookAdapter(requireContext(), carouselBookList)
        binding.viewPagerCarousel.adapter = carouselAdapter
        binding.viewPagerCarousel.offscreenPageLimit = 3
        binding.viewPagerCarousel.clipToPadding = false
        binding.viewPagerCarousel.clipChildren = false
        val compositePageTransformer = CompositePageTransformer()
        val carouselMargin = try {
            resources.getDimensionPixelOffset(R.dimen.carousel_margin)
        } catch (e: android.content.res.Resources.NotFoundException) {
            Log.w("SetupCarousel", "R.dimen.carousel_margin not found. Using default.")
            (40 * resources.displayMetrics.density).toInt()
        }
        compositePageTransformer.addTransformer(MarginPageTransformer(carouselMargin))
        compositePageTransformer.addTransformer { page, position ->
            val r = 1 - abs(position)
            page.scaleY = 0.85f + r * 0.15f
            page.scaleX = 0.85f + r * 0.10f
            page.alpha = 0.5f + r * 0.5f
        }
        binding.viewPagerCarousel.setPageTransformer(compositePageTransformer)

        binding.viewPagerCarousel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        if (carouselScrollJob?.isActive == true) {
                            isCarouselManuallyPaused = true
                            stopCarouselAutoScrollInternally()
                        }
                    }
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        if (isCarouselManuallyPaused) {
                            uiScope.launch {
                                delay(CAROUSEL_AUTO_SCROLL_DELAY_MS)
                                if (isActive && isCarouselManuallyPaused && isResumed) {
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

    private fun selectRandomGenres() {
        selectedRandomGenres = availableGenres.shuffled().take(3)
    }

    private fun setupSearchView() {
        if (_binding == null) return
        val searchView = binding.searchMainpage
        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        try {
            searchEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            val hintColor = try {
                ContextCompat.getColor(requireContext(), R.color.siyahimsi)
            } catch (e: android.content.res.Resources.NotFoundException) {
                ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
            }
            searchEditText.setHintTextColor(hintColor)
        } catch (e: Exception) {
            Log.w("SetupSearch", "Error setting search colors. ${e.message}")
        }

        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isInSearchMode) {
                switchToSearchMode()
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                currentSearchOperationJob?.cancel()
                searchQuery = newText.trim()
                if (searchQuery!!.isBlank() && currentFilters == BookFilters()) {
                    if (isInSearchMode) clearSearchResults()
                } else {
                    if (!isInSearchMode) switchToSearchMode()
                    currentSearchOperationJob = uiScope.launch {
                        delay(400)
                        if (isActive) performSearch()
                    }
                }
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                currentSearchOperationJob?.cancel()
                searchView.clearFocus()
                searchQuery = query.trim()
                if (searchQuery!!.isNotBlank() || currentFilters != BookFilters()) {
                    if (!isInSearchMode) switchToSearchMode()
                    performSearch()
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
        if (isInSearchMode) {
            performSearch()
        }
    }

    private fun performSearch() {
        if (_binding == null) return
        clearSearchResults()
        if (searchQuery.isNullOrBlank() && currentFilters == BookFilters()) {
            binding.tvNoResultsSearch?.text = "Type to search or apply filters."
            binding.tvNoResultsSearch?.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            return
        }
        binding.tvNoResultsSearch?.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        searchBooks(searchQuery, currentFilters)
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
                        try {
                            if (!findNavController().popBackStack()) {
                                // requireActivity().finish()
                            }
                        } catch (e: IllegalStateException) {
                            // requireActivity().finish()
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
        // searchAdapter'ı sizin BookAdapter'ınızla ve doğru fragmentType ile oluşturun
        // Eğer arama sonuçları için "main" tipi navigasyon kullanılacaksa:
        searchAdapter = BookAdapter(requireContext(), searchBookList, "main")
        // Ya da BookAdapter'ınızın varsayılan fragmentType'ı "main" ise:
        // searchAdapter = BookAdapter(requireContext(), searchBookList)
        binding.bookRV.adapter = searchAdapter
        setupSearchScrollListener()

        // Category RecyclerViews & Adapters
        setupCategoryRecyclerView(binding.rvTopRatedByCountBooks)
        topRatedByCountAdapter = BookCategoryAdapter(requireContext(), topRatedByCountBooks) { loadTopRatedByCountBooks(true) }
        binding.rvTopRatedByCountBooks.adapter = topRatedByCountAdapter
        hideSection(binding.tvTopRatedByCountBooks, binding.rvTopRatedByCountBooks)

        setupCategoryRecyclerView(binding.rvHighRatedFictionBooks)
        highRatedFictionAdapter = BookCategoryAdapter(requireContext(), highRatedFictionBooks) { loadHighRatedFictionBooks(true) }
        binding.rvHighRatedFictionBooks.adapter = highRatedFictionAdapter
        hideSection(binding.tvHighRatedFictionBooks, binding.rvHighRatedFictionBooks)

        setupCategoryRecyclerView(binding.rvHighRatedNonFictionBooks)
        highRatedNonFictionAdapter = BookCategoryAdapter(requireContext(), highRatedNonFictionBooks) { loadHighRatedNonFictionBooks(true) }
        binding.rvHighRatedNonFictionBooks.adapter = highRatedNonFictionAdapter
        hideSection(binding.tvHighRatedNonFictionBooks, binding.rvHighRatedNonFictionBooks)

        setupCategoryRecyclerView(binding.rvRandomGenre1)
        randomGenre1Adapter = BookCategoryAdapter(requireContext(), randomGenre1Books) { if (selectedRandomGenres.isNotEmpty()) loadRandomGenreBooks(0, true) }
        binding.rvRandomGenre1.adapter = randomGenre1Adapter

        setupCategoryRecyclerView(binding.rvRandomGenre2)
        randomGenre2Adapter = BookCategoryAdapter(requireContext(), randomGenre2Books) { if (selectedRandomGenres.size > 1) loadRandomGenreBooks(1, true) }
        binding.rvRandomGenre2.adapter = randomGenre2Adapter

        setupCategoryRecyclerView(binding.rvRandomGenre3)
        randomGenre3Adapter = BookCategoryAdapter(requireContext(), randomGenre3Books) { if (selectedRandomGenres.size > 2) loadRandomGenreBooks(2, true) }
        binding.rvRandomGenre3.adapter = randomGenre3Adapter
        updateRandomGenreViews()

        setupCategoryRecyclerView(binding.rvForYou)
        forYouAdapter = BookCategoryAdapter(requireContext(), forYouBooks) { /* For You pagination usually not needed */ }
        binding.rvForYou.adapter = forYouAdapter
        hideSection(binding.tvForYou, binding.rvForYou)

        setupCategoryRecyclerView(binding.rvSimilarTo1)
        similarTo1Adapter = BookCategoryAdapter(requireContext(), similarTo1Books) { /* Similar pagination usually not needed */ }
        binding.rvSimilarTo1.adapter = similarTo1Adapter
        hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)

        setupCategoryRecyclerView(binding.rvSimilarTo2)
        similarTo2Adapter = BookCategoryAdapter(requireContext(), similarTo2Books) { /* Similar pagination usually not needed */ }
        binding.rvSimilarTo2.adapter = similarTo2Adapter
        hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)
    }

    private fun updateRandomGenreViews(){
        if (_binding == null) return
        val genreTextViews = listOf(binding.tvRandomGenre1, binding.tvRandomGenre2, binding.tvRandomGenre3)
        val genreRecyclerViews = listOf(binding.rvRandomGenre1, binding.rvRandomGenre2, binding.rvRandomGenre3)

        for (i in 0..2) {
            if (i < selectedRandomGenres.size) {
                genreTextViews[i].text = "Best ${selectedRandomGenres[i]} Books"
                hideSection(genreTextViews[i], genreRecyclerViews[i])
            } else {
                hideSection(genreTextViews[i], genreRecyclerViews[i])
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
                if (dy <= 0 || !isInSearchMode) return

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                val threshold = booksPerPage / 2
                if (!isSearchLoading && !isSearchLastPage && totalItemCount > 0 &&
                    lastVisibleItemPosition >= totalItemCount - 1 - threshold
                ) {
                    if (!searchQuery.isNullOrBlank() || currentFilters != BookFilters()) {
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
                Log.i("Carousel", "loadCarouselBooks cancelled.")
            } catch (e: Exception) {
                Log.e("Carousel", "Error loading carousel: ${e.message}", e)
                _binding?.viewPagerCarousel?.visibility = View.GONE
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
            } catch (ce: CancellationException) {
                Log.d("Carousel", "Auto-scroll cancelled.")
            } catch (e: Exception) {
                Log.e("Carousel", "Exception in auto-scroll: ${e.message}", e)
            }
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
            if(::topRatedByCountAdapter.isInitialized) topRatedByCountAdapter.notifyDataSetChanged()
        }

        query.get().addOnSuccessListener { documents ->
            if (_binding == null || !isAdded) {
                isTopRatedByCountLoading = false
                return@addOnSuccessListener
            }
            if (!documents.isEmpty) {
                lastVisibleTopRatedByCount = documents.documents.lastOrNull()
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                if (newBooks.isNotEmpty()) {
                    val startPosition = topRatedByCountBooks.size
                    topRatedByCountBooks.addAll(newBooks)
                    if (startPosition == 0) topRatedByCountAdapter.notifyDataSetChanged()
                    else topRatedByCountAdapter.notifyItemRangeInserted(startPosition, newBooks.size)
                }
            }
            if (topRatedByCountBooks.isNotEmpty()) showSection(binding.tvTopRatedByCountBooks, binding.rvTopRatedByCountBooks)
            else hideSection(binding.tvTopRatedByCountBooks, binding.rvTopRatedByCountBooks)
            isTopRatedByCountLoading = false
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error loadTopRated: ${e.message}")
            if (_binding != null && isAdded && topRatedByCountBooks.isEmpty()) {
                hideSection(binding.tvTopRatedByCountBooks, binding.rvTopRatedByCountBooks)
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
            if(::highRatedFictionAdapter.isInitialized) highRatedFictionAdapter.notifyDataSetChanged()
        }

        query.get().addOnSuccessListener { documents ->
            if (_binding == null || !isAdded) {
                isHighRatedFictionLoading = false
                return@addOnSuccessListener
            }
            if (!documents.isEmpty) {
                lastVisibleHighRatedFiction = documents.documents.lastOrNull()
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                if (newBooks.isNotEmpty()) {
                    val startPosition = highRatedFictionBooks.size
                    highRatedFictionBooks.addAll(newBooks)
                    if (startPosition == 0) highRatedFictionAdapter.notifyDataSetChanged()
                    else highRatedFictionAdapter.notifyItemRangeInserted(startPosition, newBooks.size)
                }
            }
            if (highRatedFictionBooks.isNotEmpty()) showSection(binding.tvHighRatedFictionBooks, binding.rvHighRatedFictionBooks)
            else hideSection(binding.tvHighRatedFictionBooks, binding.rvHighRatedFictionBooks)
            isHighRatedFictionLoading = false
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error loadFiction: ${e.message}")
            if (_binding != null && isAdded && highRatedFictionBooks.isEmpty()) {
                hideSection(binding.tvHighRatedFictionBooks, binding.rvHighRatedFictionBooks)
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
            if(::highRatedNonFictionAdapter.isInitialized) highRatedNonFictionAdapter.notifyDataSetChanged()
        }

        query.get().addOnSuccessListener { documents ->
            if (_binding == null || !isAdded) {
                isHighRatedNonFictionLoading = false
                return@addOnSuccessListener
            }
            if (!documents.isEmpty) {
                lastVisibleHighRatedNonFiction = documents.documents.lastOrNull()
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                if (newBooks.isNotEmpty()) {
                    val startPosition = highRatedNonFictionBooks.size
                    highRatedNonFictionBooks.addAll(newBooks)
                    if (startPosition == 0) highRatedNonFictionAdapter.notifyDataSetChanged()
                    else highRatedNonFictionAdapter.notifyItemRangeInserted(startPosition, newBooks.size)
                }
            }
            if (highRatedNonFictionBooks.isNotEmpty()) showSection(binding.tvHighRatedNonFictionBooks, binding.rvHighRatedNonFictionBooks)
            else hideSection(binding.tvHighRatedNonFictionBooks, binding.rvHighRatedNonFictionBooks)
            isHighRatedNonFictionLoading = false
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error loadNonFiction: ${e.message}")
            if (_binding != null && isAdded && highRatedNonFictionBooks.isEmpty()) {
                hideSection(binding.tvHighRatedNonFictionBooks, binding.rvHighRatedNonFictionBooks)
            }
            isHighRatedNonFictionLoading = false
        }
    }

    private fun loadRandomGenreSections() {
        if (_binding == null || !::selectedRandomGenres.isInitialized || selectedRandomGenres.isEmpty()) return
        loadRandomGenreBooks(0)
        if (selectedRandomGenres.size > 1) loadRandomGenreBooks(1)
        if (selectedRandomGenres.size > 2) loadRandomGenreBooks(2)
    }

    private fun loadRandomGenreBooks(genreIndex: Int, loadMore: Boolean = false) {
        if (_binding == null || !::selectedRandomGenres.isInitialized || genreIndex < 0 || genreIndex >= selectedRandomGenres.size) return
        val genre = selectedRandomGenres[genreIndex]

        val components = when (genreIndex) {
            0 -> GenreSectionComponents(if(::randomGenre1Adapter.isInitialized) randomGenre1Adapter else null, randomGenre1Books, lastVisibleRandomGenre1, { b -> isRandomGenre1Loading = b }, {isRandomGenre1Loading}, binding.tvRandomGenre1, binding.rvRandomGenre1)
            1 -> GenreSectionComponents(if(::randomGenre2Adapter.isInitialized) randomGenre2Adapter else null, randomGenre2Books, lastVisibleRandomGenre2, { b -> isRandomGenre2Loading = b }, {isRandomGenre2Loading}, binding.tvRandomGenre2, binding.rvRandomGenre2)
            2 -> GenreSectionComponents(if(::randomGenre3Adapter.isInitialized) randomGenre3Adapter else null, randomGenre3Books, lastVisibleRandomGenre3, { b -> isRandomGenre3Loading = b }, {isRandomGenre3Loading}, binding.tvRandomGenre3, binding.rvRandomGenre3)
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
            setLastVisible(genreIndex, null)
            components.adapter?.notifyDataSetChanged()
        }

        query.get().addOnSuccessListener { documents ->
            if (_binding == null || !isAdded) {
                components.setIsLoading(false)
                return@addOnSuccessListener
            }
            if (!documents.isEmpty) {
                setLastVisible(genreIndex, documents.documents.lastOrNull())
                val newBooks = documents.mapNotNull { parseDocumentToBook(it) }
                if (newBooks.isNotEmpty()) {
                    val startPosition = components.bookList.size
                    components.bookList.addAll(newBooks)
                    if (startPosition == 0) components.adapter?.notifyDataSetChanged()
                    else components.adapter?.notifyItemRangeInserted(startPosition, newBooks.size)
                }
            }
            if (components.bookList.isNotEmpty()) showSection(components.textView, components.recyclerView)
            else hideSection(components.textView, components.recyclerView)
            components.setIsLoading(false)
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error loadRandomGenre $genre: ${e.message}")
            if (_binding != null && isAdded && components.bookList.isEmpty()) {
                hideSection(components.textView, components.recyclerView)
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
        if (_binding == null || !::forYouAdapter.isInitialized) {
            _binding?.let { hideSection(it.tvForYou, it.rvForYou) }
            return
        }
        if (isForYouLoading) return
        val userId = auth.currentUser?.uid ?: run {
            _binding?.let { hideSection(it.tvForYou, it.rvForYou) }
            return
        }

        isForYouLoading = true
        forYouBooks.clear()
        forYouAdapter.notifyDataSetChanged()
        hideSection(binding.tvForYou, binding.rvForYou)

        uiScope.launch {
            var bookIdsToLoad: List<Long>? = null
            var triggerApiCall = false
            try {
                val currentTotalRatingCount = getRatingCount(userId)
                if (!isActive) return@launch

                if (currentTotalRatingCount < 10) {
                    isForYouLoading = false
                    if (isActive && _binding != null && isAdded) hideSection(binding.tvForYou, binding.rvForYou)
                    return@launch
                }

                val cacheRef = firestore.collection("user_recommendations").document(userId)
                val cacheSnapshot = cacheRef.get().await()
                if (!isActive) return@launch

                val cachedData = try { cacheSnapshot.toObject<UserRecommendations>() } catch (e: Exception) { null }

                if (cachedData == null || cachedData.recommendations.isNullOrEmpty()) {
                    triggerApiCall = true
                } else {
                    val cacheTimestamp = cachedData.timestamp?.toDate()?.time ?: 0L
                    val oneDayInMillis = TimeUnit.DAYS.toMillis(1)
                    if ((Timestamp.now().toDate().time - cacheTimestamp) > oneDayInMillis ||
                        currentTotalRatingCount >= (cachedData.rated_books_count ?: 0) + 5) {
                        triggerApiCall = true
                    } else {
                        bookIdsToLoad = cachedData.recommendations?.mapNotNull { it.book_id }
                    }
                }

                if (triggerApiCall) {
                    try {
                        withContext(Dispatchers.IO) { delay(1500) }
                        if (!isActive) return@launch
                        delay(3000)
                        if (!isActive) return@launch

                        val updatedSnapshot = cacheRef.get().await()
                        if (!isActive) return@launch
                        val updatedCacheData = try { updatedSnapshot.toObject<UserRecommendations>() } catch (e: Exception) { null }
                        bookIdsToLoad = updatedCacheData?.recommendations?.mapNotNull { it.book_id }
                    } catch (e: Exception) { if (e is CancellationException) throw e }
                }

                if (!isActive || _binding == null) return@launch

                if (!bookIdsToLoad.isNullOrEmpty()) {
                    fetchBookDetailsByIds(
                        bookIds = bookIdsToLoad!!.map { it.toInt() },
                        targetList = forYouBooks, targetAdapter = forYouAdapter,
                        targetTextView = binding.tvForYou, targetRecyclerView = binding.rvForYou,
                        sectionTitle = "Recommended For You",
                        setLoadingFlag = { isForYouLoading = it },
                        limit = bookIdsToLoad!!.size
                    )
                } else {
                    isForYouLoading = false
                    hideSection(binding.tvForYou, binding.rvForYou)
                }
            } catch (ce: CancellationException) {
                Log.i("ForYou", "loadForYou cancelled.")
                if (isForYouLoading) isForYouLoading = false
            } catch (e: Exception) {
                Log.e("ForYou", "Error in loadForYou: ${e.message}", e)
                if (isActive && _binding != null) {
                    isForYouLoading = false
                    hideSection(binding.tvForYou, binding.rvForYou)
                }
            }
        }
    }
    private suspend fun getRatingCount(userId: String): Long {
        return try {
            firestore.collection("user_ratings").whereEqualTo("userId", userId).count()
                .get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            -1L
        }
    }

    private fun loadSimilarBooksSections() {
        if (_binding == null || !::similarTo1Adapter.isInitialized || !::similarTo2Adapter.isInitialized) {
            _binding?.let {
                hideSection(it.tvSimilarTo1, it.rvSimilarTo1)
                hideSection(it.tvSimilarTo2, it.rvSimilarTo2)
            }
            return
        }
        val userId = auth.currentUser?.uid ?: run {
            _binding?.let {
                hideSection(it.tvSimilarTo1, it.rvSimilarTo1)
                hideSection(it.tvSimilarTo2, it.rvSimilarTo2)
            }
            return
        }

        hideSection(binding.tvSimilarTo1, binding.rvSimilarTo1)
        hideSection(binding.tvSimilarTo2, binding.rvSimilarTo2)

        uiScope.launch {
            try {
                val fiveStarQuery = firestore.collection("user_ratings")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("rating", 5.0)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(MAX_SIMILAR_SECTIONS.toLong() * 2).get().await()
                if (!isActive) return@launch

                val fiveStarBookIds = fiveStarQuery.documents.mapNotNull { doc ->
                    Pair(doc.getLong("bookId"), doc.getTimestamp("timestamp"))
                }.filter { it.first != null && it.second != null }
                    .distinctBy { it.first }
                    .sortedByDescending { it.second!! }
                    .mapNotNull { it.first }
                    .take(MAX_SIMILAR_SECTIONS)

                if (!isActive || _binding == null || fiveStarBookIds.isEmpty()) return@launch

                val originalBooksDetails = fetchBookDetailsByIdsInternal(fiveStarBookIds.map { it.toInt() })
                if (!isActive || _binding == null || originalBooksDetails.isEmpty()) return@launch

                originalBookTitle1 = originalBooksDetails.getOrNull(0)?.title
                originalBookTitle2 = originalBooksDetails.getOrNull(1)?.title

                loadSimilarBooksForIndex(0, originalBooksDetails.mapNotNull { it.book_id })

            } catch (ce: CancellationException) {
                Log.i("SimilarBooks", "loadSimilarBooksSections cancelled.")
            } catch (e: Exception) {
                Log.e("SimilarBooks", "Error loading similar sections: ${e.message}", e)
            }
        }
    }

    private fun loadSimilarBooksForIndex(index: Int, originalBookIds: List<Int>) {
        if (_binding == null || index >= MAX_SIMILAR_SECTIONS || index >= originalBookIds.size) {
            if (_binding != null && index < MAX_SIMILAR_SECTIONS && index < originalBookIds.size && (index + 1 < originalBookIds.size)) {
                uiScope.launch { loadSimilarBooksForIndex(index + 1, originalBookIds) }
            }
            return
        }

        val originalBookId = originalBookIds[index]
        val components = when (index) {
            0 -> SimilarSectionComponents(isSimilarTo1Loading, {b -> isSimilarTo1Loading=b}, similarTo1Books, if(::similarTo1Adapter.isInitialized) similarTo1Adapter else null, binding.tvSimilarTo1, binding.rvSimilarTo1, originalBookTitle1)
            1 -> SimilarSectionComponents(isSimilarTo2Loading, {b -> isSimilarTo2Loading=b}, similarTo2Books, if(::similarTo2Adapter.isInitialized) similarTo2Adapter else null, binding.tvSimilarTo2, binding.rvSimilarTo2, originalBookTitle2)
            else -> {
                if (index + 1 < MAX_SIMILAR_SECTIONS && index + 1 < originalBookIds.size) {
                    uiScope.launch { loadSimilarBooksForIndex(index + 1, originalBookIds) }
                }
                return
            }
        }

        if (components.isLoading || components.adapter == null) {
            components.setLoadingFlag(false)
            if (components.adapter == null && _binding != null) hideSection(components.textView, components.recyclerView)
            if (_binding != null) uiScope.launch { loadSimilarBooksForIndex(index + 1, originalBookIds) }
            return
        }

        components.setLoadingFlag(true)
        components.targetList.clear()
        components.adapter.notifyDataSetChanged()
        hideSection(components.textView, components.recyclerView)
        val sectionDisplayTitle = "Similar to ${components.originalTitle ?: "Book ID $originalBookId"}"
        val sectionLogPrefix = "SimilarBooks[Idx:$index,OrigID:$originalBookId]"

        uiScope.launch {
            var bookIdsToLoad: List<Long>? = null
            var triggerApiCall = false
            try {
                val cacheQuery = firestore.collection("similar_books_cache")
                    .whereEqualTo("book_id", originalBookId.toLong()).limit(1).get().await()
                if (!isActive) return@launch

                val cachedData = if (!cacheQuery.isEmpty) try { cacheQuery.documents[0].toObject<SimilarBooksResult>() } catch (e:Exception){ null } else null

                if (cachedData?.recommendations.isNullOrEmpty()) {
                    triggerApiCall = true
                } else {
                    if (cachedData != null) {
                        bookIdsToLoad = cachedData.recommendations!!.mapNotNull { it.book_id }
                    }
                }

                if (triggerApiCall) {
                    try {
                        withContext(Dispatchers.IO) { delay(1500) }
                        if (!isActive) return@launch
                        delay(3000)
                        if (!isActive) return@launch

                        val updatedQuery = firestore.collection("similar_books_cache")
                            .whereEqualTo("book_id", originalBookId.toLong()).limit(1).get().await()
                        if (!isActive) return@launch
                        val updatedCacheData = if (!updatedQuery.isEmpty) try { updatedQuery.documents[0].toObject<SimilarBooksResult>() } catch (e:Exception){ null } else null
                        bookIdsToLoad = updatedCacheData?.recommendations?.mapNotNull { it.book_id }
                    } catch (e: Exception) { if (e is CancellationException) throw e }
                }

                if (!isActive || _binding == null) return@launch

                if (!bookIdsToLoad.isNullOrEmpty()) {
                    val filteredBookIdsToInt = bookIdsToLoad!!
                        .filter { it.toInt() != originalBookId }
                        .map { it.toInt() }

                    if (filteredBookIdsToInt.isNotEmpty()) {
                        fetchBookDetailsByIds(
                            bookIds = filteredBookIdsToInt, targetList = components.targetList,
                            targetAdapter = components.adapter, targetTextView = components.textView,
                            targetRecyclerView = components.recyclerView, sectionTitle = sectionDisplayTitle,
                            setLoadingFlag = components.setLoadingFlag, limit = 10
                        )
                    } else {
                        components.setLoadingFlag(false)
                        hideSection(components.textView, components.recyclerView)
                    }
                } else {
                    components.setLoadingFlag(false)
                    hideSection(components.textView, components.recyclerView)
                }
            } catch (ce: CancellationException) {
                Log.i(sectionLogPrefix, "Cancelled.")
                components.setLoadingFlag(false)
            } catch (e: Exception) {
                Log.e(sectionLogPrefix, "Error: ${e.message}", e)
                components.setLoadingFlag(false)
                if(isActive && _binding != null) hideSection(components.textView, components.recyclerView)
            } finally {
                if (isActive && _binding != null) {
                    loadSimilarBooksForIndex(index + 1, originalBookIds)
                }
            }
        }
    }


    private fun fetchBookDetailsByIds(
        bookIds: List<Int>, targetList: ArrayList<Books>, targetAdapter: BookCategoryAdapter,
        targetTextView: TextView, targetRecyclerView: RecyclerView, sectionTitle: String,
        setLoadingFlag: (Boolean) -> Unit, limit: Int = categoryBooksPerPage
    ) {
        if (bookIds.isEmpty()) {
            if (_binding != null && isAdded) hideSection(targetTextView, targetRecyclerView)
            setLoadingFlag(false)
            return
        }
        val idsToFetch = bookIds.take(limit)
        val queryChunks = idsToFetch.chunked(30)
        val allFetchedBooks = mutableListOf<Books>()
        var completedChunks = 0
        val totalChunks = queryChunks.size

        if (queryChunks.isEmpty()){
            if (_binding != null && isAdded) hideSection(targetTextView, targetRecyclerView)
            setLoadingFlag(false)
            return
        }

        queryChunks.forEachIndexed { chunkIndex, chunk ->
            if (chunk.isEmpty()) {
                completedChunks++
                if (completedChunks == totalChunks) {
                    if (_binding != null && isAdded) {
                        handleFetchedBookResults(allFetchedBooks, targetList, targetAdapter, targetTextView, targetRecyclerView, sectionTitle, setLoadingFlag, sectionTitle.take(10))
                    } else {
                        setLoadingFlag(false)
                    }
                }
                return@forEachIndexed
            }
            firestore.collection("books_data").whereIn("book_id", chunk).get()
                .addOnSuccessListener { documents ->
                    if (_binding == null || !isAdded) {
                        completedChunks++
                        if (completedChunks == totalChunks) setLoadingFlag(false)
                        return@addOnSuccessListener
                    }
                    allFetchedBooks.addAll(documents.mapNotNull { parseDocumentToBook(it) })
                    completedChunks++
                    if (completedChunks == totalChunks) {
                        handleFetchedBookResults(allFetchedBooks, targetList, targetAdapter, targetTextView, targetRecyclerView, sectionTitle, setLoadingFlag, sectionTitle.take(10))
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("FetchDetails", "Error chunk ${chunkIndex + 1} for '$sectionTitle': ${exception.message}")
                    completedChunks++
                    if (completedChunks == totalChunks) {
                        if (_binding != null && isAdded) {
                            handleFetchedBookResults(allFetchedBooks, targetList, targetAdapter, targetTextView, targetRecyclerView, sectionTitle, setLoadingFlag, sectionTitle.take(10))
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
            setLoadingFlag(false)
            return
        }
        targetList.clear()
        if (fetchedBooks.isNotEmpty()) {
            targetList.addAll(fetchedBooks)
            targetTextView.text = sectionTitle
            showSection(targetTextView, targetRecyclerView)
        } else {
            hideSection(targetTextView, targetRecyclerView)
        }
        targetAdapter.notifyDataSetChanged()
        setLoadingFlag(false)
    }


    private suspend fun fetchBookDetailsByIdsInternal(bookIds: List<Int>): List<Books> {
        if (bookIds.isEmpty()) return emptyList()
        val allFetchedBooks = mutableListOf<Books>()
        val queryChunks = bookIds.chunked(30)
        return try {
            for (chunk in queryChunks) {
                if (chunk.isEmpty()) continue
                val documents = firestore.collection("books_data").whereIn("book_id", chunk).get().await()
                allFetchedBooks.addAll(documents.mapNotNull { parseDocumentToBook(it) })
            }
            allFetchedBooks
        } catch (e: Exception) {
            Log.e("FetchInternal", "Error: ${e.message}", e)
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

    // --- searchBooks FUNCTION WITH CORRECTED ORDERING LOGIC ---
    private fun searchBooks(queryText: String?, filters: BookFilters) {
        if (_binding == null) {
            Log.w("SearchBooks", "Binding is null, cannot execute search.")
            isSearchLoading = false
            binding?.progressBar?.visibility = View.GONE
            return
        }
        isSearchLoading = true

        val effectiveQueryText = queryText?.lowercase()?.trim()
        var firestoreQuery: Query = firestore.collection("books_data")
        var applyClientSideTextFilter = false

        // 1. Apply WHERE clauses
        if (filters.selectedGenres.isNotEmpty()) {
            firestoreQuery = firestoreQuery.whereArrayContainsAny("genres", filters.selectedGenres.take(10))
        }
        filters.author?.let {
            if (it.isNotBlank()) firestoreQuery = firestoreQuery.whereEqualTo("author_name_lowercase", it.lowercase())
        }
        filters.publicationYear?.let {
            firestoreQuery = firestoreQuery.whereEqualTo("publication_year", it)
        }
        filters.minRating?.let {
            firestoreQuery = firestoreQuery.whereGreaterThanOrEqualTo("average_rating", it)
        }

        // 2. Apply ORDER BY clauses (must be before startAt/startAfter/endAt)
        val canServerSideTextSearch = !effectiveQueryText.isNullOrBlank() && filters.minRating == null

        if (canServerSideTextSearch) {
            firestoreQuery = firestoreQuery.orderBy("title_lowercase") // Primary for text search
            // Secondary sorting for server-side text search
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
        } else { // Not server-side text search as primary, or minRating is present
            if (!effectiveQueryText.isNullOrBlank()) {
                applyClientSideTextFilter = true
            }
            if (filters.minRating != null) { // average_rating must be the first orderBy
                firestoreQuery = firestoreQuery.orderBy("average_rating", Query.Direction.DESCENDING)
                when (filters.sortBy) {
                    SortOption.RELEVANCE, SortOption.RATING_DESC, null ->
                        firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
                    SortOption.PUBLICATION_YEAR_DESC -> {
                        firestoreQuery = firestoreQuery.orderBy("publication_year", Query.Direction.DESCENDING)
                        firestoreQuery = firestoreQuery.orderBy("rating_count", Query.Direction.DESCENDING)
                    }
                }
            } else { // No minRating, and not primary server-side text search
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

        // 3. Apply text search cursors (startAt/endAt) IF server-side text search was primary
        if (canServerSideTextSearch) {
            firestoreQuery = firestoreQuery.startAt(effectiveQueryText).endAt(effectiveQueryText!! + "\uf8ff")
        }

        // 4. Apply Pagination CURSOR (startAfter)
        lastVisibleSearchDocument?.let {
            firestoreQuery = firestoreQuery.startAfter(it)
        }

        // 5. Apply LIMIT
        firestoreQuery = firestoreQuery.limit(booksPerPage.toLong())

        Log.d("FirestoreQuery", "Final Query. Text: '$effectiveQueryText', ClientTextFilter: $applyClientSideTextFilter, Sort: ${filters.sortBy}, MinRating: ${filters.minRating}")

        firestoreQuery.get()
            .addOnSuccessListener { documents ->
                if (_binding == null || !isAdded) {
                    isSearchLoading = false
                    return@addOnSuccessListener
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
                    if (applyClientSideTextFilter && newBooks.isEmpty() && rawFetchedCount.toLong() == booksPerPage.toLong()) {
                        isSearchLastPage = false
                    } else {
                        isSearchLastPage = rawFetchedCount < booksPerPage
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
                Log.e("MainPageSearch", "Error searching books: ${exception.message}", exception)
                isSearchLoading = false
                if (_binding != null && isAdded) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error during search: ${exception.message}", Toast.LENGTH_LONG).show()
                    binding.tvNoResultsSearch?.text = "Error during search. Please try again."
                    binding.tvNoResultsSearch?.visibility = View.VISIBLE
                }
            }
    }


    @Suppress("UNCHECKED_CAST")
    private fun parseDocumentToBook(document: DocumentSnapshot): Books? {
        try {
            val title = safeGetString(document.get("title"))
            val bookId = convertToInt(document.get("book_id"))

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
