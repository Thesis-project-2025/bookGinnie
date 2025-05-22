package com.example.bookgenie // Paket adınızı kendi projenize göre güncelleyin

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.navigation.fragment.findNavController // Navigasyon için eklendi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bookgenie.databinding.FragmentUserInfoBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
// import com.google.firebase.firestore.ktx.firestore // Kullanılmıyorsa kaldırılabilir
// import com.google.firebase.ktx.Firebase // Kullanılmıyorsa kaldırılabilir

class UserInfoFragment : Fragment() {

    private var _binding: FragmentUserInfoBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentUser: FirebaseUser? = null

    private var originalUsername: String? = null
    private var originalAge: String? = null
    private var originalSelectedAvatarName: String? = null

    private val avatarDrawableNames = listOf(
        "avatar1", "avatar2", "avatar3", "avatar4",
        "avatar5", "avatar6", "avatar7", "avatar8",
        "avatar9", "avatar10", "avatar11", "avatar12"
    )
    private var avatarPickerDialog: Dialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        currentUser = auth.currentUser

        loadUserProfile()
        setupClickListeners()
        updateUIEditMode(false)
    }

    private fun getDrawableResourceIdByName(name: String): Int {
        return try {
            resources.getIdentifier(name, "drawable", requireContext().packageName)
        } catch (e: Exception) {
            Log.e("UserInfoFragment", "Error getting resource ID for $name", e)
            0
        }
    }

    private fun loadUserProfile() {
        showLoading(true)
        binding.profileLottieView.visibility = View.GONE
        binding.actualProfileImageView.visibility = View.VISIBLE

        currentUser?.uid?.let { userId ->
            val userDocRef = db.collection("users_info").document(userId)
            userDocRef.get()
                .addOnSuccessListener { document ->
                    showLoading(false)
                    if (document != null && document.exists()) {
                        originalUsername = document.getString("username")
                        val email = currentUser?.email
                        originalAge = document.getLong("age")?.toString()
                        originalSelectedAvatarName = document.getString("selectedAvatarName")

                        binding.editTextName.setText(originalUsername ?: "")
                        binding.editTextEmail.setText(email ?: "")
                        binding.editTextAge.setText(originalAge ?: "")

                        if (!originalSelectedAvatarName.isNullOrEmpty()) {
                            val avatarResId = getDrawableResourceIdByName(originalSelectedAvatarName!!)
                            if (avatarResId != 0) {
                                Glide.with(this).load(avatarResId)
                                    .placeholder(R.drawable.person)
                                    .error(R.drawable.person)
                                    .into(binding.actualProfileImageView)
                            } else {
                                binding.actualProfileImageView.setImageResource(R.drawable.person)
                            }
                        } else {
                            binding.actualProfileImageView.setImageResource(R.drawable.person)
                        }
                        loadUserRatingCount(userId)
                    } else {
                        binding.actualProfileImageView.setImageResource(R.drawable.person)
                        binding.buttonMyRatings.text = getString(R.string.my_rated_books_count, 0)
                    }
                }
                .addOnFailureListener { exception ->
                    showLoading(false)
                    binding.actualProfileImageView.setImageResource(R.drawable.person)
                    binding.buttonMyRatings.text = getString(R.string.my_rated_books_count, 0)
                    Log.e("UserInfoFragment", "Kullanıcı bilgileri yüklenirken hata.", exception)
                }
        } ?: run {
            showLoading(false)
            binding.actualProfileImageView.setImageResource(R.drawable.person)
            binding.buttonMyRatings.text = getString(R.string.my_rated_books_count, 0)
        }
    }

    private fun loadUserRatingCount(userId: String) {
        db.collection("user_ratings")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                val count = documents.size()
                binding.buttonMyRatings.text = getString(R.string.my_rated_books_count, count)
            }
            .addOnFailureListener { exception ->
                Log.w("UserInfoFragment", "Error getting rating count.", exception)
                binding.buttonMyRatings.text = getString(R.string.my_rated_books_count, 0)
            }
    }


    private fun setupClickListeners() {
        binding.buttonChangeProfilePic.setOnClickListener {
            showAvatarPickerDialog()
        }

        binding.buttonEditProfile.setOnClickListener {
            updateUIEditMode(true)
        }

        binding.buttonSaveChanges.setOnClickListener {
            saveProfileChanges()
        }

        binding.buttonCancelEdit.setOnClickListener {
            binding.editTextName.setText(originalUsername ?: "")
            binding.editTextAge.setText(originalAge ?: "")
            originalSelectedAvatarName?.let { name ->
                val avatarResId = getDrawableResourceIdByName(name)
                if (avatarResId != 0) Glide.with(this).load(avatarResId).into(binding.actualProfileImageView)
                else binding.actualProfileImageView.setImageResource(R.drawable.person)
            } ?: binding.actualProfileImageView.setImageResource(R.drawable.person)
            updateUIEditMode(false)
        }

        binding.buttonChangePassword.setOnClickListener {
            sendPasswordResetEmail()
        }

        binding.buttonMyRatings.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_userInfoFragment_to_userRatingsFragment)
            } catch (e: Exception) {
                Log.e("UserInfoFragment", "Navigation to UserRatingsFragment failed: ${e.message}")
                Toast.makeText(context, "Could not open ratings.", Toast.LENGTH_SHORT).show()
            }
        }

        // LOG OUT BUTONU İÇİN TIKLAMA OLAYI EKLENDİ
        binding.buttonLogOut.setOnClickListener {
            auth.signOut() // Firebase'den çıkış yap
            Toast.makeText(context, getString(R.string.logged_out_successfully), Toast.LENGTH_SHORT).show()
            try {
                // Onboarding ekranına yönlendir ve geri tuşuyla profil ekranına dönülmesini engelle
                findNavController().navigate(R.id.action_userInfoFragment_to_onboardingFragment)
            } catch (e: Exception) {
                Log.e("UserInfoFragment", "Navigation to OnboardingFragment failed after logout: ${e.message}")
                // Alternatif olarak, aktiviteyi yeniden başlatabilir veya ana navigasyon grafiğine dönebilirsiniz
                // Örneğin: requireActivity().recreate() veya findNavController().popBackStack(R.id.main_activity_nav, true)
            }
        }
    }

    private fun showAvatarPickerDialog() {
        if (avatarPickerDialog?.isShowing == true) return

        val dialogView = LayoutInflater.from(context).inflate(R.layout.fragment_dialog_avatar_picker, null)
        val recyclerViewAvatars = dialogView.findViewById<RecyclerView>(R.id.recyclerViewAvatars)
        val avatarResourceIds = avatarDrawableNames.mapNotNull { name ->
            getDrawableResourceIdByName(name).takeIf { it != 0 }
        }

        if (avatarResourceIds.isEmpty()){
            Toast.makeText(context, "No avatars to display.", Toast.LENGTH_SHORT).show()
            return
        }

        val adapter = AvatarAdapter(requireContext(), avatarResourceIds) { selectedAvatarResId ->
            val selectedName = avatarDrawableNames.find { name -> getDrawableResourceIdByName(name) == selectedAvatarResId }
            selectedName?.let {
                updateSelectedAvatarInFirestore(it)
                Glide.with(this).load(selectedAvatarResId).into(binding.actualProfileImageView)
            }
            avatarPickerDialog?.dismiss()
        }
        recyclerViewAvatars.layoutManager = GridLayoutManager(context, 4)
        recyclerViewAvatars.adapter = adapter

        avatarPickerDialog = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogView)
            show()
        }
    }

    private fun updateSelectedAvatarInFirestore(avatarName: String) {
        currentUser?.uid?.let { userId ->
            showLoading(true)
            db.collection("users_info").document(userId)
                .update("selectedAvatarName", avatarName)
                .addOnSuccessListener {
                    showLoading(false)
                    originalSelectedAvatarName = avatarName
                    Toast.makeText(context, getString(R.string.avatar_updated), Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { exception ->
                    showLoading(false)
                    Log.e("UserInfoFragment", "Avatar update failed", exception)
                    Toast.makeText(context, getString(R.string.avatar_update_failed), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun updateUIEditMode(isEdit: Boolean) {
        binding.editTextName.isEnabled = isEdit
        binding.editTextAge.isEnabled = isEdit

        binding.buttonEditProfile.visibility = if (isEdit) View.GONE else View.VISIBLE
        binding.buttonSaveChanges.visibility = if (isEdit) View.VISIBLE else View.GONE
        binding.buttonCancelEdit.visibility = if (isEdit) View.VISIBLE else View.GONE
        binding.buttonChangePassword.visibility = if (isEdit) View.GONE else View.VISIBLE
        if (isEdit) binding.editTextName.requestFocus()
    }

    private fun saveProfileChanges() {
        val newUsername = binding.editTextName.text.toString().trim()
        val newAgeString = binding.editTextAge.text.toString().trim()

        if (newUsername.isEmpty()) {
            Toast.makeText(context, "İsim boş bırakılamaz.", Toast.LENGTH_SHORT).show()
            return
        }
        val newAge: Long? = newAgeString.toLongOrNull()?.takeIf { it > 0 }
        if (newAgeString.isNotEmpty() && newAge == null) {
            Toast.makeText(context, "Lütfen geçerli bir yaş girin.", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)
        currentUser?.uid?.let { userId ->
            val updates = hashMapOf<String, Any?>(
                "username" to newUsername,
                "age" to newAge
            )
            db.collection("users_info").document(userId)
                .update(updates.filterValues { it != null })
                .addOnSuccessListener {
                    showLoading(false)
                    originalUsername = newUsername
                    originalAge = newAge?.toString()
                    Toast.makeText(context, getString(R.string.profile_update_success), Toast.LENGTH_SHORT).show()
                    updateUIEditMode(false)
                }
                .addOnFailureListener { exception ->
                    showLoading(false)
                    Toast.makeText(context, getString(R.string.profile_update_failed) + ": ${exception.message}", Toast.LENGTH_LONG).show()
                }
        } ?: showLoading(false)
    }

    private fun sendPasswordResetEmail() {
        currentUser?.email?.let { email ->
            showLoading(true)
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    showLoading(false)
                    if (task.isSuccessful) {
                        Toast.makeText(context, getString(R.string.password_reset_email_sent), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, getString(R.string.password_reset_email_failed) + ": ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } ?: Toast.makeText(context, "E-posta adresi bulunamadı.", Toast.LENGTH_SHORT).show()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBarUserInfo.visibility = if (isLoading) View.VISIBLE else View.GONE
        val enableInteractions = !isLoading
        binding.buttonChangeProfilePic.isEnabled = enableInteractions
        binding.buttonEditProfile.isEnabled = enableInteractions
        binding.buttonSaveChanges.isEnabled = enableInteractions
        binding.buttonCancelEdit.isEnabled = enableInteractions
        binding.buttonChangePassword.isEnabled = enableInteractions
        binding.buttonMyRatings.isEnabled = enableInteractions
        binding.buttonLogOut.isEnabled = enableInteractions // Çıkış yap butonunu da etkile
    }

    override fun onDestroyView() {
        super.onDestroyView()
        avatarPickerDialog?.dismiss()
        _binding = null
    }
}
