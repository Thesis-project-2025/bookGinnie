package com.example.bookgenie

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.bookgenie.databinding.FragmentMainPageBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainPageFragment : Fragment() {
    private lateinit var binding: FragmentMainPageBinding
    private val firestore = FirebaseFirestore.getInstance()

    // Search functionality
    private lateinit var searchAdapter: BookAdapter
    private val searchBookList = ArrayList<Books>()
    private var lastVisibleSearchDocument: DocumentSnapshot? = null
    private var isSearchLoading = false
    private var isSearchLastPage = false
    private var searchQuery: String? = null

    // Category adapters and data
    private lateinit var topRatedAdapter: BookCategoryAdapter
    private lateinit var actionAdapter: BookCategoryAdapter
    private lateinit var fictionAdapter: BookCategoryAdapter
    private lateinit var childrenAdapter: BookCategoryAdapter

    private val topRatedBooks = ArrayList<Books>()
    private val actionBooks = ArrayList<Books>()
    private val fictionBooks = ArrayList<Books>()
    private val childrenBooks = ArrayList<Books>()

    private var lastVisibleTopRated: DocumentSnapshot? = null
    private var lastVisibleAction: DocumentSnapshot? = null
    private var lastVisibleFiction: DocumentSnapshot? = null
    private var lastVisibleChildren: DocumentSnapshot? = null

    private var isTopRatedLoading = false
    private var isActionLoading = false
    private var isFictionLoading = false
    private var isChildrenLoading = false

    private val booksPerPage = 5
    private var isInSearchMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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

        setupSearchView()
        setupBackPressHandler()
        setupRecyclerViews()
        setupBottomNavigation()

        // Load initial data for categories
        loadTopRatedBooks()
        loadActionBooks()
        loadFictionBooks()
        loadChildrenBooks()
    }

    private fun setupSearchView() {
        val searchView = binding.searchMainpage
        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)

        searchEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.beige))
        searchEditText.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.beige))

        // Always switch to search mode when the search view gains focus
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                switchToSearchMode()
                Log.d("SearchMode", "Switched to search mode due to focus")
            }
        }

        // Add debounce to avoid too many queries
        var searchJob: Job? = null

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                // Cancel previous job if it's still active
                searchJob?.cancel()

                if (newText.isEmpty()) {
                    clearSearchResults()
                    Log.d("SearchMode", "Cleared search results")
                } else {
                    switchToSearchMode() // Ensure we're in search mode

                    // Use debounce to prevent firing too many queries
                    searchJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(300) // Wait for 300ms before executing search
                        clearSearchResults() // Clear before new search
                        lastVisibleSearchDocument = null // Reset for new search
                        searchBooks(newText.trim())
                        Log.d("SearchMode", "Searching for: $newText")
                    }
                }
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                if (query.isNotEmpty()) {
                    switchToSearchMode() // Ensure we're in search mode
                    clearSearchResults() // Clear before new search
                    lastVisibleSearchDocument = null // Reset for new search
                    searchBooks(query.trim())
                    Log.d("SearchMode", "Search submitted: $query")
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
                        requireActivity().onBackPressed()
                    }
                }
            }
        )
    }

    private fun setupRecyclerViews() {
        // Set up search results RecyclerView
        binding.bookRV.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        searchAdapter = BookAdapter(requireContext(), searchBookList)
        binding.bookRV.adapter = searchAdapter

        setupSearchScrollListener()

        // Set up category RecyclerViews
        setupCategoryRecyclerView(binding.rvTopRatedBooks) { loadTopRatedBooks() }
        setupCategoryRecyclerView(binding.rvActionBooks) { loadActionBooks() }
        setupCategoryRecyclerView(binding.rvFictionBooks) { loadFictionBooks() }
        setupCategoryRecyclerView(binding.rvChildrenBooks) { loadChildrenBooks() }

        // Initialize adapters
        topRatedAdapter = BookCategoryAdapter(requireContext(), topRatedBooks) { loadTopRatedBooks() }
        actionAdapter = BookCategoryAdapter(requireContext(), actionBooks) { loadActionBooks() }
        fictionAdapter = BookCategoryAdapter(requireContext(), fictionBooks) { loadFictionBooks() }
        childrenAdapter = BookCategoryAdapter(requireContext(), childrenBooks) { loadChildrenBooks() }

        binding.rvTopRatedBooks.adapter = topRatedAdapter
        binding.rvActionBooks.adapter = actionAdapter
        binding.rvFictionBooks.adapter = fictionAdapter
        binding.rvChildrenBooks.adapter = childrenAdapter
    }

    private fun setupCategoryRecyclerView(recyclerView: RecyclerView, loadMoreCallback: () -> Unit) {
        recyclerView.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
    }

    private fun setupSearchScrollListener() {
        binding.bookRV.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as StaggeredGridLayoutManager
                val totalItemCount = layoutManager.itemCount
                val visibleItemCount = layoutManager.childCount
                val firstVisibleItem = layoutManager.findFirstVisibleItemPositions(null).minOrNull() ?: 0

                if (!isSearchLoading && !isSearchLastPage &&
                    (visibleItemCount + firstVisibleItem) >= (totalItemCount * 0.9).toInt()) {
                    searchQuery?.let { searchBooks(it) }
                }
            }
        })
    }

    private fun setupBottomNavigation() {
        val bottomNavigationView: BottomNavigationView = binding.bottomNavView
        bottomNavigationView.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.idMainPage -> {
                    switchToCategoriesMode()
                    true
                }
                R.id.idSettings -> {
                    findNavController().navigate(R.id.mainPageToSettings)
                    true
                }
                R.id.idProfile -> {
                    findNavController().navigate(R.id.mainPageToUserInfo)
                    true
                }
                R.id.idSearch -> {
                    findNavController().navigate(R.id.action_mainPageFragment_to_searchFragment)
                    true
                }
                else -> false
            }
        }
    }

    // Category Data Loading Functions

    private fun loadTopRatedBooks() {
        if (isTopRatedLoading) return
        isTopRatedLoading = true

        Log.d("CategoryLoading", "Loading Top Rated books")

        var query = firestore.collection("books_data")
            .orderBy("average_rating", Query.Direction.DESCENDING)
            .limit(booksPerPage.toLong())

        lastVisibleTopRated?.let { query = query.startAfter(it) }

        query.get()
            .addOnSuccessListener { documents ->
                Log.d("CategoryLoading", "Top Rated books query returned ${documents.size()} documents")
                if (!documents.isEmpty) {
                    lastVisibleTopRated = documents.documents.last()
                    for (document in documents) {
                        val book = parseDocumentToBook(document)
                        topRatedBooks.add(book)
                        Log.d("CategoryLoading", "Added Top Rated book: ${book.title}")
                    }
                    topRatedAdapter.notifyDataSetChanged()
                }
                isTopRatedLoading = false
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error loading top rated books: ${exception.message}")
                isTopRatedLoading = false
            }
    }

    private fun loadActionBooks() {
        if (isActionLoading) return
        isActionLoading = true

        Log.d("CategoryLoading", "Loading Action books")

        var query = firestore.collection("books_data")
            .whereArrayContains("genres", "Action")
            .limit(booksPerPage.toLong())

        lastVisibleAction?.let { query = query.startAfter(it) }

        query.get()
            .addOnSuccessListener { documents ->
                Log.d("CategoryLoading", "Action books query returned ${documents.size()} documents")
                if (!documents.isEmpty) {
                    lastVisibleAction = documents.documents.last()
                    for (document in documents) {
                        val book = parseDocumentToBook(document)
                        actionBooks.add(book)
                        Log.d("CategoryLoading", "Added Action book: ${book.title}")
                    }
                    actionAdapter.notifyDataSetChanged()
                }
                isActionLoading = false
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error loading action books: ${exception.message}")
                isActionLoading = false
            }
    }

    private fun loadFictionBooks() {
        if (isFictionLoading) return
        isFictionLoading = true

        Log.d("CategoryLoading", "Loading Fiction books")

        var query = firestore.collection("books_data")
            .whereArrayContains("genres", "Fiction")
            .limit(booksPerPage.toLong())

        lastVisibleFiction?.let { query = query.startAfter(it) }

        query.get()
            .addOnSuccessListener { documents ->
                Log.d("CategoryLoading", "Fiction books query returned ${documents.size()} documents")
                if (!documents.isEmpty) {
                    lastVisibleFiction = documents.documents.last()
                    for (document in documents) {
                        val book = parseDocumentToBook(document)
                        fictionBooks.add(book)
                        Log.d("CategoryLoading", "Added Fiction book: ${book.title}")
                    }
                    fictionAdapter.notifyDataSetChanged()
                }
                isFictionLoading = false
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error loading fiction books: ${exception.message}")
                isFictionLoading = false
            }
    }

    private fun loadChildrenBooks() {
        if (isChildrenLoading) return
        isChildrenLoading = true

        Log.d("CategoryLoading", "Loading Children books")

        var query = firestore.collection("books_data")
            .whereArrayContains("genres", "Childrens")
            .limit(booksPerPage.toLong())

        lastVisibleChildren?.let { query = query.startAfter(it) }

        query.get()
            .addOnSuccessListener { documents ->
                Log.d("CategoryLoading", "Children books query returned ${documents.size()} documents")
                if (!documents.isEmpty) {
                    lastVisibleChildren = documents.documents.last()
                    for (document in documents) {
                        val book = parseDocumentToBook(document)
                        childrenBooks.add(book)
                        Log.d("CategoryLoading", "Added Children book: ${book.title}")
                    }
                    childrenAdapter.notifyDataSetChanged()
                }
                isChildrenLoading = false
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error loading children books: ${exception.message}")
                isChildrenLoading = false
            }
    }

    // Search Functions

    private fun switchToSearchMode() {
        if (!isInSearchMode) {
            isInSearchMode = true
            binding.scrollViewCategories.visibility = View.GONE
            binding.bookRV.visibility = View.VISIBLE
            Log.d("SearchMode", "Switched to search mode")
        }
    }

    private fun switchToCategoriesMode() {
        if (isInSearchMode) {
            isInSearchMode = false
            binding.searchMainpage.setQuery("", false)
            binding.searchMainpage.clearFocus()
            binding.scrollViewCategories.visibility = View.VISIBLE
            binding.bookRV.visibility = View.GONE
            Log.d("SearchMode", "Switched to categories mode")
        }
    }

    private fun clearSearchResults() {
        searchBookList.clear()
        searchAdapter.notifyDataSetChanged()
        lastVisibleSearchDocument = null
        isSearchLastPage = false
    }

    private fun searchBooks(query: String) {
        if (isSearchLoading) return
        isSearchLoading = true
        searchQuery = query

        // Only clear previous results if this is a new search
        if (lastVisibleSearchDocument == null) {
            searchBookList.clear()
            searchAdapter.notifyDataSetChanged()
        }

        binding.progressBar.visibility = View.VISIBLE

        // Format query: convert to lowercase and trim
        val formattedQuery = query.lowercase().trim()
        Log.d("Search", "Searching for: $formattedQuery")

        var firestoreQuery: Query = firestore.collection("books_data")
            .orderBy("title_lowercase")
            .startAt(formattedQuery)
            .endAt(formattedQuery + "\uf8ff")
            .limit(booksPerPage.toLong())

        lastVisibleSearchDocument?.let {
            firestoreQuery = firestoreQuery.startAfter(it)
        }

        firestoreQuery.get()
            .addOnSuccessListener { documents ->
                Log.d("Search", "Search query returned ${documents.size()} documents")
                if (!documents.isEmpty) {
                    lastVisibleSearchDocument = documents.documents.last()
                    val newBooks = ArrayList<Books>()

                    for (document in documents) {
                        val titleLowercase = document.getString("title_lowercase") ?: ""
                        // Check if title contains all words in the search query
                        if (formattedQuery.split(" ").all { titleLowercase.contains(it) }) {
                            val book = parseDocumentToBook(document)
                            newBooks.add(book)
                            Log.d("Search", "Added book to results: ${book.title}")
                        }
                    }

                    if (newBooks.isEmpty()) {
                        Log.d("Search", "No matching books found after filtering")
                        isSearchLastPage = documents.size() < booksPerPage
                    } else {
                        searchBookList.addAll(newBooks)
                        searchAdapter.notifyDataSetChanged()
                    }
                } else {
                    Log.d("Search", "No more results from Firestore")
                    isSearchLastPage = true
                }

                isSearchLoading = false
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error searching books: ${exception.message}")
                isSearchLoading = false
                binding.progressBar.visibility = View.GONE
            }
    }

    // Helper Functions

    private fun handleStringOrNaN(value: Any?): String {
        return when (value) {
            is String -> value
            is Number -> {
                val doubleValue = value.toDouble()
                if (doubleValue.isNaN()) "" else doubleValue.toString()
            }
            else -> ""
        }
    }

    private fun convertStringOrIntToInt(value: Any?): Int {
        return when (value) {
            is String -> value.toIntOrNull() ?: 0
            is Number -> value.toInt()
            else -> 0
        }
    }

    private fun convertStringOrIntToDouble(value: Any?): Double {
        return when (value) {
            is String -> value.toDoubleOrNull() ?: 0.0
            is Number -> value.toDouble()
            else -> 0.0
        }
    }

    private fun parseDocumentToBook(document: DocumentSnapshot): Books {
        val authors = convertStringOrIntToInt(document.get("authors"))
        val author_name = handleStringOrNaN(document.get("author_name"))
        val average_rating = convertStringOrIntToDouble(document.get("average_rating"))
        val book_id = convertStringOrIntToInt(document.get("book_id"))
        val description = handleStringOrNaN(document.get("description"))
        val format = handleStringOrNaN(document.get("format"))
        val genres = document.get("genres") as? List<String> ?: emptyList()
        val imageUrl = handleStringOrNaN(document.get("image_url"))
        val is_ebook = document.get("is_ebook") as? Boolean ?: false
        val isbn = handleStringOrNaN(document.get("isbn"))
        val isbn13 = handleStringOrNaN(document.get("isbn13"))
        val kindle_asin = handleStringOrNaN(document.get("kindle_asin"))
        val language_code = handleStringOrNaN(document.get("language_code"))
        val num_pages = convertStringOrIntToInt(document.get("num_pages"))
        val publication_day = document.get("publication_day") as? List<String> ?: emptyList()
        val publication_month = convertStringOrIntToInt(document.get("publication_month"))
        val publication_year = convertStringOrIntToInt(document.get("publication_year"))
        val publisher = handleStringOrNaN(document.get("publisher"))
        val title = handleStringOrNaN(document.get("title"))

        return Books(
            authors,
            author_name,
            average_rating,
            book_id,
            description,
            format,
            genres,
            imageUrl,
            is_ebook,
            isbn,
            isbn13,
            kindle_asin,
            language_code,
            num_pages,
            publication_day,
            publication_month,
            publication_year,
            publisher,
            title
        )
    }
}