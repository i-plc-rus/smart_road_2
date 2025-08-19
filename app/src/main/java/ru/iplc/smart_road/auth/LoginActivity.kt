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
import ru.iplc.smart_road.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var authViewModel: AuthViewModel
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
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
        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            if (validateInputs(email, password)) {
                // Показываем прогресс-бар перед отправкой запроса
                //showLoading(true)
                setUiEnabled(false)
                authViewModel.login(email, password)
            }
        }

        binding.registerTextView.setOnClickListener {
            if (binding.loginProgress.visibility != View.VISIBLE) {
                startActivity(Intent(this, RegisterActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }
    }



    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            if (validateInputs(email, password)) {
                authViewModel.login(email, password)
            }
        }

        binding.registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun setupObservers() {
        authViewModel.loginResult.observe(this) { result ->
            when (result) {
                is AuthRepository.Result.Loading -> {
                    // Состояние загрузки уже активировано при нажатии кнопки
                }
                is AuthRepository.Result.Success -> {
                    //showLoading(false)
                    navigateToMain()
                }
                is AuthRepository.Result.Error -> {
                    //showLoading(false)
                    setUiEnabled(true)
                    handleError(result.message)
                }
            }
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        binding.loginProgress.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.loginButton.isEnabled = enabled
        binding.registerTextView.isEnabled = enabled
        binding.emailEditText.isEnabled = enabled
        binding.passwordEditText.isEnabled = enabled

        // Плавное изменение прозрачности
        binding.loginForm.animate().alpha(if (enabled) 1f else 0.7f).duration = 200
    }

    private fun showLoading(isLoading: Boolean) {
        binding.loginProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.loginButton.isEnabled = !isLoading
        binding.registerTextView.isEnabled = !isLoading

        // Дополнительно можно затемнять форму во время загрузки
        binding.loginForm.alpha = if (isLoading) 0.5f else 1f
    }


    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

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

        return isValid
    }



    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)

        // Завершаем текущую активити после перехода
        finish()
    }

    private fun handleError(errorMessage: String) {
        when {
            errorMessage.contains("401") -> {
                binding.passwordInputLayout.error = getString(R.string.error_invalid_credentials)
            }
            errorMessage.contains("timeout") -> {
                Toast.makeText(
                    this,
                    getString(R.string.error_network_timeout),
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                Toast.makeText(
                    this,
                    getString(R.string.error_login_failed, errorMessage),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    /*override fun onBackPressed() {
        // Блокируем кнопку "назад" во время загрузки
        if (binding.loginProgress.visibility != View.VISIBLE) {
            super.onBackPressed()
        }
    }*/

}