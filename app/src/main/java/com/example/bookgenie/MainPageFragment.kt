package com.example.bookgenie

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.bookgenie.databinding.FragmentMainPageBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class MainPageFragment : Fragment() {
    private lateinit var binding:FragmentMainPageBinding

    private lateinit var adapter: BookAdapter
    private val bookList = ArrayList<Books>()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentMainPageBinding.inflate(inflater, container, false)

        binding.toolbarMainPage.title = "bookGenie"

        binding.searchMainpage.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                search(newText) // Kullanıcı yazdıkça arama yapılır
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                search(query) // Kullanıcı arama düğmesine bastığında arama yapılır
                return true
            }
        })

        binding.bookRV.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        adapter = BookAdapter(requireContext(), bookList) // Adapter'i başlatın
        binding.bookRV.adapter = adapter

        fetchBooksFromFirestore()

        val bottomNavigationView: BottomNavigationView = binding.bottomNavigationView
        bottomNavigationView.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.idMainPage -> {
                    // Already in MainPageFragment, no navigation needed
                    true
                }
                R.id.idSettings -> {
                    // Navigate to the Settings Fragment
                    findNavController().navigate(R.id.mainPageToSettings)
                    true
                }
                R.id.idUserInfo -> {
                    // Navigate to the User Info Fragment
                    findNavController().navigate(R.id.mainPageToUserInfo)
                    true
                }
                else -> false
            }
        }

        return binding.root

    }

    private fun search(searchWord: String) {
        Log.e("search a book", searchWord)
    }


    private fun fetchBooksFromFirestore() {
        firestore.collection("books")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val asin = document.getString("asin") ?: ""
                    val authors = document.getLong("authors")?.toInt() ?: 0
                    val average_rating = document.getDouble("average_rating") ?: 0.0
                    val book_id = document.getLong("book_id")?.toInt() ?: 0
                    val country_code = document.getString("country_code") ?: ""
                    val description = document.getString("description") ?: ""
                    val edition_information = document.getString("edition_information") ?: ""
                    val format = document.getString("format") ?: ""
                    val genres = document.get("genres") as? List<String> ?: emptyList()
                    val imageUrl = document.getString("image_url") ?: ""
                    val is_ebook = document.getBoolean("is_ebook") ?: false
                    val isbn = document.getString("isbn") ?: ""
                    val isbn13 = document.getString("isbn13") ?: ""
                    val kindle_asin = document.getString("kindle_asin") ?: ""
                    val language_code = document.getString("language_code") ?: ""
                    val num_pages = document.getLong("num_pages")?.toInt() ?: 0
                    val publication_day = document.get("publication_day") as? List<String> ?: emptyList()
                    val publication_month = document.getLong("publication_month")?.toInt() ?: 0
                    val publication_year = document.getLong("publication_year")?.toInt() ?: 0
                    val publisher = document.getString("publisher") ?: ""
                    val title = document.getString("title") ?: ""
                    val title_without_series = document.getString("title_without_series") ?: ""

                    // Kitap nesnesini listeye ekle
                    bookList.add(Books(asin,authors,average_rating,book_id,country_code,description,edition_information,
                        format,genres,imageUrl,is_ebook,isbn,isbn13,kindle_asin,language_code,num_pages,publication_day,publication_month,publication_year,
                        publisher,title,title_without_series))
                }
                adapter.notifyDataSetChanged() // Adapter'i güncelle
            }
    }

}

