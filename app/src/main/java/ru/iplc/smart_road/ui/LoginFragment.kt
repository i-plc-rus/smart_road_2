package ru.iplc.smart_road.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import ru.iplc.smart_road.R
import ru.iplc.smart_road.SmartRoadApp
import ru.iplc.smart_road.auth.viewmodel.AuthViewModel
import ru.iplc.smart_road.auth.viewmodel.AuthViewModelFactory
import ru.iplc.smart_road.data.repository.AuthRepository
import ru.iplc.smart_road.databinding.FragmentLoginBinding


class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var authViewModel: AuthViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val application = requireActivity().application as SmartRoadApp
        authViewModel = ViewModelProvider(
            this,
            AuthViewModelFactory(application.authRepository)
        )[AuthViewModel::class.java]


        setupToolbar()
        setupViews()
        setupObservers()
    }

    private fun setupToolbar() {
        binding.toolbarSettings.apply {
            title = "Аутентификация"
            setNavigationOnClickListener {
                findNavController().navigate(R.id.nav_home)
            }
        }
    }

    private fun setupViews() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            if (validateInputs(email, password)) {
                setUiEnabled(false)
                authViewModel.login(email, password)
            }
        }

        binding.registerTextView.setOnClickListener {
            if (binding.loginProgress.visibility != View.VISIBLE) {
                findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
            }
        }
    }

    private fun setupObservers() {
        authViewModel.loginResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is AuthRepository.Result.Loading -> {}
                is AuthRepository.Result.Success -> {
                    navigateToMain()
                }
                is AuthRepository.Result.Error -> {
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
        binding.loginForm.animate().alpha(if (enabled) 1f else 0.7f).duration = 200
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
        // Навигация в Main (например, если у тебя graph.xml настроен)
        //findNavController().navigate(R.id.action_loginFragment_to_mainFragment)
        findNavController().navigate(R.id.action_loginFragment_to_profileFragment)
    }

    private fun handleError(errorMessage: String) {
        when {
            errorMessage.contains("401") -> {
                binding.passwordInputLayout.error = getString(R.string.error_invalid_credentials)
            }
            errorMessage.contains("timeout") -> {
                Toast.makeText(requireContext(), getString(R.string.error_network_timeout), Toast.LENGTH_LONG).show()
            }
            else -> {
                Log.d("Login Fragment", errorMessage)
                Toast.makeText(requireContext(), getString(R.string.error_login_failed, errorMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
