package com.example.bookgenie

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.bookgenie.databinding.FragmentBookDetailsBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class BookDetailsFragment : Fragment() {
private lateinit var binding: FragmentBookDetailsBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        binding= FragmentBookDetailsBinding.inflate(inflater, container, false)

        val bundle: BookDetailsFragmentArgs by navArgs()
        val book = bundle.book

        binding.toolbarBookDetails.title = book.title

        // Using Glide to load the book image
        Glide.with(requireContext())
            .load(book.imageUrl) // URL'den resmi yükle
            .placeholder(R.drawable.img) // Yüklenene kadar gösterilecek resim
            .into(binding.ivBook) // ImageView'a yükle

        // Setting the title of the book
        binding.tvBook.text = book.title
        binding.tvAuthor.text = book.authors.toString()
        binding.tvPages.text = book.num_pages.toString()
        binding.tvYear.text = book.publication_year.toString()
        binding.tvRating.text = book.average_rating.toString()

        val bottomNavigationView: BottomNavigationView = binding.bottomNavigationView2
        bottomNavigationView.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.idMainPage -> {
                    findNavController().navigate(R.id.bookDetailsToMainPage)
                    true
                }
                R.id.idSettings -> {
                    // Navigate to the Settings Fragment
                    findNavController().navigate(R.id.bookDetailsToSettings)
                    true
                }
                R.id.idUserInfo -> {
                    // Navigate to the User Info Fragment
                    findNavController().navigate(R.id.bookDetailsToUserInfo)
                    true
                }
                else -> false
            }
        }

        return binding.root
    }


}
