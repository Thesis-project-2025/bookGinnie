package com.example.bookgenie

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.example.bookgenie.databinding.FragmentUserInfoBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class UserInfoFragment : Fragment() {

    private var _binding: FragmentUserInfoBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var currentUser: FirebaseUser? = null

    private var imageUri: Uri? = null
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    // Kullanıcının orijinal bilgilerini saklamak için
    private var originalUsername: String? = null
    private var originalAge: String? = null
    private var originalProfileImageUrl: String? = null


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
        storage = FirebaseStorage.getInstance()
        currentUser = auth.currentUser

        setupActivityResultLauncher()
        loadUserProfile()
        setupClickListeners()
        updateUIEditMode(false) // Başlangıçta düzenleme modu kapalı
    }

    private fun setupActivityResultLauncher() {
        activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data: Intent? = result.data
                    data?.data?.let {
                        imageUri = it
                        // Resmi ImageView'da göster ve Lottie'yi gizle
                        Glide.with(this)
                            .load(imageUri)
                            .placeholder(R.drawable.person) // Yüklenirken gösterilecek resim
                            .error(R.drawable.person) // Hata durumunda gösterilecek resim
                            .into(binding.actualProfileImageView)
                        binding.actualProfileImageView.visibility = View.VISIBLE
                        binding.profileLottieView.visibility = View.GONE
                        // Resmi hemen yükle ve kaydet
                        uploadImageToFirebaseStorage()
                    }
                }
            }
    }

    private fun loadUserProfile() {
        showLoading(true)
        if (currentUser != null) {
            val userId = currentUser!!.uid
            val userDocRef = db.collection("users_info").document(userId)

            userDocRef.get()
                .addOnSuccessListener { document ->
                    showLoading(false)
                    if (document != null && document.exists()) {
                        originalUsername = document.getString("username")
                        val email = document.getString("email") // E-posta Firebase Auth'dan alınabilir, Firestore'da da tutuluyorsa buradan.
                        originalAge = document.getLong("age")?.toString()
                        originalProfileImageUrl = document.getString("profileImageUrl")

                        binding.editTextName.setText(originalUsername ?: "")
                        binding.editTextEmail.setText(currentUser?.email ?: email ?: "") // Öncelik Auth e-postası
                        binding.editTextAge.setText(originalAge ?: "")

                        if (!originalProfileImageUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(originalProfileImageUrl)
                                .placeholder(R.drawable.person)
                                .error(R.drawable.person)
                                .into(binding.actualProfileImageView)
                            binding.actualProfileImageView.visibility = View.VISIBLE
                            binding.profileLottieView.visibility = View.GONE
                        } else {
                            binding.profileLottieView.visibility = View.VISIBLE
                            binding.actualProfileImageView.visibility = View.GONE
                        }
                        Log.d("UserInfoFragment", "User info loaded successfully.")
                    } else {
                        Log.d("UserInfoFragment", "No such document for user.")
                        Toast.makeText(context, getString(R.string.user_info_not_found), Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    showLoading(false)
                    Log.e("UserInfoFragment", "Error loading user info", exception)
                    Toast.makeText(context, getString(R.string.error_loading_user_info, exception.message), Toast.LENGTH_LONG).show()
                }
        } else {
            showLoading(false)
            Log.w("UserInfoFragment", "Current user is null.")
            Toast.makeText(context, getString(R.string.please_sign_in), Toast.LENGTH_SHORT).show()
            // Gerekirse giriş ekranına yönlendir
        }
    }

    private fun setupClickListeners() {
        binding.buttonChangeProfilePic.setOnClickListener {
            openImageChooser()
        }

        binding.buttonEditProfile.setOnClickListener {
            updateUIEditMode(true)
        }

        binding.buttonSaveChanges.setOnClickListener {
            saveProfileChanges()
        }

        binding.buttonCancelEdit.setOnClickListener {
            // Değişiklikleri geri al ve düzenleme modunu kapat
            binding.editTextName.setText(originalUsername ?: "")
            binding.editTextAge.setText(originalAge ?: "")
            if (!originalProfileImageUrl.isNullOrEmpty()) {
                Glide.with(this).load(originalProfileImageUrl).into(binding.actualProfileImageView)
                binding.actualProfileImageView.visibility = View.VISIBLE
                binding.profileLottieView.visibility = View.GONE
            } else {
                binding.profileLottieView.visibility = View.VISIBLE
                binding.actualProfileImageView.visibility = View.GONE
                binding.actualProfileImageView.setImageResource(R.drawable.person) // veya placeholder
            }
            imageUri = null // Seçilen yeni resmi iptal et
            updateUIEditMode(false)
        }

        binding.buttonChangePassword.setOnClickListener {
            sendPasswordResetEmail()
        }
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        // Alternatif olarak:
        // val intent = Intent()
        // intent.type = "image/*"
        // intent.action = Intent.ACTION_GET_CONTENT
        activityResultLauncher.launch(intent)
    }

    private fun uploadImageToFirebaseStorage() {
        imageUri?.let { uri ->
            showLoading(true)
            val userId = currentUser?.uid ?: return@let
            // Dosya adını benzersiz yapın (örn: userId.jpg veya userId_timestamp.jpg)
            val fileName = "${userId}_profile.jpg"
            val storageRef: StorageReference = storage.reference.child("profile_images/$fileName")

            storageRef.putFile(uri)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        updateUserProfileImageUrlInFirestore(downloadUrl.toString())
                    }.addOnFailureListener {
                        showLoading(false)
                        Toast.makeText(context, getString(R.string.image_upload_failed) + ": URL alınamadı", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    showLoading(false)
                    Log.e("UserInfoFragment", "Image upload failed", exception)
                    Toast.makeText(context, getString(R.string.image_upload_failed) + ": ${exception.message}", Toast.LENGTH_LONG).show()
                }
                .addOnProgressListener { snapshot ->
                    // Yükleme ilerlemesini gösterebilirsiniz
                    val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount)
                    Log.d("UserInfoFragment", "Upload is $progress% done")
                }
        }
    }

    private fun updateUserProfileImageUrlInFirestore(imageUrl: String) {
        val userId = currentUser?.uid ?: return
        db.collection("users_info").document(userId)
            .update("profileImageUrl", imageUrl)
            .addOnSuccessListener {
                showLoading(false)
                originalProfileImageUrl = imageUrl // Orijinal URL'yi güncelle
                Toast.makeText(context, getString(R.string.image_upload_success), Toast.LENGTH_SHORT).show()
                Log.d("UserInfoFragment", "Profile image URL updated in Firestore.")
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e("UserInfoFragment", "Error updating profile image URL in Firestore", exception)
                Toast.makeText(context, getString(R.string.image_upload_failed), Toast.LENGTH_LONG).show()
            }
    }


    private fun updateUIEditMode(isEdit: Boolean) {
        binding.editTextName.isEnabled = isEdit
        binding.editTextAge.isEnabled = isEdit
        // binding.editTextEmail.isEnabled = isEdit // E-posta genellikle Auth üzerinden güncellenir, şimdilik kapalı

        if (isEdit) {
            binding.buttonEditProfile.visibility = View.GONE
            binding.buttonSaveChanges.visibility = View.VISIBLE
            binding.buttonCancelEdit.visibility = View.VISIBLE
            binding.buttonChangePassword.visibility = View.GONE // Düzenleme sırasında şifre değiştirme gizlensin
            binding.editTextName.requestFocus() // İsim alanına odaklan
        } else {
            binding.buttonEditProfile.visibility = View.VISIBLE
            binding.buttonSaveChanges.visibility = View.GONE
            binding.buttonCancelEdit.visibility = View.GONE
            binding.buttonChangePassword.visibility = View.VISIBLE
        }
    }

    private fun saveProfileChanges() {
        val newUsername = binding.editTextName.text.toString().trim()
        val newAgeString = binding.editTextAge.text.toString().trim()

        if (newUsername.isEmpty() || newAgeString.isEmpty()) {
            Toast.makeText(context, "Ad ve yaş boş bırakılamaz.", Toast.LENGTH_SHORT).show()
            return
        }

        val newAge = newAgeString.toIntOrNull()
        if (newAge == null || newAge <= 0) {
            Toast.makeText(context, "Lütfen geçerli bir yaş girin.", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)
        val userId = currentUser?.uid ?: return

        val updates = hashMapOf<String, Any>(
            "username" to newUsername,
            "age" to newAge
        )

        db.collection("users_info").document(userId)
            .update(updates)
            .addOnSuccessListener {
                showLoading(false)
                originalUsername = newUsername // Orijinal bilgileri güncelle
                originalAge = newAge.toString()
                Toast.makeText(context, getString(R.string.profile_update_success), Toast.LENGTH_SHORT).show()
                updateUIEditMode(false)
                Log.d("UserInfoFragment", "Profile updated successfully.")
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e("UserInfoFragment", "Error updating profile", exception)
                Toast.makeText(context, getString(R.string.profile_update_failed) + ": ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun sendPasswordResetEmail() {
        currentUser?.email?.let { email ->
            showLoading(true)
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    showLoading(false)
                    if (task.isSuccessful) {
                        Toast.makeText(context, getString(R.string.password_reset_email_sent), Toast.LENGTH_LONG).show()
                        Log.d("UserInfoFragment", "Password reset email sent.")
                    } else {
                        Toast.makeText(context, getString(R.string.password_reset_email_failed) + ": ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        Log.e("UserInfoFragment", "Failed to send password reset email.", task.exception)
                    }
                }
        } ?: run {
            Toast.makeText(context, "E-posta adresi bulunamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBarUserInfo.visibility = if (isLoading) View.VISIBLE else View.GONE
        // Yükleme sırasında diğer butonları devre dışı bırakabilirsiniz
        binding.buttonChangeProfilePic.isEnabled = !isLoading
        binding.buttonEditProfile.isEnabled = !isLoading
        binding.buttonSaveChanges.isEnabled = !isLoading
        binding.buttonCancelEdit.isEnabled = !isLoading
        binding.buttonChangePassword.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}