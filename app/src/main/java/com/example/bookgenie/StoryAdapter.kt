package com.example.bookgenie

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.bookgenie.databinding.ItemCardBinding
import com.google.firebase.firestore.FirebaseFirestore
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator


class StoryAdapter(
    private val stories: List<Story>,
    private val onItemClick: (Story) -> Unit
) : RecyclerView.Adapter<StoryAdapter.StoryViewHolder>() {

    private var selectedPosition = -1

    inner class StoryViewHolder(val binding: ItemCardBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    selectedPosition = position
                    onItemClick(stories[position]) // Send the full Story object, not just a String
                    notifyDataSetChanged()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
        val binding = ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
        val story = stories[position]
        val context = holder.itemView.context

        val cardView = holder.binding.root
        val cardTitle = holder.binding.cardTitle
        val cardIcon = holder.binding.cardIcon

        // Renkleri ayarla
        if (position == selectedPosition) {
            val selectedColor = ContextCompat.getColor(context, R.color.selectedColor)
            val currentColor = (cardView.cardBackgroundColor?.defaultColor ?: selectedColor)
            val colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, selectedColor)
            colorAnim.duration = 300
            colorAnim.addUpdateListener {
                cardView.setCardBackgroundColor(it.animatedValue as Int)
            }
            colorAnim.start()

            // Elevation ve scale ile görsel etki
            cardView.cardElevation = 16f
            cardView.scaleX = 1.05f
            cardView.scaleY = 1.05f

        } else {
            val pastelColors = listOf(
                R.color.mavi,
                R.color.pembe,
                R.color.mandalina,
                R.color.sari
            )
            val color = pastelColors[position % pastelColors.size]
            cardView.setCardBackgroundColor(ContextCompat.getColor(context, color))

            cardView.cardElevation = 4f
            cardView.scaleX = 1.0f
            cardView.scaleY = 1.0f
        }

        // Başlık ve ikon
        cardTitle.text = story.title

        val icons = listOf(
            R.drawable.ic_star_filled,
            R.drawable.flower,
            R.drawable.fish,
            R.drawable.elephant
        )
        val iconRes = icons[position % icons.size]
        cardIcon.setImageResource(iconRes)

    }

    override fun getItemCount() = stories.size
}
