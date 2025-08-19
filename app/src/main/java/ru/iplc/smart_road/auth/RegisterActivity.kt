package ru.iplc.smart_road.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import ru.iplc.smart_road.MainActivity
import ru.iplc.smart_road.R
import ru.iplc.smart_road.SmartRoadApp
import ru.iplc.smart_road.auth.viewmodel.AuthViewModel
import ru.iplc.smart_road.auth.viewmodel.AuthViewModelFactory
import ru.iplc.smart_road.data.repository.AuthRepository
import ru.iplc.smart_road.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {
    private lateinit var authViewModel: AuthViewModel
    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val application = application as SmartRoadApp
        authViewModel = ViewModelProvider(
            this,
            AuthViewModelFactory(application.authRepository)
        )[AuthViewModel::class.java]

        setupViews()
        setupObservers()
    }

    private fun setupViews() {
        binding.registerButton.setOnClickListener {



            val username = binding.usernameEditText.text.toString()
            val fio = binding.nameEditText.text.toString()
            val phone = binding.phoneEditText.text.toString()
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()


            if (validateInputs(username, fio, phone, email, password, confirmPassword)) {
                authViewModel.register(username, fio, phone, email, password)
            }
        }

        binding.loginTextView.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun setupObservers() {
        authViewModel.registerResult.observe(this) { result ->
            when (result) {
                is AuthRepository.Result.Loading -> {
                    showLoading(true)
                }
                is AuthRepository.Result.Success -> {
                    showLoading(false)
                    handleRegistrationSuccess()
                }
                is AuthRepository.Result.Error -> {
                    showLoading(false)
                    handleRegistrationError(result.message)
                }
            }
        }
    }

    private fun validateInputs(
        username: String,
        fio: String,
        phone: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true

        if (username.isEmpty()) {
            binding.usernameInputLayout.error = getString(R.string.error_name_empty)
            isValid = false
        } else {
            binding.usernameInputLayout.error = null
        }
        if (fio.isEmpty()) {
            binding.nameInputLayout.error = getString(R.string.error_fio_empty)
            isValid = false
        } else {
            binding.nameInputLayout.error = null
        }

        if (phone.isEmpty()) {
            binding.phoneInputLayout.error = getString(R.string.error_phone_empty)
            isValid = false
        } else {
            binding.phoneInputLayout.error = null
        }

        if (email.isEmpty()) {
            binding.emailInputLayout.error = getString(R.string.error_email_empty)
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = getString(R.string.error_email_invalid)
            isValid = false
        } else {
            binding.emailInputLayout.error = null
        }

        if (password.isEmpty()) {
            binding.passwordInputLayout.error = getString(R.string.error_password_empty)
            isValid = false
        } else if (password.length < 6) {
            binding.passwordInputLayout.error = getString(R.string.error_password_short)
            isValid = false
        } else {
            binding.passwordInputLayout.error = null
        }

        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordInputLayout.error = getString(R.string.error_confirm_password_empty)
            isValid = false
        } else if (password != confirmPassword) {
            binding.confirmPasswordInputLayout.error = getString(R.string.error_passwords_not_match)
            isValid = false
        } else {
            binding.confirmPasswordInputLayout.error = null
        }

        return isValid
    }

    private fun showLoading(isLoading: Boolean) {
        binding.registerProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.registerButton.isEnabled = !isLoading
        binding.loginTextView.isEnabled = !isLoading
    }

    private fun handleRegistrationSuccess() {
        Toast.makeText(this, getString(R.string.registration_success), Toast.LENGTH_SHORT).show()
        //navigateToProfile()
        navigateToMain()
    }

    private fun handleRegistrationError(errorMessage: String) {
        when {
            errorMessage.contains("409") -> {
                binding.emailInputLayout.error = getString(R.string.error_email_already_registered)
                Toast.makeText(this, getString(R.string.error_email_already_registered), Toast.LENGTH_LONG).show()
            }
            errorMessage.contains("timeout") -> {
                Toast.makeText(this, getString(R.string.error_network_timeout), Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(this,
                    getString(R.string.error_registration_failed, errorMessage),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun navigateToProfile() {
        startActivity(Intent(this, ProfileActivity::class.java))
        finish()
    }
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
}