package com.example.bookgenie

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bookgenie.databinding.ItemUserRatedBookBinding // Yeni layout için binding
import java.util.Locale

class UserRatedBooksAdapter(
    private val context: Context,
    private var ratedBooksList: List<RatedBookDisplay>
) : RecyclerView.Adapter<UserRatedBooksAdapter.RatedBookViewHolder>() {

    inner class RatedBookViewHolder(val binding: ItemUserRatedBookBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val ratedBookDisplay = ratedBooksList[position]
                    // BookDetailsFragment'a gitmek için Books nesnesini kullan
                    // NavGraph'ta UserRatingsFragment'tan BookDetailsFragment'a bir action tanımlı olmalı
                    // ve bu action 'book' adında bir Books argümanı kabul etmeli.
                    try {
                        val action = UserRatingsFragmentDirections.actionUserRatingsFragmentToBookDetailsFragment(ratedBookDisplay.book)
                        it.findNavController().navigate(action)
                    } catch (e: Exception) {
                        Log.e("UserRatedBooksAdapter", "Navigation to BookDetailsFragment failed: ${e.message}", e)
                        // Toast.makeText(context, "Could not open book details.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RatedBookViewHolder {
        val binding = ItemUserRatedBookBinding.inflate(LayoutInflater.from(context), parent, false)
        return RatedBookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RatedBookViewHolder, position: Int) {
        val ratedBookDisplay = ratedBooksList[position]
        val book = ratedBookDisplay.book
        val userRating = ratedBookDisplay.userRating

        holder.binding.tvRatedBookTitle.text = book.title.ifEmpty { "N/A" }
        holder.binding.tvRatedBookAuthor.text = book.author_name.ifEmpty { "N/A" }

        // Kullanıcının verdiği oyu RatingBar'da göster
        holder.binding.rbUserBookRating.rating = userRating
        // Kullanıcının verdiği oyu metin olarak göster "(X.X)"
        holder.binding.tvUserBookRatingValue.text = String.format(Locale.US, "(%.1f)", userRating)


        Glide.with(context)
            .load(book.imageUrl)
            .placeholder(R.drawable.img) // Placeholder resminiz
            .error(R.drawable.img) // Hata durumunda gösterilecek resim
            .into(holder.binding.ivRatedBookCover)
    }

    override fun getItemCount(): Int = ratedBooksList.size

    fun updateList(newRatedBooks: List<RatedBookDisplay>) {
        ratedBooksList = newRatedBooks
        notifyDataSetChanged() // Daha verimli güncellemeler için DiffUtil kullanabilirsiniz
    }
}
