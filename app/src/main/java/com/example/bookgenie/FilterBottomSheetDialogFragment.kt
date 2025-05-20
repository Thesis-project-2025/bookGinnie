package com.example.bookgenie

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import com.example.bookgenie.databinding.FragmentFilterBottomSheetDialogBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

class FilterBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentFilterBottomSheetDialogBinding? = null
    private val binding get() = _binding!!

    private var listener: FilterDialogListener? = null
    private var availableGenres: List<String> = emptyList()
    private var currentFilters: BookFilters = BookFilters()

    interface FilterDialogListener {
        fun onFiltersApplied(filters: BookFilters)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Parent fragment'ın listener'ı implemente ettiğinden emin ol
        if (parentFragment is FilterDialogListener) {
            listener = parentFragment as FilterDialogListener
        } else if (context is FilterDialogListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement FilterDialogListener or be hosted by a Fragment that does.")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilterBottomSheetDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let {
            availableGenres = it.getStringArrayList("availableGenres") ?: emptyList()
            currentFilters = it.getParcelable("currentFilters") ?: BookFilters() // BookFilters Parcelable olmalı
        }
        if (currentFilters == null) { // Parcelable olmaması durumunda fallback
            currentFilters = BookFilters()
        }


        setupGenreChips()
        populateCurrentFilters()

        binding.buttonApplyFilters.setOnClickListener {
            applyFilters()
        }

        binding.buttonResetFilters.setOnClickListener {
            resetFilters()
            // İsteğe bağlı: Sıfırladıktan sonra hemen uygula veya kullanıcının "Uygula"ya basmasını bekle
            // applyFilters()
        }
    }

    private fun setupGenreChips() {
        binding.chipGroupGenres.removeAllViews()
        availableGenres.forEach { genre ->
            val chip = Chip(context)
            chip.text = genre
            chip.isCheckable = true
            chip.isChecked = currentFilters.selectedGenres.contains(genre)
            binding.chipGroupGenres.addView(chip)
        }
    }

    private fun populateCurrentFilters() {
        // Türler zaten setupGenreChips'te ayarlandı

        // Minimum Puan
        when (currentFilters.minRating) {
            4.0 -> binding.rgMinRating.check(R.id.rbRating4Plus)
            3.0 -> binding.rgMinRating.check(R.id.rbRating3Plus)
            else -> binding.rgMinRating.check(R.id.rbRatingAny)
        }

        // Yazar
        binding.etAuthor.setText(currentFilters.author ?: "")

        // Yayın Yılı
        currentFilters.publicationYear?.let { binding.etPublicationYear.setText(it.toString()) }


        // Sıralama
        when (currentFilters.sortBy) {
            SortOption.RELEVANCE -> binding.rgSortBy.check(R.id.rbSortRelevance)
            SortOption.RATING_DESC -> binding.rgSortBy.check(R.id.rbSortRating)
            SortOption.PUBLICATION_YEAR_DESC -> binding.rgSortBy.check(R.id.rbSortYear)
        }
    }

    private fun applyFilters() {
        val selectedGenres = mutableListOf<String>()
        for (i in 0 until binding.chipGroupGenres.childCount) {
            val chip = binding.chipGroupGenres.getChildAt(i) as Chip
            if (chip.isChecked) {
                selectedGenres.add(chip.text.toString())
            }
        }

        val minRating = when (binding.rgMinRating.checkedRadioButtonId) {
            R.id.rbRating4Plus -> 4.0
            R.id.rbRating3Plus -> 3.0
            else -> null
        }

        val author = binding.etAuthor.text.toString().trim().takeIf { it.isNotEmpty() }
        val publicationYear = binding.etPublicationYear.text.toString().toIntOrNull()

        val sortBy = when (binding.rgSortBy.checkedRadioButtonId) {
            R.id.rbSortRating -> SortOption.RATING_DESC
            R.id.rbSortYear -> SortOption.PUBLICATION_YEAR_DESC
            else -> SortOption.RELEVANCE
        }

        val newFilters = BookFilters(selectedGenres, minRating, sortBy, author, publicationYear)
        listener?.onFiltersApplied(newFilters)
        dismiss()
    }

    private fun resetFilters() {
        currentFilters = BookFilters() // Varsayılan filtrelere sıfırla
        setupGenreChips() // Çipleri yeniden oluştur ve işaretleri kaldır
        populateCurrentFilters() // Diğer alanları varsayılana ayarla
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FilterBottomSheetDialog"

        // BookFilters'ı Parcelable yapmanız veya burada manuel bundle işlemleri yapmanız gerekir.
        // Basitlik adına, şimdilik Parcelable olmadığını varsayalım ve MainPageFragment'tan gönderirken dikkat edelim.
        // Daha robust bir çözüm için BookFilters'ı @Parcelize ile işaretleyin.
        fun newInstance(availableGenres: List<String>, currentFilters: BookFilters): FilterBottomSheetDialogFragment {
            val fragment = FilterBottomSheetDialogFragment()
            val args = Bundle()
            args.putStringArrayList("availableGenres", ArrayList(availableGenres))
            // BookFilters'ı Parcelable yaparsanız:
            // args.putParcelable("currentFilters", currentFilters)
            // Şimdilik manuel olarak veya daha basit bir yolla aktaracağız.
            // Bu örnekte, currentFilters'ı yeniden oluşturmak için temel değerleri aktarabiliriz
            // ya da MainPageFragment'ta bir ViewModel veya paylaşılan bir değişkende tutabiliriz.
            // Şimdilik, MainPageFragment'taki currentFilters'a doğrudan erişim olmadığını varsayıyoruz
            // ve başlangıç değerlerini bu şekilde aktarıyoruz.
            // Daha iyi bir yaklaşım, BookFilters'ı @Parcelize ile işaretlemek olacaktır.
            args.putStringArrayList("currentSelectedGenres", ArrayList(currentFilters.selectedGenres))
            currentFilters.minRating?.let { args.putDouble("currentMinRating", it) }
            args.putString("currentSortBy", currentFilters.sortBy.name)
            currentFilters.author?.let { args.putString("currentAuthor", it) }
            currentFilters.publicationYear?.let { args.putInt("currentPublicationYear", it) }

            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // BookFilters'ı Parcelable yapmadıysanız, argümanları burada manuel olarak ayrıştırın
        arguments?.let {
            val genres = it.getStringArrayList("currentSelectedGenres") ?: emptyList()
            val rating = if (it.containsKey("currentMinRating")) it.getDouble("currentMinRating") else null
            val sortName = it.getString("currentSortBy") ?: SortOption.RELEVANCE.name
            val author = it.getString("currentAuthor")
            val year = if (it.containsKey("currentPublicationYear")) it.getInt("currentPublicationYear") else null
            currentFilters = BookFilters(
                selectedGenres = genres,
                minRating = rating,
                sortBy = SortOption.valueOf(sortName),
                author = author,
                publicationYear = year
            )
            availableGenres = it.getStringArrayList("availableGenres") ?: emptyList()
        }
    }
}
