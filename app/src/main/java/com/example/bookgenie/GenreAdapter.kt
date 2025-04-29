package com.example.bookgenie

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class GenreAdapter(
    private val context: Context,
    private val genreList: List<String>,
    private val onGenreClick: (String) -> Unit
) : RecyclerView.Adapter<GenreAdapter.GenreViewHolder>() {

    // Generate a list of pastel colors for genre cards
    private val colorList = listOf(
        R.color.secondBlue,
        R.color.blue3,
        R.color.mainBlue
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.genre_item, parent, false)
        return GenreViewHolder(view)
    }

    override fun onBindViewHolder(holder: GenreViewHolder, position: Int) {
        val genre = genreList[position]

        // Assign color based on position (cycle through colors)
        val colorResId = colorList[position % colorList.size]

        holder.genreCardView.setCardBackgroundColor(ContextCompat.getColor(context, colorResId))
        holder.genreTextView.text = genre

        // Set click listener
        holder.itemView.setOnClickListener {
            onGenreClick(genre)
        }
    }

    override fun getItemCount(): Int = genreList.size

    class GenreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val genreCardView: CardView = itemView.findViewById(R.id.genreCardView)
        val genreTextView: TextView = itemView.findViewById(R.id.genreTextView)
    }
}