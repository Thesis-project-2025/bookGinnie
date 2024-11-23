package com.example.bookgenie

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.bookgenie.databinding.FragmentSignUpBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

class SignUpFragment : Fragment() {

    private lateinit var binding: FragmentSignUpBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSignUpBinding.inflate(inflater, container, false)

        // FirebaseAuth instance'ını alıyoruz
        auth = FirebaseAuth.getInstance()

        val button = binding.signUpButton

        button.setOnClickListener {
            // Butona tıklanınca email, password ve confirmpassword bilgilerini alıyoruz
            val email = binding.emailSignUp.text.toString()
            val password = binding.passwordSignUp.text.toString()
            val confirmpassword = binding.confirmPassword.text.toString()

            if (password == confirmpassword) {
                // Şifreler eşleşiyorsa kullanıcıyı kayıt ediyoruz
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Snackbar.make(binding.emailSignUp, "Successfully signed up!", Snackbar.LENGTH_SHORT)
                                .show()
                            // Kayıt başarılı, giriş ekranına yönlendirme
                            Navigation.findNavController(binding.root)
                                .navigate(R.id.onboardingFragment)
                        } else {
                            // Hata mesajı göster
                            Toast.makeText(context, "Kayıt Yapılamadı: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                // Şifreler eşleşmiyorsa hata mesajı göster
                Toast.makeText(context, "Şifreler eşleşmiyor.", Toast.LENGTH_SHORT).show()
            }
        }

        return binding.root
    }
}