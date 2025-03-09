package com.example.bookgenie

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.bookgenie.databinding.FragmentBookDetailsBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.palette.graphics.Palette
import android.renderscript.*
import android.widget.ImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class BookDetailsFragment : Fragment() {
    private lateinit var binding: FragmentBookDetailsBinding
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = Firebase.auth

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentBookDetailsBinding.inflate(inflater, container, false)

        val bundle: BookDetailsFragmentArgs by navArgs()
        val book = bundle.book

        binding.toolbarBookDetails.title = book.title

        // Glide ile resmi yükleyip arka planı oluştur
        Glide.with(requireContext())
            .asBitmap()
            .load(book.imageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    applyBlurredBackground(resource)
                    binding.ivBook.setImageBitmap(resource) // Kitap kapağını tam olarak yerleştir
                    binding.ivBook.visibility = View.VISIBLE // Kapağı görünür yap
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    binding.ivBook.setImageDrawable(placeholder)
                }
            })

        // Kitap bilgilerini doldur
        binding.tvBook.text = book.title
        binding.tvAuthor.text = book.author_name
        binding.tvDescription.text = book.description
        binding.tvPages.text = book.num_pages.toString()
        binding.tvYear.text = book.publication_year.toString()
        binding.tvRating.text = book.average_rating.toString()
        binding.tvGenres.text = book.genres.joinToString(", ")

        // Rating işlemleri
        loadRatingFromFirestore(book.book_id) // Kitap için rating verisini al
        for (i in 0 until binding.llRating.childCount) {
            val starView = binding.llRating.getChildAt(i) as ImageView
            starView.setOnClickListener {
                saveRatingToFirestore(book.book_id, i + 1) // Rating'i kaydet
            }
        }

        // Bottom Navigation işlemleri
        val bottomNavigationView: BottomNavigationView = binding.bottomNavigationView2
        bottomNavigationView.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.idMainPage -> {
                    findNavController().navigate(R.id.bookDetailsToMainPage)
                    true
                }
                R.id.idSettings -> {
                    findNavController().navigate(R.id.bookDetailsToSettings)
                    true
                }
                R.id.idSearch -> {
                    findNavController().navigate(R.id.action_bookDetailsFragment_to_searchFragment2)
                    true
                }
                else -> false
            }
        }

        return binding.root
    }

    // Rating verisini Firestore'a kaydet
    private fun saveRatingToFirestore(bookId: Int, selectedRating: Int) {
        val userId = auth.currentUser?.uid ?: return

        val ratingData = hashMapOf(
            "userId" to userId,
            "bookId" to bookId,
            "rating" to selectedRating,
            "timestamp" to FieldValue.serverTimestamp()
        )

        // Rating belgesini kullanıcı kitap ID'sine göre benzersiz bir şekilde kaydediyoruz.
        firestore.collection("user_ratings")
            .document("${userId}_${bookId}")
            .set(ratingData)
            .addOnSuccessListener {
                loadRatingFromFirestore(bookId) // Yeni rating kaydedildikten sonra tekrar yükle
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
            }
    }

    // Firestore'dan rating verisini al
    private fun loadRatingFromFirestore(bookId: Int) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("user_ratings")
            .document("${userId}_${bookId}")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val rating = document.getLong("rating")?.toInt() ?: 0
                    updateRatingStars(rating) // Yıldızları güncelle
                }
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
            }
    }

    // Rating'e göre yıldızları güncelleme fonksiyonu
    private fun updateRatingStars(rating: Int) {
        for (i in 1..5) {
            val starView = binding.llRating.getChildAt(i - 1) as ImageView
            if (i <= rating) {
                starView.setImageResource(R.drawable.ic_star_filled) // Sarı yıldız
            } else {
                starView.setImageResource(R.drawable.ic_star_empty) // Boş yıldız
            }
        }
    }

    // Ana renklerden bulanık bir arka plan oluştur
    private fun applyBlurredBackground(bitmap: Bitmap) {
        // Palette ile ana rengi al
        Palette.from(bitmap).generate { palette ->
            val dominantColor = palette?.getDominantColor(0xFFCCCCCC.toInt()) ?: 0xFFCCCCCC.toInt()

            // Bitmap'i büyüt ve bulanıklaştır
            val blurredBitmap = blurBitmap(scaleBitmap(bitmap, 2.5f)) // 2.5x büyütüp blur uygula

            // Arka plana uygula
            binding.ivBackground.setImageBitmap(blurredBitmap)
            binding.ivBackground.setBackgroundColor(dominantColor)
        }
    }

    // Bitmap'i ölçeklendir
    private fun scaleBitmap(bitmap: Bitmap, factor: Float): Bitmap {
        val width = ((bitmap.width * factor)).toInt() * 2
        val height = (bitmap.height * factor).toInt()
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    // RenderScript ile bulanıklaştırma
    private fun blurBitmap(bitmap: Bitmap): Bitmap {
        val rs = RenderScript.create(requireContext())
        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        script.setRadius(25f) // Blur seviyesi (0-25 arası)
        script.setInput(input)
        script.forEach(output)
        output.copyTo(bitmap)
        rs.destroy()
        return bitmap
    }
}
