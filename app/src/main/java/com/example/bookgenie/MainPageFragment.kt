package com.example.bookgenie

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.bookgenie.databinding.FragmentMainPageBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import androidx.appcompat.app.AlertDialog

class MainPageFragment : Fragment() {
    private lateinit var binding: FragmentMainPageBinding
    private lateinit var adapter: BookAdapter
    private val bookList = ArrayList<Books>()
    private val firestore = FirebaseFirestore.getInstance()
    private var lastVisibleDocument: DocumentSnapshot? = null
    private var isLoading = false
    private var isLastPage = false
    private var isSearchMode = false
    private var searchQuery: String? = null
    private var lastVisibleSearchDocument: DocumentSnapshot? = null
    private val booksPerPage = 2
    private val genreList = listOf(    "Fiction", "Romance", "Nonfiction", "Fantasy", "Contemporary", "Mystery",
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
        "BDSM", "Action", "Juvenile", "Regency", "Feminism") // Örnek genre listesi
    private val selectedGenres = mutableListOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentMainPageBinding.inflate(inflater, container, false)

        binding.toolbarMainPage.title = "bookGenie"

        // SearchView dinleyicisi ekleme
        binding.searchMainpage.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                if (newText.isEmpty()) {
                    resetToMainPage()
                } else {
                    switchToSearchMode(newText.trim())
                }
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                if (query.isEmpty()) {
                    resetToMainPage()
                } else {
                    switchToSearchMode(query.trim())
                }
                return true
            }
        })

        binding.bookRV.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        adapter = BookAdapter(requireContext(), bookList)
        binding.bookRV.adapter = adapter
        binding.filterButton.setOnClickListener {
            showFilterDialog()
        }
        setupRecyclerViewScrollListener()
        fetchBooksFromFirestore()

        val bottomNavigationView: BottomNavigationView = binding.bottomNavigationView
        bottomNavigationView.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.idMainPage -> true
                R.id.idSettings -> {
                    findNavController().navigate(R.id.mainPageToSettings)
                    true
                }
                R.id.idUserInfo -> {
                    findNavController().navigate(R.id.mainPageToUserInfo)
                    true
                }
                else -> false
            }
        }

        return binding.root
    }
    private fun showFilterDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Filter by Genre")

        val selected = BooleanArray(genreList.size) // Checkbox durumlarını takip için
        builder.setMultiChoiceItems(genreList.toTypedArray(), selected) { _, which, isChecked ->
            if (isChecked) {
                selectedGenres.add(genreList[which])
            } else {
                selectedGenres.remove(genreList[which])
            }
        }

        builder.setPositiveButton("Apply") { _, _ ->
            applyGenreFilter()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }
    private fun applyGenreFilter() {
        if (selectedGenres.isEmpty()) {
            resetToMainPage()
            return
        }

        isSearchMode = false
        bookList.clear()
        adapter.notifyDataSetChanged()
        isLastPage = false
        lastVisibleDocument = null

        fetchBooksByGenres()
    }

    private fun fetchBooksByGenres() {
        if (isLoading || isLastPage) return
        isLoading = true
        binding.progressBar.visibility = View.VISIBLE

        val query = firestore.collection("books_data")
            .whereArrayContainsAny("genres", selectedGenres) // İlk filtreleme "OR" mantığı
            .limit(booksPerPage.toLong())

        lastVisibleDocument?.let {
            query.startAfter(it)
        }

        query.get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    lastVisibleDocument = documents.documents.last()
                    for (document in documents) {
                        val book = parseDocumentToBook(document)
                        // İstemci tarafında "AND" kontrolü
                        if (selectedGenres.all { genre -> book.genres.contains(genre) }) {
                            bookList.add(book)
                        }
                    }
                    adapter.notifyDataSetChanged()
                } else {
                    isLastPage = true
                }
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error fetching books by genre: ${exception.message}")
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
    }




    private fun resetToMainPage() {
        isSearchMode = false
        searchQuery = null
        lastVisibleSearchDocument = null
        bookList.clear()
        adapter.notifyDataSetChanged()
        lastVisibleDocument = null
        isLastPage = false
        fetchBooksFromFirestore()
    }

    private fun switchToSearchMode(query: String) {
        isSearchMode = true
        searchQuery = query
        lastVisibleSearchDocument = null
        bookList.clear()
        adapter.notifyDataSetChanged()
        isLastPage = false
        searchBooksFromFirestore(query)
    }

    private fun fetchBooksFromFirestore() {
        if (isLoading || isLastPage) return
        isLoading = true
        binding.progressBar.visibility = View.VISIBLE

        var query: Query = firestore.collection("books_data")
            .orderBy("title")
            .limit(booksPerPage.toLong())

        lastVisibleDocument?.let {
            query = query.startAfter(it)
        }

        query.get()
            .addOnSuccessListener { documents ->
                Log.d("Firestore", "Çekilen veri sayısı: ${documents.size()}")
                if (!documents.isEmpty) {
                    lastVisibleDocument = documents.documents.last()
                    for (document in documents) {
                        val book = parseDocumentToBook(document)
                        bookList.add(book)
                    }
                    adapter.notifyDataSetChanged()
                } else {
                    isLastPage = true
                }
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error fetching books: ${exception.message}")
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
    }


    private fun searchBooksFromFirestore(searchWord: String) {
        if (isLoading) return
        isLoading = true
        binding.progressBar.visibility = View.VISIBLE

        // Küçük harfe çevir ve başına/sonuna gerekli wildcard'ı ekle
        val formattedQuery = searchWord.lowercase().trim()

        var query: Query = firestore.collection("books_data")
            .orderBy("title_lowercase")
            .startAt(formattedQuery)
            .endAt(formattedQuery + "\uf8ff")
            .limit(booksPerPage.toLong())

        lastVisibleSearchDocument?.let {
            query = query.startAfter(it)
        }

        query.get()
            .addOnSuccessListener { documents ->
                Log.d("Firestore", "Çekilen veri sayısı: ${documents.size()}")
                if (!documents.isEmpty) {
                    lastVisibleSearchDocument = documents.documents.last()
                    for (document in documents) {
                        val title = document.getString("title_lowercase") ?: ""
                        // Arama sonucunun tüm kelimeleri içermesi gerektiğini kontrol et
                        if (formattedQuery.split(" ").all { title.contains(it) }) {
                            val book = parseDocumentToBook(document)
                            bookList.add(book)
                        }
                    }
                    adapter.notifyDataSetChanged()
                } else {
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


    private fun setupRecyclerViewScrollListener() {
        binding.bookRV.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as StaggeredGridLayoutManager
                val totalItemCount = layoutManager.itemCount
                val visibleItemCount = layoutManager.childCount
                val firstVisibleItem = layoutManager.findFirstVisibleItemPositions(null).minOrNull() ?: 0

                if (!isLoading && !isLastPage && (visibleItemCount + firstVisibleItem) >= (totalItemCount * 0.9).toInt()) {
                    if (isSearchMode) {
                        searchBooksFromFirestore(searchQuery ?: "")
                    } else {
                        fetchBooksFromFirestore()
                    }
                }
            }
        })
    }

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
