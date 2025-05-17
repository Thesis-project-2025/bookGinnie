// UserInfoFragment.kt
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
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.bookgenie.databinding.FragmentUserInfoBinding
// import com.google.firebase.auth.EmailAuthProvider // Kullanılmıyorsa kaldırılabilir
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class UserInfoFragment : Fragment() {

    private var _binding: FragmentUserInfoBinding? = null
    private val binding get() = _binding!!

    // Firebase Authentication için değişken
    private lateinit var auth: FirebaseAuth // EKLENDİ: auth değişkeninin tanımı
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var currentUser: FirebaseUser? = null

    private var imageUri: Uri? = null
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    private var originalUsername: String? = null
    private var originalAge: String? = null
    private var originalProfileImageUrl: String? = null

    // Çıkış yaptıktan sonra OnboardingFragment'a yönlendirme için action ID
    // Bu ID'nin main_activity_nav.xml dosyasında UserInfoFragment'tan OnboardingFragment'a
    // giden bir action olarak tanımlanması gerekecek.
    private val actionUserInfoToOnboarding by lazy { R.id.action_userInfoFragment_to_onboardingFragment } // Kendi action ID'nizle değiştirin


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserInfoBinding.inflate(inflater, container, false)

        // Firebase servislerini initialize et
        auth = FirebaseAuth.getInstance() // auth burada initialize ediliyor.
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        currentUser = auth.currentUser // currentUser, auth initialize edildikten sonra atanıyor.

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // auth, db, storage ve currentUser onCreateView'de initialize edildi.
        // Burada tekrar initialize etmeye gerek yok.

        setupActivityResultLauncher()
        loadUserProfile()
        setupClickListeners()
        updateUIEditMode(false)
    }

    private fun setupActivityResultLauncher() {
        activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data: Intent? = result.data
                    data?.data?.let {
                        imageUri = it
                        Glide.with(this)
                            .load(imageUri)
                            .placeholder(R.drawable.person) // Projenizde R.drawable.person olduğundan emin olun
                            .error(R.drawable.person)       // Projenizde R.drawable.person olduğundan emin olun
                            .into(binding.actualProfileImageView)
                        binding.actualProfileImageView.visibility = View.VISIBLE
                        binding.profileLottieView.visibility = View.GONE
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
                        // E-posta Firebase Auth'dan alınabilir, Firestore'da da tutuluyorsa buradan da okunabilir.
                        // val emailFromFirestore = document.getString("email")
                        originalAge = document.getLong("age")?.toString()
                        originalProfileImageUrl = document.getString("profileImageUrl")

                        binding.editTextName.setText(originalUsername ?: "")
                        binding.editTextEmail.setText(currentUser?.email ?: "") // Öncelik Auth e-postası
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
            // Örneğin: findNavController().navigate(R.id.action_global_to_loginFragment) // Nav grafiğinize göre
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

        binding.buttonLogOut.setOnClickListener {
            showLogOutConfirmationDialog()
        }
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        activityResultLauncher.launch(intent)
    }

    private fun uploadImageToFirebaseStorage() {
        imageUri?.let { uri ->
            showLoading(true)
            val userId = currentUser?.uid ?: run {
                showLoading(false)
                Toast.makeText(context, "User not logged in.", Toast.LENGTH_SHORT).show()
                return@let
            }
            // Dosya adını benzersiz yapın (örn: userId.jpg veya userId_timestamp.jpg)
            val fileName = "${userId}_profile.jpg"
            val storageRef: StorageReference = storage.reference.child("profile_images/$fileName")

            storageRef.putFile(uri)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        updateUserProfileImageUrlInFirestore(downloadUrl.toString())
                    }.addOnFailureListener {  exception -> // Hata durumunda exception parametresini ekle
                        showLoading(false)
                        Log.e("UserInfoFragment", "Failed to get download URL", exception)
                        Toast.makeText(context, getString(R.string.image_upload_failed) + ": URL alınamadı", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    showLoading(false)
                    Log.e("UserInfoFragment", "Image upload failed", exception)
                    Toast.makeText(context, getString(R.string.image_upload_failed) + ": ${exception.message}", Toast.LENGTH_LONG).show()
                }
                .addOnProgressListener { snapshot ->
                    val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount)
                    Log.d("UserInfoFragment", "Upload is $progress% done")
                    // İsterseniz burada bir ProgressBar güncelleyebilirsiniz.
                }
        }
    }

    private fun updateUserProfileImageUrlInFirestore(imageUrl: String) {
        val userId = currentUser?.uid ?: run {
            showLoading(false) // Eğer userId null ise yüklemeyi durdur
            Toast.makeText(context, "User not logged in to update image URL.", Toast.LENGTH_SHORT).show()
            return
        }
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
        // binding.editTextEmail.isEnabled = isEdit // E-posta genellikle Auth üzerinden güncellenir, bu yüzden kapalı kalması iyi bir pratik.

        if (isEdit) {
            binding.buttonEditProfile.visibility = View.GONE
            binding.buttonSaveChanges.visibility = View.VISIBLE
            binding.buttonCancelEdit.visibility = View.VISIBLE
            binding.buttonChangePassword.visibility = View.GONE
            binding.buttonLogOut.visibility = View.GONE // Düzenleme sırasında çıkış yap butonu da gizlensin
            binding.editTextName.requestFocus() // İsim alanına odaklan
        } else {
            binding.buttonEditProfile.visibility = View.VISIBLE
            binding.buttonSaveChanges.visibility = View.GONE
            binding.buttonCancelEdit.visibility = View.GONE
            binding.buttonChangePassword.visibility = View.VISIBLE
            binding.buttonLogOut.visibility = View.VISIBLE // Düzenleme modu kapalıyken göster
        }
    }

    private fun saveProfileChanges() {
        val newUsername = binding.editTextName.text.toString().trim()
        val newAgeString = binding.editTextAge.text.toString().trim()

        if (newUsername.isEmpty()) {
            binding.tilName.error = "Name cannot be empty" // TextInputLayout ile hata göster
            // Toast.makeText(context, "Ad boş bırakılamaz.", Toast.LENGTH_SHORT).show()
            return
        } else {
            binding.tilName.error = null // Hata yoksa temizle
        }

        if (newAgeString.isEmpty()) {
            binding.tilAge.error = "Age cannot be empty" // TextInputLayout ile hata göster
            // Toast.makeText(context, "Yaş boş bırakılamaz.", Toast.LENGTH_SHORT).show()
            return
        } else {
            binding.tilAge.error = null // Hata yoksa temizle
        }


        val newAge = newAgeString.toIntOrNull()
        if (newAge == null || newAge <= 0) {
            binding.tilAge.error = "Please enter a valid age" // TextInputLayout ile hata göster
            // Toast.makeText(context, "Lütfen geçerli bir yaş girin.", Toast.LENGTH_SHORT).show()
            return
        } else {
            binding.tilAge.error = null // Hata yoksa temizle
        }

        showLoading(true)
        val userId = currentUser?.uid ?: run {
            showLoading(false)
            Toast.makeText(context, "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = hashMapOf<String, Any>(
            "username" to newUsername,
            "age" to newAge // newAge zaten Int
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

    private fun showLogOutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.log_out_dialog_title))
            .setMessage(getString(R.string.log_out_dialog_message))
            .setPositiveButton(getString(R.string.log_out_confirm)) { dialog, _ ->
                logOutUser()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel_dialog)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun logOutUser() {
        showLoading(true)
        auth.signOut()

        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.main_activity_nav, true)
            .build()

        findNavController().navigate(actionUserInfoToOnboarding, null, navOptions)
        // Yönlendirme sonrası bu fragment yok olacağı için showLoading(false) gerekmeyebilir,
        // ama yine de Toast mesajından önce çağrılabilir.
        // showLoading(false)
        Toast.makeText(context, getString(R.string.logged_out_successfully), Toast.LENGTH_SHORT).show()
    }


    private fun showLoading(isLoading: Boolean) {
        binding.progressBarUserInfo.visibility = if (isLoading) View.VISIBLE else View.GONE
        // Butonların durumunu ayarla
        val buttonsAreEnabled = !isLoading
        binding.buttonChangeProfilePic.isEnabled = buttonsAreEnabled
        binding.buttonEditProfile.isEnabled = buttonsAreEnabled
        binding.buttonSaveChanges.isEnabled = buttonsAreEnabled
        binding.buttonCancelEdit.isEnabled = buttonsAreEnabled
        binding.buttonChangePassword.isEnabled = buttonsAreEnabled
        binding.buttonLogOut.isEnabled = buttonsAreEnabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}