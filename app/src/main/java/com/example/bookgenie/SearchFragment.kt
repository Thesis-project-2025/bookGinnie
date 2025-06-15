package com.example.bookgenie

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
// import android.widget.Spinner // Spinner'a doğrudan erişim gerekmiyorsa kaldırılabilir, binding üzerinden erişilecek
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bookgenie.databinding.FragmentSearchBinding
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// Sıralama kriterleri için enum (kullanıcının son verdiği haliyle)
enum class SortCriteria(val displayName: String) {
    NONE("Filter: No Sorting"),
    TITLE_ASC("Book Title (A-Z)"),
    TITLE_DESC("Book Title (Z-A)"),
    AUTHOR_ASC("Author (A-Z)"),
    AUTHOR_DESC("Author (Z-A)"),
    RATING_ASC("Rating (Low to High)"),
    RATING_DESC("Rating (High to Low)")
}

class SearchFragment : Fragment() {
    private lateinit var binding: FragmentSearchBinding
    private val firestore = FirebaseFirestore.getInstance()

    // Tür listesi
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

    private lateinit var genreAdapter: GenreAdapter
    private lateinit var bookAdapter: BookAdapter
    private val bookList = ArrayList<Books>() // Sıralanacak ana kitap listesi
    private var lastVisibleDocument: DocumentSnapshot? = null
    private var isLoading = false
    private var isLastPage = false
    private var searchQuery: String? = null
    private var selectedGenre: String? = null
    private val booksPerPage = 10

    private var isInGenreMode = true
    private var currentSortCriteria: SortCriteria = SortCriteria.NONE

    // SearchView listener'ını saklamak için üye değişken
    private var searchViewQueryTextListener: SearchView.OnQueryTextListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSearchBinding.inflate(inflater, container, false)
        Log.d("SearchFragment", "onCreateView çağrıldı.")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("SearchFragment", "onViewCreated çağrıldı. Mevcut selectedGenre: $selectedGenre, searchQuery: $searchQuery, currentSortCriteria: $currentSortCriteria")

        setupSortSpinner()
        setupSearchView()
        setupBackPressHandler()
        setupGenreRecyclerView()
        setupBookRecyclerView()

        if (selectedGenre != null) {
            Log.d("SearchFragment", "onViewCreated: '$selectedGenre' türü için görünüm geri yükleniyor. Sıralama: $currentSortCriteria")
            // Listener'ı geçici olarak kaldır
            binding.searchBar.setOnQueryTextListener(null)
            binding.searchBar.setQuery("", false)
            binding.searchBar.clearFocus()
            // Listener'ı geri yükle
            binding.searchBar.setOnQueryTextListener(searchViewQueryTextListener)

            binding.spinnerSortOptions.setSelection(currentSortCriteria.ordinal, false)
            lastVisibleDocument = null
            isLastPage = false
            loadBooksByGenre(selectedGenre!!)
        } else if (searchQuery != null && searchQuery!!.isNotEmpty()) {
            Log.d("SearchFragment", "onViewCreated: '$searchQuery' araması için görünüm geri yükleniyor.")
            binding.searchBar.setQuery(searchQuery, true)
        } else {
            Log.d("SearchFragment", "onViewCreated: Varsayılan olarak tür moduna geçiliyor.")
            switchToGenreMode()
        }
    }

    private fun setupSortSpinner() {
        val sortOptions = SortCriteria.values().map { it.displayName }.toTypedArray()
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSortOptions.adapter = spinnerAdapter

        binding.spinnerSortOptions.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedSort = SortCriteria.values()[position]
                if (currentSortCriteria != selectedSort) {
                    currentSortCriteria = selectedSort
                    Log.d("SearchFragment", "Sıralama kriteri değişti: $currentSortCriteria")
                    applySortingAndRefreshAdapter()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { /* No action */ }
        }
    }

    private fun applySortingAndRefreshAdapter() {
        if (!::bookAdapter.isInitialized) {
            updateSpinnerAndIconVisibility()
            return
        }
        Log.d("SearchFragment", "$currentSortCriteria sıralaması ${bookList.size} öğeye uygulanıyor")

        when (currentSortCriteria) {
            SortCriteria.TITLE_ASC -> bookList.sortBy { it.title.lowercase(Locale.getDefault()) }
            SortCriteria.TITLE_DESC -> bookList.sortByDescending { it.title.lowercase(Locale.getDefault()) }
            SortCriteria.AUTHOR_ASC -> bookList.sortBy { it.author_name.lowercase(Locale.getDefault()) }
            SortCriteria.AUTHOR_DESC -> bookList.sortByDescending { it.author_name.lowercase(Locale.getDefault()) }
            SortCriteria.RATING_ASC -> bookList.sortBy { it.average_rating }
            SortCriteria.RATING_DESC -> bookList.sortByDescending { it.average_rating }
            SortCriteria.NONE -> { /* No specific sort */ }
        }
        bookAdapter.notifyDataSetChanged()
        updateSpinnerAndIconVisibility()
    }

    private fun setupSearchView() {
        val searchView = binding.searchBar
        // SearchView içindeki EditText bileşenine erişiyoruz.
        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)

        //--- YAZI RENGİ DÜZELTMESİ BAŞLANGICI ---//
        try {
            // Rengi sabit bir dosyadan (örn: R.color.beige) almak yerine,
            // mevcut temanın birincil metin rengini (textColorPrimary) alıyoruz.
            // Bu, temanız açıkken koyu renk, koyu temadayken açık renk olmasını sağlar.
            val typedValue = android.util.TypedValue()
            requireContext().theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            val textColor = ContextCompat.getColor(requireContext(), typedValue.resourceId)

            // Aynı şekilde ipucu rengini (hint) de temadan alıyoruz.
            requireContext().theme.resolveAttribute(android.R.attr.textColorHint, typedValue, true)
            val hintColor = ContextCompat.getColor(requireContext(), typedValue.resourceId)

            // Elde ettiğimiz dinamik renkleri metin ve ipucu için atıyoruz.
            searchEditText.setTextColor(textColor)
            searchEditText.setHintTextColor(hintColor)

        } catch (e: Exception) {
            // Bir hata olması durumunda (örn: tema kaynağı bulunamazsa), güvenli bir renge geri dönebiliriz.
            Log.e("SearchFragment", "SearchView renkleri ayarlanırken hata oluştu.", e)
            try {
                // Güvenli fallback: Siyah metin, gri ipucu
                searchEditText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                searchEditText.setHintTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            } catch (e2: Resources.NotFoundException) {
                Log.e("SearchFragment", "Fallback renkleri de yüklenemedi.", e2)
            }
        }
        //--- YAZI RENGİ DÜZELTMESİ SONU ---//


        // Metodun geri kalanı sizin kodunuzdaki gibi, herhangi bir değişiklik gerekmiyor.
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if(selectedGenre == null) {
                    binding.genreTitle.visibility = View.GONE
                }
                updateSpinnerAndIconVisibility()
            }
        }

        var searchJob: Job? = null
        // Listener'ı oluşturup değişkene ata
        searchViewQueryTextListener = object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                searchJob?.cancel()
                if (newText.isEmpty()) {
                    if (!searchView.hasFocus() && !isInGenreMode) {
                        Log.d("SearchFragment", "onQueryTextChange: Metin boş, odak yok, tür modunda değil. Tür moduna geçiliyor.")
                        switchToGenreMode()
                    } else if (searchView.hasFocus()) {
                        Log.d("SearchFragment", "onQueryTextChange: Metin boş, odak var. Arama sonuçları temizleniyor.")
                        clearSearchResults()
                        binding.genreTitle.visibility = View.GONE
                        binding.rvBooks.visibility = View.VISIBLE
                        binding.rvGenres.visibility = View.GONE
                        updateSpinnerAndIconVisibility()
                    }
                } else {
                    if (isInGenreMode) {
                        selectedGenre = null
                        switchToSearchMode()
                    } else if (selectedGenre == null) {
                        binding.genreTitle.visibility = View.GONE
                    }
                    updateSpinnerAndIconVisibility()
                    searchJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(300)
                        lastVisibleDocument = null
                        isLastPage = false
                        searchBooks(newText.trim())
                    }
                }
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                searchJob?.cancel()
                if (query.isNotEmpty()) {
                    lastVisibleDocument = null
                    isLastPage = false
                    searchBooks(query.trim())
                }
                return true
            }
        }
        // Oluşturulan listener'ı SearchView'a ata
        searchView.setOnQueryTextListener(searchViewQueryTextListener)
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Log.d("SearchFragment", "Geri tuşuna basıldı. isInGenreMode: $isInGenreMode, Arama Sorgusu: '${binding.searchBar.query}'")
                    if (!binding.searchBar.query.isNullOrEmpty()){
                        // Listener'ı geçici olarak kaldır
                        binding.searchBar.setOnQueryTextListener(null)
                        binding.searchBar.setQuery("", false)
                        // Listener'ı geri yükle
                        binding.searchBar.setOnQueryTextListener(searchViewQueryTextListener)

                        if (!binding.searchBar.hasFocus() && !isInGenreMode) {
                            switchToGenreMode()
                        } else {
                            clearSearchResults()
                            binding.genreTitle.visibility = View.GONE
                            binding.rvBooks.visibility = View.VISIBLE
                            binding.rvGenres.visibility = View.GONE
                            updateSpinnerAndIconVisibility()
                        }
                    } else if (!isInGenreMode) {
                        switchToGenreMode()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun setupGenreRecyclerView() {
        binding.rvGenres.layoutManager = GridLayoutManager(requireContext(), 2)
        genreAdapter = GenreAdapter(requireContext(), genreList) { genre ->
            Log.d("SearchFragment", "Tür tıklandı: $genre")
            this.selectedGenre = genre
            this.searchQuery = null

            // Listener'ı geçici olarak kaldır
            binding.searchBar.setOnQueryTextListener(null)
            binding.searchBar.setQuery("", false)
            binding.searchBar.clearFocus()
            // Listener'ı geri yükle
            binding.searchBar.setOnQueryTextListener(searchViewQueryTextListener)

            currentSortCriteria = SortCriteria.NONE
            binding.spinnerSortOptions.setSelection(SortCriteria.NONE.ordinal, false)

            lastVisibleDocument = null
            isLastPage = false
            loadBooksByGenre(genre)
        }
        binding.rvGenres.adapter = genreAdapter
    }

    private fun setupBookRecyclerView() {
        binding.rvBooks.layoutManager = LinearLayoutManager(requireContext())
        bookAdapter = BookAdapter(requireContext(), bookList, "search_detailed")
        binding.rvBooks.adapter = bookAdapter

        binding.rvBooks.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val visibleItemCount = layoutManager.childCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && !isLastPage && totalItemCount > 0 &&
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount &&
                    firstVisibleItemPosition >= 0) {
                    selectedGenre?.let { loadBooksByGenre(it) } ?: searchQuery?.let { searchBooks(it) }
                }
            }
        })
    }

    private fun updateSpinnerAndIconVisibility() {
        val shouldShow = !isInGenreMode && binding.rvBooks.visibility == View.VISIBLE && bookList.isNotEmpty()
        binding.spinnerSortOptions.visibility = if (shouldShow) View.VISIBLE else View.GONE
        binding.ivSortIcon.visibility = if (shouldShow) View.VISIBLE else View.GONE
        Log.d("SearchFragment", "Spinner ve İkon görünürlüğü güncellendi: ${binding.spinnerSortOptions.visibility == View.VISIBLE}")
    }

    private fun switchToGenreMode() {
        Log.d("SearchFragment", "switchToGenreMode çağrıldı.")
        isInGenreMode = true
        binding.genreTitle.text = getString(R.string.explore_by_genre_text)
        binding.genreTitle.visibility = View.VISIBLE
        binding.rvGenres.visibility = View.VISIBLE
        binding.rvBooks.visibility = View.GONE
        binding.progressBar.visibility = View.GONE

        // Listener'ı geçici olarak kaldır
        binding.searchBar.setOnQueryTextListener(null)
        if (binding.searchBar.query.isNotEmpty()) {
            binding.searchBar.setQuery("", false)
        }
        binding.searchBar.clearFocus()
        // Listener'ı geri yükle
        binding.searchBar.setOnQueryTextListener(searchViewQueryTextListener)

        clearSearchResults()
        selectedGenre = null
        searchQuery = null
        currentSortCriteria = SortCriteria.NONE
        if(::binding.isInitialized && binding.spinnerSortOptions.adapter != null) { // Adapter null kontrolü eklendi
            binding.spinnerSortOptions.setSelection(SortCriteria.NONE.ordinal, false)
        }
        updateSpinnerAndIconVisibility()
    }

    private fun switchToSearchMode() {
        Log.d("SearchFragment", "switchToSearchMode çağrıldı. Seçili Tür: $selectedGenre")
        isInGenreMode = false
        if (selectedGenre == null) {
            binding.genreTitle.visibility = View.GONE
        } else {
            binding.genreTitle.text = selectedGenre
            binding.genreTitle.visibility = View.VISIBLE
        }
        binding.rvGenres.visibility = View.GONE
        binding.rvBooks.visibility = View.VISIBLE
    }

    private fun clearSearchResults() {
        Log.d("SearchFragment", "Kitap sonuçları temizleniyor. Önceki boyut: ${bookList.size}")
        val previousSize = bookList.size
        if (previousSize > 0) {
            bookList.clear()
            if (::bookAdapter.isInitialized) {
                bookAdapter.notifyItemRangeRemoved(0, previousSize)
            }
        }
        lastVisibleDocument = null
        isLastPage = false
    }

    private fun loadBooksByGenre(genre: String) {
        if (isLoading || !isAdded) {
            Log.w("SearchFragment", "loadBooksByGenre: Yükleme iptal edildi (isLoading=$isLoading, isAdded=$isAdded)")
            return
        }
        isLoading = true

        if (lastVisibleDocument == null) {
            Log.d("SearchFragment", "loadBooksByGenre: İlk yükleme için kitap listesi temizleniyor.")
            clearSearchResults()
        }

        switchToSearchMode()
        binding.progressBar.visibility = View.VISIBLE
        Log.d("SearchFragment", "'$genre' türü için kitaplar yükleniyor. Sayfalama Belirteci: $lastVisibleDocument")

        var query = firestore.collection("books_data")
            .whereArrayContains("genres", genre)
            .limit(booksPerPage.toLong())
        lastVisibleDocument?.let { query = query.startAfter(it) }

        query.get()
            .addOnSuccessListener { documents ->
                if (!isAdded) return@addOnSuccessListener
                val newBooks = ArrayList<Books>()
                if (!documents.isEmpty) {
                    lastVisibleDocument = documents.documents.last()
                    for (document in documents) {
                        newBooks.add(parseDocumentToBook(document))
                    }
                    val initialSize = bookList.size
                    bookList.addAll(newBooks)
                    Log.d("SearchFragment", "loadBooksByGenre: ${newBooks.size} yeni kitap eklendi. Toplam: ${bookList.size}")
                    if (::bookAdapter.isInitialized) {
                        if (initialSize == 0 && newBooks.isNotEmpty()) {
                            bookAdapter.notifyDataSetChanged()
                        } else if (newBooks.isNotEmpty()) {
                            bookAdapter.notifyItemRangeInserted(initialSize, newBooks.size)
                        }
                    }
                    isLastPage = documents.size() < booksPerPage
                } else {
                    isLastPage = true
                    if (bookList.isEmpty()) Log.d("SearchFragment", "'$genre' türü için kitap bulunamadı.")
                }
                applySortingAndRefreshAdapter()
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                if (!isAdded) return@addOnFailureListener
                Log.e("Firestore", "'$genre' türü için kitap yükleme hatası: ${exception.message}", exception)
                isLoading = false
                binding.progressBar.visibility = View.GONE
                updateSpinnerAndIconVisibility()
            }
    }

    private fun searchBooks(queryText: String) {
        if (isLoading || !isAdded) {
            Log.w("SearchFragment", "searchBooks: Yükleme iptal edildi (isLoading=$isLoading, isAdded=$isAdded)")
            return
        }
        isLoading = true
        // Arama yapıldığında selectedGenre null olmalı, searchQuery ise güncel queryText olmalı.
        // Bu atamalar onQueryTextChange veya onQueryTextSubmit içinde yapılmalı.
        // this.searchQuery = queryText // Bu zaten onQueryTextChange/Submit içinde güncelleniyor
        // this.selectedGenre = null   // Bu zaten onQueryTextChange/Submit içinde güncelleniyor

        switchToSearchMode()

        if (lastVisibleDocument == null) {
            Log.d("SearchFragment", "searchBooks: İlk arama için kitap listesi temizleniyor.")
            clearSearchResults()
        }
        binding.progressBar.visibility = View.VISIBLE
        Log.d("SearchFragment", "'$queryText' için kitap aranıyor. Sayfalama Belirteci: $lastVisibleDocument")

        val formattedQuery = queryText.lowercase(Locale.getDefault()).trim()
        var firestoreSearchQuery: Query = firestore.collection("books_data")
            .orderBy("title_lowercase")
            .startAt(formattedQuery)
            .endAt(formattedQuery + "\uf8ff")
            .limit(booksPerPage.toLong())
        lastVisibleDocument?.let { firestoreSearchQuery = firestoreSearchQuery.startAfter(it) }

        firestoreSearchQuery.get()
            .addOnSuccessListener { documents ->
                if (!isAdded) return@addOnSuccessListener
                val newBooks = ArrayList<Books>()
                if (!documents.isEmpty) {
                    lastVisibleDocument = documents.documents.last()
                    for (document in documents) {
                        val titleLowercase = document.getString("title_lowercase") ?: ""
                        if (formattedQuery.split(" ").all { word -> titleLowercase.contains(word) }) {
                            newBooks.add(parseDocumentToBook(document))
                        }
                    }
                    if (newBooks.isNotEmpty()) {
                        val initialSize = bookList.size
                        bookList.addAll(newBooks)
                        Log.d("SearchFragment", "searchBooks: ${newBooks.size} yeni kitap eklendi. Toplam: ${bookList.size}")
                        if (::bookAdapter.isInitialized) {
                            if (initialSize == 0 && newBooks.isNotEmpty()) {
                                bookAdapter.notifyDataSetChanged()
                            } else if (newBooks.isNotEmpty()) {
                                bookAdapter.notifyItemRangeInserted(initialSize, newBooks.size)
                            }
                        }
                    } else if (documents.size() > 0 && bookList.isEmpty()) {
                        Log.d("SearchFragment", "İstemci tarafı filtreleme sonrası '$queryText' için eşleşen kitap bulunamadı.")
                    }
                    isLastPage = documents.size() < booksPerPage
                } else {
                    isLastPage = true
                    if (bookList.isEmpty()) Log.d("SearchFragment", "'$queryText' için Firestore'dan sonuç gelmedi.")
                }
                applySortingAndRefreshAdapter()
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                if (!isAdded) return@addOnFailureListener
                Log.e("Firestore", "'$queryText' için kitap arama hatası: ${exception.message}", exception)
                isLoading = false
                binding.progressBar.visibility = View.GONE
                updateSpinnerAndIconVisibility()
            }
    }

    private fun handleStringOrNaN(value: Any?): String {
        return when (value) {
            is String -> value
            is Number -> {
                val doubleValue = value.toDouble()
                if (doubleValue.isNaN() || doubleValue.isInfinite()) "" else doubleValue.toString()
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
        val publication_day_raw = document.get("publication_day")
        val publication_day: List<String> = when (publication_day_raw) {
            is List<*> -> publication_day_raw.mapNotNull { it?.toString() }
            is String -> listOf(publication_day_raw)
            else -> emptyList()
        }
        val publication_month = convertStringOrIntToInt(document.get("publication_month"))
        val publication_year = convertStringOrIntToInt(document.get("publication_year"))
        val publisher = handleStringOrNaN(document.get("publisher"))
        val title = handleStringOrNaN(document.get("title"))
        val rating_count = convertStringOrIntToInt(document.get("rating_count"))
        val title_lowercase = handleStringOrNaN(document.get("title_lowercase")) // title_lowercase burada dolduruluyor

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
            title,
            rating_count,
            title_lowercase
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("SearchFragment", "onDestroyView çağrıldı.")
        // searchViewQueryTextListener = null // Listener'ı temizle
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selectedGenre", selectedGenre)
        outState.putString("searchQuery", searchQuery)
        outState.putSerializable("currentSortCriteria", currentSortCriteria)
        Log.d("SearchFragment", "onSaveInstanceState: selectedGenre=$selectedGenre, searchQuery=$searchQuery, sort=$currentSortCriteria")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            selectedGenre = savedInstanceState.getString("selectedGenre")
            searchQuery = savedInstanceState.getString("searchQuery")
            @Suppress("DEPRECATION")
            currentSortCriteria = savedInstanceState.getSerializable("currentSortCriteria") as? SortCriteria ?: SortCriteria.NONE
            Log.d("SearchFragment", "onCreate (restored): selectedGenre=$selectedGenre, searchQuery=$searchQuery, sort=$currentSortCriteria")
        }
    }
}
