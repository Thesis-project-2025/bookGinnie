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

class MainPageFragment : Fragment() {
    private lateinit var binding: FragmentMainPageBinding
    private lateinit var adapter: BookAdapter
    private val bookList = ArrayList<Books>()
    private val firestore = FirebaseFirestore.getInstance()
    private var lastVisibleDocument: DocumentSnapshot? = null
    private var isLoading = false
    private var isLastPage = false // Daha fazla veri olup olmadığını kontrol eder
    private val booksPerPage = 2 // Dinamik limit

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentMainPageBinding.inflate(inflater, container, false)

        binding.toolbarMainPage.title = "bookGenie"

        binding.searchMainpage.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                search(newText)
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                search(query)
                return true
            }
        })

        binding.bookRV.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        adapter = BookAdapter(requireContext(), bookList)
        binding.bookRV.adapter = adapter

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

    private fun search(searchWord: String) {
        val filteredList = bookList.filter { book ->
            book.title.contains(searchWord, ignoreCase = true) ||
                    book.authors.toString().contains(searchWord, ignoreCase = true)
        }
        adapter.updateList(filteredList)
    }
    private fun handleStringOrNaN(value: Any?): String {
        return when (value) {
            is String -> value // Eğer String ise doğrudan döndür
            is Number -> {
                val doubleValue = value.toDouble() // Number'ı Double'a dönüştür
                if (doubleValue.isNaN()) "" else doubleValue.toString()
            }
            else -> "" // Diğer durumlarda boş değer döndür
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
    private fun fetchBooksFromFirestore() {
        if (isLoading || isLastPage) return // Zaten yükleme yapılıyorsa veya veri bitmişse devam etme
        isLoading = true

        // ProgressBar'ı görünür yap
        binding.progressBar.visibility = View.VISIBLE

        var query: Query = firestore.collection("books").limit(booksPerPage.toLong())
        lastVisibleDocument?.let {
            query = query.startAfter(it)
        }

        query.get()
            .addOnSuccessListener { documents ->
                Log.d("Firestore", "Çekilen veri sayısı: ${documents.size()}")
                if (!documents.isEmpty) {
                    lastVisibleDocument = documents.documents.last()
                    for (document in documents) {
                        val asin = handleStringOrNaN(document.get("asin"))
                        val authors = convertStringOrIntToInt(document.get("authors"))
                        val average_rating = convertStringOrIntToDouble(document.get("average_rating"))
                        val book_id = convertStringOrIntToInt(document.get("book_id"))
                        val country_code = handleStringOrNaN(document.get("country_code"))
                        val description = handleStringOrNaN(document.get("description"))
                        val edition_information = handleStringOrNaN(document.get("edition_information"))
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
                        val title_without_series = handleStringOrNaN(document.get("title_without_series"))

                        bookList.add(
                            Books(
                                asin,
                                authors,
                                average_rating,
                                book_id,
                                country_code,
                                description,
                                edition_information,
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
                                title,
                                title_without_series
                            )
                        )
                    }
                    adapter.notifyDataSetChanged()
                } else {
                    isLastPage = true // Daha fazla veri yok
                }
                binding.progressBar.visibility = View.GONE
                isLoading = false
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error fetching books: ${exception.message}")
                binding.progressBar.visibility = View.GONE
                isLoading = false
            }
    }


    private fun setupRecyclerViewScrollListener() {
        binding.bookRV.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as StaggeredGridLayoutManager
                val visibleItemPositions = layoutManager.findLastVisibleItemPositions(null)

                // En son görünen elemanı bul
                val lastVisibleItem = visibleItemPositions.maxOrNull() ?: 0

                // Toplam öğe sayısını al
                val totalItemCount = layoutManager.itemCount

                // Eğer en son görünen öğe toplam öğe sayısından 1 eksikse, veri çek
                if (!isLoading && !isLastPage && lastVisibleItem >= totalItemCount - 1) {
                    fetchBooksFromFirestore()
                }
            }
        })
    }

}
