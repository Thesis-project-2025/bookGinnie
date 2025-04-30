package com.example.bookgenie

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.bookgenie.databinding.FragmentSearchBinding
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

class SearchFragment : Fragment() {
    private lateinit var binding: FragmentSearchBinding
    private val firestore = FirebaseFirestore.getInstance()

    // Genre list for display
    private val genreList = listOf(
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
        "Supernatural", "High Fantasy", "Teen", "American", "Comic Book", "Drama", "Essays",
        "Food", "Romantic Suspense", "Anthologies", "Comedy", "Music", "Sports",
        "Christianity", "Novella", "Autobiography", "Superheroes", "Shapeshifters",
        "Mythology", "Detective", "Storytime", "Westerns", "Theology", "Cozy Mystery",
        "BDSM", "Action", "Juvenile", "Regency", "Feminism"
    )

    // Genre adapter
    private lateinit var genreAdapter: GenreAdapter

    // Search functionality variables
    private lateinit var bookAdapter: BookAdapter
    private val bookList = ArrayList<Books>()
    private var lastVisibleDocument: DocumentSnapshot? = null
    private var isLoading = false
    private var isLastPage = false
    private var searchQuery: String? = null
    private var selectedGenre: String? = null
    private val booksPerPage = 2 // Limit to 2 books per page as requested

    // View state
    private var isInGenreMode = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSearchBinding.inflate(inflater, container, false)

        binding.fab.setOnClickListener {
            Navigation.findNavController(it).navigate(R.id.searchToFairy)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSearchView()
        setupBackPressHandler()
        setupGenreRecyclerView()
        setupBookRecyclerView()
        setupBottomNavigation()

        // Default to genre mode when first opening
        switchToGenreMode()
    }

    private fun setupBottomNavigation() {
        val bottomNavigationView: BottomNavigationView = binding.bottomNavView

        // Set the current selected item
        bottomNavigationView.selectedItemId = R.id.idSearch

        bottomNavigationView.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.idMainPage -> {
                    findNavController().navigate(R.id.action_searchFragment_to_mainPageFragment)
                    true
                }
                R.id.idSettings -> {
                    // Make sure these IDs match your navigation graph
                    findNavController().navigate(R.id.action_searchFragment_to_settingsFragment)
                    true
                }
                R.id.idProfile -> {
                    findNavController().navigate(R.id.action_searchFragment_to_userInfoFragment)
                    true
                }
                R.id.idSearch -> {
                    // Already in search fragment, do nothing
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSearchView() {
        val searchView = binding.searchBar
        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)

        searchEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.beige))
        searchEditText.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.beige))

        // Switch to search mode when the search view gains focus
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                switchToSearchMode()
                Log.d("SearchFragment", "Switched to search mode due to focus")
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
                    Log.d("SearchFragment", "Cleared search results")
                } else {
                    switchToSearchMode() // Ensure we're in search mode

                    // Use debounce to prevent firing too many queries
                    searchJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(300) // Wait for 300ms before executing search
                        clearSearchResults() // Clear before new search
                        lastVisibleDocument = null // Reset for new search
                        searchBooks(newText.trim())
                        Log.d("SearchFragment", "Searching for: $newText")
                    }
                }
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                if (query.isNotEmpty()) {
                    switchToSearchMode() // Ensure we're in search mode
                    clearSearchResults() // Clear before new search
                    lastVisibleDocument = null // Reset for new search
                    searchBooks(query.trim())
                    Log.d("SearchFragment", "Search submitted: $query")
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
                    if (!isInGenreMode) {
                        switchToGenreMode()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressed()
                    }
                }
            }
        )
    }

    private fun setupGenreRecyclerView() {
        // Set up grid layout for genres (3 columns)
        binding.rvGenres.layoutManager = GridLayoutManager(requireContext(), 2)

        // Initialize genre adapter
        genreAdapter = GenreAdapter(requireContext(), genreList) { genre ->
            // Handle genre click
            selectedGenre = genre
            loadBooksByGenre(genre)
        }

        binding.rvGenres.adapter = genreAdapter
    }

    private fun setupBookRecyclerView() {
        // Set up staggered grid layout for books (2 columns)
        binding.rvBooks.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        // Initialize book adapter
        bookAdapter = BookAdapter(requireContext(), bookList, "search")
        binding.rvBooks.adapter = bookAdapter

        // Add scroll listener for pagination
        binding.rvBooks.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as StaggeredGridLayoutManager
                val totalItemCount = layoutManager.itemCount
                val visibleItemCount = layoutManager.childCount
                val firstVisibleItem = layoutManager.findFirstVisibleItemPositions(null).minOrNull() ?: 0

                if (!isLoading && !isLastPage &&
                    (visibleItemCount + firstVisibleItem) >= (totalItemCount * 0.9).toInt()
                ) {
                    // Load more books when scrolling near the end
                    selectedGenre?.let { loadBooksByGenre(it) } ?: searchQuery?.let { searchBooks(it) }
                }
            }
        })
    }

    private fun switchToGenreMode() {
        isInGenreMode = true
        binding.genreTitle.visibility = View.VISIBLE
        binding.rvGenres.visibility = View.VISIBLE
        binding.rvBooks.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.searchBar.setQuery("", false)
        binding.searchBar.clearFocus()

        // Reset search state
        clearSearchResults()
        selectedGenre = null

        Log.d("SearchFragment", "Switched to genre mode")
    }

    private fun switchToSearchMode() {
        isInGenreMode = false
        binding.genreTitle.visibility = View.GONE
        binding.rvGenres.visibility = View.GONE
        binding.rvBooks.visibility = View.VISIBLE

        Log.d("SearchFragment", "Switched to search mode")
    }

    private fun clearSearchResults() {
        bookList.clear()
        bookAdapter.notifyDataSetChanged()
        lastVisibleDocument = null
        isLastPage = false
    }

    private fun loadBooksByGenre(genre: String) {
        if (isLoading) return
        isLoading = true

        // If this is a new genre search, clear previous results
        if (selectedGenre != genre || lastVisibleDocument == null) {
            clearSearchResults()
            selectedGenre = genre
            switchToSearchMode()
        }

        binding.progressBar.visibility = View.VISIBLE

        Log.d("SearchFragment", "Loading books for genre: $genre")

        var query = firestore.collection("books_data")
            .whereArrayContains("genres", genre)
            .limit(booksPerPage.toLong())

        lastVisibleDocument?.let { query = query.startAfter(it) }

        query.get()
            .addOnSuccessListener { documents ->
                Log.d("SearchFragment", "Genre query returned ${documents.size()} documents")
                if (!documents.isEmpty) {
                    lastVisibleDocument = documents.documents.last()
                    for (document in documents) {
                        val book = parseDocumentToBook(document)
                        bookList.add(book)
                        Log.d("SearchFragment", "Added book: ${book.title}")
                    }
                    bookAdapter.notifyDataSetChanged()

                    // Check if we've reached the last page
                    isLastPage = documents.size() < booksPerPage
                } else {
                    isLastPage = true
                }

                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error loading books by genre: ${exception.message}")
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
    }

    private fun searchBooks(query: String) {
        if (isLoading) return
        isLoading = true
        searchQuery = query

        // Only clear previous results if this is a new search
        if (lastVisibleDocument == null) {
            bookList.clear()
            bookAdapter.notifyDataSetChanged()
        }

        binding.progressBar.visibility = View.VISIBLE

        // Format query: convert to lowercase and trim
        val formattedQuery = query.lowercase().trim()
        Log.d("SearchFragment", "Searching for: $formattedQuery")

        var firestoreQuery: Query = firestore.collection("books_data")
            .orderBy("title_lowercase")
            .startAt(formattedQuery)
            .endAt(formattedQuery + "\uf8ff")
            .limit(booksPerPage.toLong())

        lastVisibleDocument?.let {
            firestoreQuery = firestoreQuery.startAfter(it)
        }

        firestoreQuery.get()
            .addOnSuccessListener { documents ->
                Log.d("SearchFragment", "Search query returned ${documents.size()} documents")
                if (!documents.isEmpty) {
                    lastVisibleDocument = documents.documents.last()
                    val newBooks = ArrayList<Books>()

                    for (document in documents) {
                        val titleLowercase = document.getString("title_lowercase") ?: ""
                        // Check if title contains all words in the search query
                        if (formattedQuery.split(" ").all { titleLowercase.contains(it) }) {
                            val book = parseDocumentToBook(document)
                            newBooks.add(book)
                            Log.d("SearchFragment", "Added book to results: ${book.title}")
                        }
                    }

                    if (newBooks.isEmpty()) {
                        Log.d("SearchFragment", "No matching books found after filtering")
                        isLastPage = documents.size() < booksPerPage
                    } else {
                        bookList.addAll(newBooks)
                        bookAdapter.notifyDataSetChanged()
                    }
                } else {
                    Log.d("SearchFragment", "No more results from Firestore")
                    isLastPage = true
                }

                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error searching books: ${exception.message}")
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
    }

    // Helper Functions (copied from MainPageFragment)
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