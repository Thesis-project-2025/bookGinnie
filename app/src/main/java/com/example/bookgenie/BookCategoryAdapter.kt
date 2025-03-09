package com.example.bookgenie

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bookgenie.databinding.CardDesignHorizontalBinding

class BookCategoryAdapter(
    private val context: Context,
    private var bookList: List<Books> = emptyList(),
    private val onLoadMore: () -> Unit
) : RecyclerView.Adapter<BookCategoryAdapter.BookHolder>() {

    inner class BookHolder(val binding: CardDesignHorizontalBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookHolder {
        val binding = CardDesignHorizontalBinding.inflate(LayoutInflater.from(context), parent, false)
        return BookHolder(binding)
    }

    override fun getItemCount(): Int = bookList.size

    override fun onBindViewHolder(holder: BookHolder, position: Int) {
        val book = bookList[position]

        // Load book cover image
        Glide.with(context)
            .load(book.imageUrl)
            .placeholder(R.drawable.img)
            .into(holder.binding.imageViewBook)

        // Set click listener to navigate to book details
        holder.binding.root.setOnClickListener {
            val action = MainPageFragmentDirections.mainToBookDetails(book)
            it.findNavController().navigate(action)
        }

        // Check if we're near the end of the list to load more items
        if (position >= itemCount - 2) {
            onLoadMore()
        }
    }

    fun updateList(newBooks: List<Books>) {
        bookList = newBooks
        notifyDataSetChanged()
    }
}