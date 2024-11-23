package com.example.bookgenie

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.bookgenie.databinding.FragmentLogInBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

class LogInFragment : Fragment() {

    private lateinit var binding: FragmentLogInBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentLogInBinding.inflate(inflater, container, false)

        // FirebaseAuth instance'ını alıyoruz
        auth = FirebaseAuth.getInstance()

        val button = binding.logInButton

        button.setOnClickListener {
            // Butona tıklanınca email ve password bilgilerini alıyoruz
            val email = binding.emailLogIn.text.toString()
            val password = binding.passwordLogIn.text.toString()

            // Giriş işlemi yapıyoruz
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Giriş başarılıysa ana ekrana yönlendirme
                        Snackbar.make(binding.emailLogIn, "Successfully logged in!", Snackbar.LENGTH_SHORT)
                            .show()
                        Navigation.findNavController(binding.root)
                            .navigate(R.id.onboardingFragment)  // homeFragment'i kendi ana ekranınızla değiştirin
                    } else {
                        // Hata mesajı göster
                        Toast.makeText(context, "Giriş Yapılamadı: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        return binding.root
    }
}