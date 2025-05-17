package com.example.bookgenie

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.bookgenie.databinding.FragmentSignUpBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignUpFragment : Fragment() {

    private lateinit var binding: FragmentSignUpBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSignUpBinding.inflate(inflater, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val button = binding.signUpButton

        button.setOnClickListener {
            val username = binding.usernameSignUp.text.toString().trim()
            val email = binding.emailSignUp.text.toString().trim()
            val ageString = binding.ageSignUp.text.toString().trim() // Yaş bilgisi XML'deki sırasından bağımsız olarak ID ile alınır
            val password = binding.passwordSignUp.text.toString()
            val confirmpassword = binding.confirmPassword.text.toString()

            // Tüm alanların dolu olup olmadığını kontrol et
            if (username.isEmpty() || email.isEmpty() || ageString.isEmpty() || password.isEmpty() || confirmpassword.isEmpty()) {
                Toast.makeText(context, "Lütfen tüm alanları doldurun.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Yaşın geçerli bir sayı olup olmadığını kontrol et
            val age = ageString.toIntOrNull()
            if (age == null || age <= 0) {
                Toast.makeText(context, "Lütfen geçerli bir yaş girin.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            if (password == confirmpassword) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val firebaseUser = auth.currentUser
                            firebaseUser?.let { user ->
                                val userId = user.uid

                                val userInfo = hashMapOf(
                                    "username" to username,
                                    "email" to email,
                                    "age" to age // Yaş bilgisi (Int) Map'e eklenir
                                )

                                db.collection("users_info").document(userId)
                                    .set(userInfo)
                                    .addOnSuccessListener {
                                        Log.d("Firestore", "Kullanıcı bilgileri başarıyla eklendi: $userId")
                                        Snackbar.make(binding.root, "Successfully signed up!", Snackbar.LENGTH_SHORT).show()
                                        Navigation.findNavController(binding.root)
                                            .navigate(R.id.onboardingFragment)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.w("Firestore", "Kullanıcı bilgileri eklenirken hata oluştu", e)
                                        Toast.makeText(context, "Kullanıcı bilgileri kaydedilemedi: ${e.message}", Toast.LENGTH_LONG).show()
                                        // user.delete()... (opsiyonel: auth kullanıcısını sil)
                                    }
                            } ?: run {
                                Toast.makeText(context, "Kullanıcı oluşturuldu ancak detaylar alınamadı.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.w("AuthError", "createUserWithEmail:failure", task.exception)
                            Toast.makeText(context, "Kayıt Yapılamadı: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(context, "Şifreler eşleşmiyor.", Toast.LENGTH_SHORT).show()
            }
        }
        return binding.root
    }
}