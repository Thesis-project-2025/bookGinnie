package com.example.bookgenie

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bookgenie.databinding.ItemBookDetailedBinding // CardDesignBinding yerine bunu kullanacağız
import java.util.Locale

class BookAdapter(
    private var mContext: Context,
    private var bookList: List<Books>,
    private val fragmentType: String = "main" // Bu parametre navigasyon için kullanılıyor
) : RecyclerView.Adapter<BookAdapter.BookHolder>() {

    // ViewHolder'ı ItemBookDetailedBinding kullanacak şekilde güncelliyoruz
    inner class BookHolder(var binding: ItemBookDetailedBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookHolder {
        // Yeni layout'umuzu inflate ediyoruz
        val binding = ItemBookDetailedBinding.inflate(LayoutInflater.from(mContext), parent, false)
        return BookHolder(binding)
    }

    override fun getItemCount(): Int {
        return bookList.size
    }

    override fun onBindViewHolder(holder: BookHolder, position: Int) {
        val book = bookList[position]
        val b = holder.binding // binding değişkenine daha kısa bir isim verelim

        // Kitap başlığını ayarla
        b.tvBookTitle.text = book.title.ifEmpty { "Başlık Yok" }

        // Yazar adını ayarla
        b.tvBookAuthor.text = book.author_name.ifEmpty { "Yazar Bilgisi Yok" }

        // Kitap puanını RatingBar'a ve TextView'a ayarla
        val rating = book.average_rating.toFloat()
        b.rbBookRating.rating = rating
        // Puanı "(X.X)" formatında göster
        b.tvBookRatingValue.text = String.format(Locale.US, "(%.1f)", book.average_rating)


        // Kitabın kapak resmini Glide ile yükle
        Glide.with(mContext)
            .load(book.imageUrl)
            .placeholder(R.drawable.img) // Mevcut placeholder'ınız
            .error(R.drawable.img) // Hata durumunda gösterilecek resim (placeholder ile aynı olabilir)
            .into(b.ivBookCover) // item_book_detailed.xml'deki ImageView ID'si

        // Tıklama olayını ayarla
        b.root.setOnClickListener {
            try {
                // fragmentType'a göre navigasyon yap
                if (fragmentType == "main") {
                    // MainPageFragment'tan geliyorsa BookDetails'e git
                    // Navigasyon action'ınızın adının bu olduğunu varsayıyorum
                    val action = MainPageFragmentDirections.mainToBookDetails(book)
                    it.findNavController().navigate(action)
                } else {
                    // SearchFragment'tan (veya başka bir yerden) geliyorsa BookDetails'e git
                    // Navigasyon action'ınızın adının bu olduğunu varsayıyorum
                    val action = SearchFragmentDirections.searchToBookDetails(book)
                    it.findNavController().navigate(action)
                }
            } catch (e: Exception) {
                // Hata olursa logla ve çökmeyi engelle
                Log.e("BookAdapter", "Navigation error: ${e.message}", e)
            }
        }
    }

    // Liste güncelleme fonksiyonu (DiffUtil ile daha verimli hale getirilebilir)
    fun updateList(newBooks: List<Books>) {
        bookList = newBooks
        notifyDataSetChanged() // Daha verimli güncellemeler için DiffUtil kullanmayı düşünebilirsiniz
    }
}
