package com.example.bookgenie

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bookgenie.databinding.CardDesignBinding
import com.bumptech.glide.Glide

class BookAdapter(
    private var mContext: Context,
    private var bookList: List<Books>,
    private val fragmentType: String = "main" // Add a parameter to identify the fragment
) : RecyclerView.Adapter<BookAdapter.BookHolder>() {


    inner class BookHolder(var tasarim: CardDesignBinding) : RecyclerView.ViewHolder(tasarim.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookHolder {
        val binding = CardDesignBinding.inflate(LayoutInflater.from(mContext), parent, false)
        return BookHolder(binding)
    }

    override fun getItemCount(): Int {
        return bookList.size
    }

    override fun onBindViewHolder(holder: BookHolder, position: Int) {
        val book = bookList[position]
        val t = holder.tasarim

        // Load the book's image using Glide
        Glide.with(mContext)
            .load(book.imageUrl)
            .placeholder(R.drawable.img)
            .into(t.imageViewBook)

        // Set an OnClickListener that checks which fragment we're in
        t.root.setOnClickListener {
            try {
                // Navigate based on the fragment type
                if (fragmentType == "main") {
                    val action = MainPageFragmentDirections.mainToBookDetails(book)
                    it.findNavController().navigate(action)
                } else {
                    val action = SearchFragmentDirections.searchToBookDetails(book)
                    it.findNavController().navigate(action)
                }
            } catch (e: Exception) {
                // Log the error but prevent crash
                Log.e("BookAdapter", "Navigation error: ${e.message}")
            }
        }
    }

    fun updateList(newBooks: List<Books>) {
        bookList = newBooks
        notifyDataSetChanged()
    }

}

