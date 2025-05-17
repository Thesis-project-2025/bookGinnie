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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bookgenie.databinding.FragmentUserInfoBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class UserInfoFragment : Fragment() {

    private var _binding: FragmentUserInfoBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentUser: FirebaseUser? = null

    private var originalUsername: String? = null
    private var originalAge: String? = null
    private var originalSelectedAvatarName: String? = null // Firestore'dan gelen avatar adı

    // Drawable klasörünüzdeki avatar resimlerinin dosya adları (uzantısız)
    // Bu listeyi kendi avatar dosyalarınıza göre güncelleyin.
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
        updateUIEditMode(false) // Başlangıçta düzenleme modu kapalı
    }

    // Verilen drawable adına göre resource ID'sini alır
    private fun getDrawableResourceIdByName(name: String): Int {
        return try {
            resources.getIdentifier(name, "drawable", requireContext().packageName)
        } catch (e: Exception) {
            Log.e("UserInfoFragment", "Error getting resource ID for $name", e)
            0 // Hata durumunda 0 döner
        }
    }

    private fun loadUserProfile() {
        showLoading(true)
        // Başlangıçta Lottie'yi gizleyip, avatar varsa onu, yoksa varsayılanı göstereceğiz.
        binding.profileLottieView.visibility = View.GONE
        binding.actualProfileImageView.visibility = View.VISIBLE // ImageView her zaman görünür olsun

        if (currentUser != null) {
            val userId = currentUser!!.uid
            val userDocRef = db.collection("users_info").document(userId)

            userDocRef.get()
                .addOnSuccessListener { document ->
                    showLoading(false)
                    if (document != null && document.exists()) {
                        originalUsername = document.getString("username")
                        val email = currentUser?.email // E-posta her zaman Auth'dan alınmalı
                        originalAge = document.getLong("age")?.toString()
                        originalSelectedAvatarName = document.getString("selectedAvatarName")

                        binding.editTextName.setText(originalUsername ?: "")
                        binding.editTextEmail.setText(email ?: "")
                        binding.editTextAge.setText(originalAge ?: "")

                        if (!originalSelectedAvatarName.isNullOrEmpty()) {
                            val avatarResId = getDrawableResourceIdByName(originalSelectedAvatarName!!)
                            if (avatarResId != 0) {
                                Glide.with(this)
                                    .load(avatarResId)
                                    .placeholder(R.drawable.person) // Varsayılan avatarınız
                                    .error(R.drawable.person) // Hata durumunda varsayılan avatar
                                    .into(binding.actualProfileImageView)
                            } else {
                                // Kaynak bulunamadı, varsayılanı göster
                                binding.actualProfileImageView.setImageResource(R.drawable.person)
                                Log.w("UserInfoFragment", "Avatar resource not found: $originalSelectedAvatarName, showing default.")
                            }
                        } else {
                            // Avatar seçilmemişse varsayılanı göster
                            binding.actualProfileImageView.setImageResource(R.drawable.person)
                        }
                        Log.d("UserInfoFragment", "Kullanıcı bilgileri başarıyla yüklendi.")
                    } else {
                        Log.d("UserInfoFragment", "Kullanıcı belgesi bulunamadı.")
                        Toast.makeText(context, getString(R.string.user_info_not_found), Toast.LENGTH_SHORT).show()
                        binding.actualProfileImageView.setImageResource(R.drawable.person) // Varsayılanı göster
                    }
                }
                .addOnFailureListener { exception ->
                    showLoading(false)
                    binding.actualProfileImageView.setImageResource(R.drawable.person) // Varsayılanı göster
                    Log.e("UserInfoFragment", "Kullanıcı bilgileri yüklenirken hata oluştu.", exception)
                    Toast.makeText(context, getString(R.string.error_loading_user_info, exception.message), Toast.LENGTH_LONG).show()
                }
        } else {
            showLoading(false)
            binding.actualProfileImageView.setImageResource(R.drawable.person) // Varsayılanı göster
            Log.w("UserInfoFragment", "Giriş yapmış kullanıcı bulunamadı.")
            Toast.makeText(context, getString(R.string.please_sign_in), Toast.LENGTH_SHORT).show()
            // Gerekirse giriş ekranına yönlendir
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
            // Değişiklikleri geri al ve düzenleme modunu kapat
            binding.editTextName.setText(originalUsername ?: "")
            binding.editTextAge.setText(originalAge ?: "")

            if (!originalSelectedAvatarName.isNullOrEmpty()) {
                val avatarResId = getDrawableResourceIdByName(originalSelectedAvatarName!!)
                if (avatarResId != 0) {
                    Glide.with(this).load(avatarResId).into(binding.actualProfileImageView)
                } else {
                    binding.actualProfileImageView.setImageResource(R.drawable.person)
                }
            } else {
                binding.actualProfileImageView.setImageResource(R.drawable.person)
            }
            updateUIEditMode(false)
        }

        binding.buttonChangePassword.setOnClickListener {
            sendPasswordResetEmail()
        }
    }

    private fun showAvatarPickerDialog() {
        if (avatarPickerDialog != null && avatarPickerDialog!!.isShowing) {
            return // Dialog zaten açıksa tekrar açma
        }

        // dialog_avatar_picker.xml layout'unu inflate et
        val dialogView = LayoutInflater.from(context).inflate(R.layout.fragment_dialog_avatar_picker, null)
        val recyclerViewAvatars = dialogView.findViewById<RecyclerView>(R.id.recyclerViewAvatars)

        // Avatar isimlerinden drawable ID'lerini oluştur
        val avatarResourceIds = avatarDrawableNames.mapNotNull { name ->
            val resId = getDrawableResourceIdByName(name)
            if (resId != 0) resId else null // Geçerli ID ise listeye ekle
        }

        if (avatarResourceIds.isEmpty()){
            Toast.makeText(context, "Gösterilecek avatar bulunamadı.", Toast.LENGTH_SHORT).show()
            Log.e("UserInfoFragment", "No avatar resources found to display in picker.")
            return
        }

        // AvatarAdapter'ı oluştur ve RecyclerView'a ata
        val adapter = AvatarAdapter(requireContext(), avatarResourceIds) { selectedAvatarResId ->
            // Seçilen avatarın drawable adını bul (Firestore'a kaydetmek için)
            val selectedName = avatarDrawableNames.find { name ->
                getDrawableResourceIdByName(name) == selectedAvatarResId
            }

            selectedName?.let {
                updateSelectedAvatarInFirestore(it) // Firestore'a kaydet
                // Seçilen avatarı profil resminde göster
                Glide.with(this)
                    .load(selectedAvatarResId)
                    .placeholder(R.drawable.person)
                    .error(R.drawable.person)
                    .into(binding.actualProfileImageView)
            }
            avatarPickerDialog?.dismiss() // Dialog'u kapat
        }

        recyclerViewAvatars.layoutManager = GridLayoutManager(context, 4) // 4 sütunlu grid
        recyclerViewAvatars.adapter = adapter

        // Dialog'u oluştur ve göster
        avatarPickerDialog = Dialog(requireContext())
        avatarPickerDialog?.requestWindowFeature(Window.FEATURE_NO_TITLE) // Başlığı kaldır
        avatarPickerDialog?.setContentView(dialogView)
        avatarPickerDialog?.show()
    }

    private fun updateSelectedAvatarInFirestore(avatarName: String) {
        val userId = currentUser?.uid ?: return // Kullanıcı ID'si yoksa işlem yapma
        showLoading(true)
        db.collection("users_info").document(userId)
            .update("selectedAvatarName", avatarName) // Firestore'a avatar adını kaydet
            .addOnSuccessListener {
                showLoading(false)
                originalSelectedAvatarName = avatarName // Orijinal seçimi güncelle (iptal durumu için)
                Toast.makeText(context, getString(R.string.avatar_updated), Toast.LENGTH_SHORT).show()
                Log.d("UserInfoFragment", "Seçilen avatar Firestore'a güncellendi: $avatarName")
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e("UserInfoFragment", "Seçilen avatar güncellenirken hata oluştu", exception)
                Toast.makeText(context, getString(R.string.avatar_update_failed), Toast.LENGTH_LONG).show()
            }
    }

    private fun updateUIEditMode(isEdit: Boolean) {
        binding.editTextName.isEnabled = isEdit
        binding.editTextAge.isEnabled = isEdit
        // E-posta genellikle Firebase Auth üzerinden güncellenir, bu yüzden burada düzenlenemez bırakıldı.

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

        if (newUsername.isEmpty()) { // Yaş boş bırakılabilir, ama isim zorunlu olsun
            Toast.makeText(context, "İsim boş bırakılamaz.", Toast.LENGTH_SHORT).show()
            return
        }

        val newAge: Long? = if (newAgeString.isNotEmpty()) {
            val parsedAge = newAgeString.toLongOrNull()
            if (parsedAge == null || parsedAge <= 0) {
                Toast.makeText(context, "Lütfen geçerli bir yaş girin.", Toast.LENGTH_SHORT).show()
                return
            }
            parsedAge
        } else {
            null // Yaş boş bırakılmışsa null
        }


        showLoading(true)
        val userId = currentUser?.uid ?: return

        val updates = hashMapOf<String, Any?>( // Any? yaparak null değerlere izin ver
            "username" to newUsername
        )
        if (newAge != null) {
            updates["age"] = newAge
        } else {
            // Eğer yaş boş bırakıldıysa ve Firestore'da silmek istiyorsanız:
            // updates["age"] = FieldValue.delete() // Bu satırı kullanmak için FieldValue import edilmeli
            // Veya null olarak bırakabilirsiniz, Firestore'da alan null olur.
            // Şimdilik null olarak bırakalım, eğer alanın varlığı önemli değilse.
            // Eğer yaş alanı her zaman olmalıysa, yukarıdaki isEmpty kontrolünü yaş için de yapın.
        }


        db.collection("users_info").document(userId)
            .update(updates as Map<String, Any>) // Null değerler varsa Firestore bunları görmezden gelir veya siler (yapılandırmaya bağlı)
            .addOnSuccessListener {
                showLoading(false)
                originalUsername = newUsername // Orijinal bilgileri güncelle
                originalAge = newAge?.toString()
                Toast.makeText(context, getString(R.string.profile_update_success), Toast.LENGTH_SHORT).show()
                updateUIEditMode(false)
                Log.d("UserInfoFragment", "Profil başarıyla güncellendi.")
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e("UserInfoFragment", "Profil güncellenirken hata oluştu", exception)
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
                        Log.d("UserInfoFragment", "Şifre sıfırlama e-postası gönderildi.")
                    } else {
                        Toast.makeText(context, getString(R.string.password_reset_email_failed) + ": ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        Log.e("UserInfoFragment", "Şifre sıfırlama e-postası gönderilemedi.", task.exception)
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
        avatarPickerDialog?.dismiss() // Fragment yok edilirken dialog açıksa kapat
        _binding = null // Bellek sızıntılarını önlemek için _binding'i null yap
    }
}
