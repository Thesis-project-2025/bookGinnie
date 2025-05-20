package com.example.bookgenie

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bookgenie.databinding.ItemCarouselBookBinding // Import view binding

class CarouselBookAdapter(
    private val context: Context,
    private val bookList: List<Books>
) : RecyclerView.Adapter<CarouselBookAdapter.CarouselViewHolder>() {

    inner class CarouselViewHolder(val binding: ItemCarouselBookBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val book = bookList[position]
                    // Navigate to book detail, similar to BookAdapter
                    // Ensure you have the correct action ID in your nav_graph.xml
                    val action = MainPageFragmentDirections.mainToBookDetails(book)
                    try {
                        Navigation.findNavController(it).navigate(action)
                    } catch (e: IllegalStateException) {
                        // Handle cases where NavController might not be found (e.g. view not fully attached)
                        android.util.Log.e("CarouselAdapter", "Navigation failed: ${e.message}")
                    } catch (e: IllegalArgumentException) {
                        // Handle cases where action is not found
                        android.util.Log.e("CarouselAdapter", "Navigation action not found: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
        val binding = ItemCarouselBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CarouselViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
        val book = bookList[position]
        holder.binding.tvCarouselBookTitle.text = book.title
        Glide.with(context)
            .load(book.imageUrl)
            .placeholder(R.drawable.book_placeholder) // Add a placeholder drawable
            .error(R.drawable.book_placeholder_error) // Add an error drawable
            .into(holder.binding.ivCarouselBookImage)
    }

    override fun getItemCount(): Int = bookList.size
}