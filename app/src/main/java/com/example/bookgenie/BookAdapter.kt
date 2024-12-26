package com.example.bookgenie

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bookgenie.databinding.CardDesignBinding
import com.bumptech.glide.Glide

class BookAdapter(var mContext: Context, var bookList: List<Books>) : RecyclerView.Adapter<BookAdapter.BookHolder>() {

    inner class BookHolder(var tasarim: CardDesignBinding) : RecyclerView.ViewHolder(tasarim.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookHolder {
        val binding = CardDesignBinding.inflate(LayoutInflater.from(mContext), parent, false)
        return BookHolder(binding)
    }

    override fun getItemCount(): Int {
        return bookList.size
    }

    override fun onBindViewHolder(holder: BookHolder, position: Int) {
        val book = bookList[position] // Get the book item at the current position
        val t = holder.tasarim // View Binding reference

        // Set the title of the book
        t.textViewTitle.text = book.title

        // Load the book's image using Glide
        Glide.with(mContext)
            .load(book.imageUrl) // Load the image from the URL
            .placeholder(R.drawable.img) // Placeholder image
            .into(t.imageViewBook) // Set the image to ImageView

        // Set an OnClickListener to navigate to the BookDetailsFragment when the item is clicked
        t.root.setOnClickListener {
            val action = MainPageFragmentDirections
                .mainToBookDetails(book) // Pass the selected book to BookDetailsFragment
            it.findNavController().navigate(action) // Use NavController to navigate
        }
    }

    fun updateList(newBooks: List<Books>) {
        bookList = newBooks
        notifyDataSetChanged()
    }

    }

