package ru.iplc.smart_road.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import ru.iplc.smart_road.R
import ru.iplc.smart_road.SmartRoadApp
import ru.iplc.smart_road.auth.viewmodel.AuthViewModel
import ru.iplc.smart_road.auth.viewmodel.AuthViewModelFactory
import ru.iplc.smart_road.data.model.User
import ru.iplc.smart_road.data.model.UserProfile
import ru.iplc.smart_road.data.repository.AuthRepository
import ru.iplc.smart_road.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {
    private lateinit var authViewModel: AuthViewModel
    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val application = application as SmartRoadApp
        authViewModel = ViewModelProvider(
            this,
            AuthViewModelFactory(application.authRepository)
        )[AuthViewModel::class.java]

        setupViews()
        setupObservers()

        // Load profile data
        authViewModel.getProfile()
    }

    private fun setupViews() {
        binding.logoutButton.setOnClickListener {
            authViewModel.logout()
            navigateToLogin()
        }
    }

    private fun setupObservers() {
        authViewModel.profileResult.observe(this) { result ->
            when (result) {
                is AuthRepository.Result.Loading -> {
                    showLoading(true)
                }
                is AuthRepository.Result.Success -> {
                    showLoading(false)
                    displayProfile(result.data)
                }
                is AuthRepository.Result.Error -> {
                    showLoading(false)
                    handleError(result.message)
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.profileContent.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.logoutButton.isEnabled = !isLoading
    }

    private fun displayProfile(user: UserProfile) {
        binding.nameTextView.text = user.username
        binding.emailTextView.text = user.email
        //binding.createdAtTextView.text = "Member since ${user.createdAt}"
    }

    private fun handleError(errorMessage: String) {
        when {
            errorMessage.contains("401") -> {
                showSessionExpired()
            }
            else -> {
                Toast.makeText(
                    this,
                    "Failed to load profile: $errorMessage",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showSessionExpired() {
        Toast.makeText(
            this,
            "Session expired. Please login again.",
            Toast.LENGTH_LONG
        ).show()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}