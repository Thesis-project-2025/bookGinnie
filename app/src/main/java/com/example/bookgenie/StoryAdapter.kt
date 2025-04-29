package com.example.bookgenie

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.bookgenie.databinding.ItemCardBinding

class StoryAdapter(
    private val storyList: List<String>,
    private val onItemClick: (position: Int) -> Unit
) : RecyclerView.Adapter<StoryAdapter.StoryViewHolder>() {

    private var selectedPosition = -1

    inner class StoryViewHolder(val binding: ItemCardBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    selectedPosition = position
                    onItemClick(position)
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
        holder.binding.textView.text = storyList[position]

        if (position == selectedPosition) {
            holder.binding.root.setBackgroundColor(holder.itemView.context.getColor(R.color.selectedColor))
        } else {
            holder.binding.root.setBackgroundColor(holder.itemView.context.getColor(R.color.deselectedColor))
        }

    }

    override fun getItemCount() = storyList.size
}
