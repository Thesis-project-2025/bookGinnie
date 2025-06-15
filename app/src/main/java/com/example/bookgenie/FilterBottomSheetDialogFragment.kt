package com.example.bookgenie

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
// import android.widget.RadioButton // RadioGroup.checkedRadioButtonId kullanıldığı için doğrudan RadioButton importuna gerek yok
import com.example.bookgenie.databinding.FragmentFilterBottomSheetDialogBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip


class FilterBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentFilterBottomSheetDialogBinding? = null
    private val binding get() = _binding!!

    private var listener: FilterDialogListener? = null
    private var availableGenres: List<String> = emptyList()
    private var initialFilters: BookFilters = BookFilters() // Başlangıç filtrelerini tutmak için

    interface FilterDialogListener {
        fun onFiltersApplied(filters: BookFilters)
    }

    // MainPageFragment'tan listener'ı set etmek için public metot
    fun setFilterDialogListener(listener: FilterDialogListener) {
        this.listener = listener
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Listener'ın setFilterDialogListener ile ayarlanması beklenir.
        // Ancak, bir güvenlik önlemi olarak parentFragment'ı da kontrol edebiliriz,
        // ama bu genellikle setFilterDialogListener'ın unutulduğu anlamına gelir.
        if (this.listener == null) {
            if (parentFragment is FilterDialogListener) {
                Log.w(TAG, "Listener parentFragment üzerinden atandı. setFilterDialogListener kullanılması tercih edilir.")
                this.listener = parentFragment as FilterDialogListener
            } else if (context is FilterDialogListener) {
                Log.w(TAG, "Listener context üzerinden atandı. setFilterDialogListener kullanılması tercih edilir.")
                // Bu genellikle aktivite için geçerlidir, fragment için parentFragment daha olasıdır.
                // this.listener = context as FilterDialogListener
            }
            // throw RuntimeException("$context or parentFragment must implement FilterDialogListener if not set via setFilterDialogListener.")
            // Listener'ın set edilmesi zorunluysa ve edilmemişse burada bir exception fırlatılabilir.
            // Şimdilik sadece logluyoruz.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            availableGenres = it.getStringArrayList(ARG_AVAILABLE_GENRES) ?: emptyList()

            // BookFilters Parcelable ise:
            // initialFilters = it.getParcelable(ARG_CURRENT_FILTERS) ?: BookFilters()

            // BookFilters Parcelable değilse (mevcut manuel yaklaşım):
            val genres = it.getStringArrayList(ARG_CURRENT_SELECTED_GENRES) ?: emptyList()
            val rating = if (it.containsKey(ARG_CURRENT_MIN_RATING)) it.getDouble(ARG_CURRENT_MIN_RATING) else null
            val sortName = it.getString(ARG_CURRENT_SORT_BY) ?: SortOption.RELEVANCE.name
            val author = it.getString(ARG_CURRENT_AUTHOR)
            val year = if (it.containsKey(ARG_CURRENT_PUBLICATION_YEAR)) it.getInt(ARG_CURRENT_PUBLICATION_YEAR) else null
            initialFilters = BookFilters(
                selectedGenres = ArrayList(genres), // Değiştirilebilir liste için kopyala
                minRating = rating,
                sortBy = try { SortOption.valueOf(sortName) } catch (e: IllegalArgumentException) { SortOption.RELEVANCE },
                //author = author,
                publicationYear = year
            )
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

        // availableGenres ve initialFilters onCreate'de zaten dolduruldu.
        // onViewCreated'da tekrar argümanları okumaya gerek yok.

        setupGenreChips()
        populateCurrentFilters() // initialFilters'a göre UI'ı doldur

        binding.buttonApplyFilters.setOnClickListener {
            applyFilters()
        }

        binding.buttonResetFilters.setOnClickListener {
            resetFiltersAndPopulate()
        }
    }

    private fun setupGenreChips() {
        binding.chipGroupGenres.removeAllViews() // Önceki çipleri temizle
        availableGenres.forEach { genre ->
            val chip = Chip(context).apply {
                text = genre
                isCheckable = true
                // Başlangıç filtrelerine göre çipin işaretli olup olmadığını ayarla
                isChecked = initialFilters.selectedGenres.contains(genre)
            }
            binding.chipGroupGenres.addView(chip)
        }
    }

    private fun populateCurrentFilters() {
        // Türler setupGenreChips içinde initialFilters'a göre ayarlandı.

        // Minimum Puan
        when (initialFilters.minRating) {
            4.0 -> binding.rgMinRating.check(R.id.rbRating4Plus)
            3.0 -> binding.rgMinRating.check(R.id.rbRating3Plus)
            else -> binding.rgMinRating.check(R.id.rbRatingAny) // null veya başka bir değer için
        }

        // Yazar
        //binding.etAuthor.setText(initialFilters.author ?: "")

        // Yayın Yılı
        binding.etPublicationYear.setText(initialFilters.publicationYear?.toString() ?: "")


        // Sıralama
        when (initialFilters.sortBy) {
            SortOption.RELEVANCE -> binding.rgSortBy.check(R.id.rbSortRelevance)
            SortOption.RATING_DESC -> binding.rgSortBy.check(R.id.rbSortRating)
            SortOption.PUBLICATION_YEAR_DESC -> binding.rgSortBy.check(R.id.rbSortYear)
            // else -> binding.rgSortBy.check(R.id.rbSortRelevance) // Varsayılan olarak eklenebilir
        }
    }

    private fun applyFilters() {
        val selectedGenres = mutableListOf<String>()
        for (i in 0 until binding.chipGroupGenres.childCount) {
            val chip = binding.chipGroupGenres.getChildAt(i) as? Chip
            if (chip?.isChecked == true) {
                selectedGenres.add(chip.text.toString())
            }
        }

        val minRatingSelectedId = binding.rgMinRating.checkedRadioButtonId
        val minRating = when (minRatingSelectedId) {
            R.id.rbRating4Plus -> 4.0
            R.id.rbRating3Plus -> 3.0
            else -> null // R.id.rbRatingAny veya hiçbir şey seçili değilse
        }

        //val author = binding.etAuthor.text.toString().trim().takeIf { it.isNotEmpty() }
        val publicationYear = binding.etPublicationYear.text.toString().toIntOrNull()

        val sortBySelectedId = binding.rgSortBy.checkedRadioButtonId
        val sortBy = when (sortBySelectedId) {
            R.id.rbSortRating -> SortOption.RATING_DESC
            R.id.rbSortYear -> SortOption.PUBLICATION_YEAR_DESC
            else -> SortOption.RELEVANCE // R.id.rbSortRelevance veya hiçbir şey seçili değilse
        }

        val newFilters = BookFilters(selectedGenres, minRating, sortBy, publicationYear)
        listener?.onFiltersApplied(newFilters)
        dismiss()
    }

    private fun resetFiltersAndPopulate() {
        initialFilters = BookFilters() // Filtreleri varsayılana sıfırla
        // UI'ı yeni (boş) initialFilters'a göre güncelle
        setupGenreChips() // Çipleri yeniden oluşturur ve işaretleri kaldırır (initialFilters.selectedGenres artık boş)
        populateCurrentFilters() // Diğer UI elemanlarını sıfırlar
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FilterBottomSheetDialog"

        // Argüman anahtarları
        private const val ARG_AVAILABLE_GENRES = "availableGenres"
        // BookFilters Parcelable ise:
        // private const val ARG_CURRENT_FILTERS = "currentFilters"

        // BookFilters Parcelable değilse (mevcut manuel yaklaşım için anahtarlar):
        private const val ARG_CURRENT_SELECTED_GENRES = "currentSelectedGenres"
        private const val ARG_CURRENT_MIN_RATING = "currentMinRating"
        private const val ARG_CURRENT_SORT_BY = "currentSortBy"
        private const val ARG_CURRENT_AUTHOR = "currentAuthor"
        private const val ARG_CURRENT_PUBLICATION_YEAR = "currentPublicationYear"


        fun newInstance(availableGenres: List<String>, currentFilters: BookFilters): FilterBottomSheetDialogFragment {
            val fragment = FilterBottomSheetDialogFragment()
            val args = Bundle().apply {
                putStringArrayList(ARG_AVAILABLE_GENRES, ArrayList(availableGenres))

                // BookFilters Parcelable ise:
                // putParcelable(ARG_CURRENT_FILTERS, currentFilters)

                // BookFilters Parcelable değilse (mevcut manuel yaklaşım):
                putStringArrayList(ARG_CURRENT_SELECTED_GENRES, ArrayList(currentFilters.selectedGenres))
                currentFilters.minRating?.let { putDouble(ARG_CURRENT_MIN_RATING, it) }
                putString(ARG_CURRENT_SORT_BY, currentFilters.sortBy.name) // Enum'ı string olarak kaydet
                //currentFilters.author?.let { putString(ARG_CURRENT_AUTHOR, it) }
                currentFilters.publicationYear?.let { putInt(ARG_CURRENT_PUBLICATION_YEAR, it) }
            }
            fragment.arguments = args
            return fragment
        }
    }
}


