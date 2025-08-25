package ru.iplc.smart_road.auth

import android.os.Bundle
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
import ru.iplc.smart_road.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var authViewModel: AuthViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
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
        binding.toolbarRegister.apply {
            //title = getString(R.string.register_title)
            title = "Регистрация"
            setNavigationOnClickListener { findNavController().navigateUp() }
        }
    }

    private fun setupViews() {
        binding.registerButton.setOnClickListener {
            val username = binding.usernameEditText.text.toString()
            val fio = binding.nameEditText.text.toString()
            val phone = binding.phoneEditText.text.toString()
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            val confirmPassword = binding.confirmPasswordEditText.text.toString()

            if (validateInputs(username, fio, phone, email, password, confirmPassword)) {
                authViewModel.register(username, fio, phone, email, password)
            }
        }

        binding.loginTextView.setOnClickListener {
            if (binding.registerProgress.visibility != View.VISIBLE) {
                findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
            }
        }
    }

    private fun setupObservers() {
        authViewModel.registerResult.observe(viewLifecycleOwner) { result ->
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
        } else binding.usernameInputLayout.error = null

        if (fio.isEmpty()) {
            binding.nameInputLayout.error = getString(R.string.error_fio_empty)
            isValid = false
        } else binding.nameInputLayout.error = null

        if (phone.isEmpty()) {
            binding.phoneInputLayout.error = getString(R.string.error_phone_empty)
            isValid = false
        } else binding.phoneInputLayout.error = null

        if (email.isEmpty()) {
            binding.emailInputLayout.error = getString(R.string.error_email_empty)
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = getString(R.string.error_email_invalid)
            isValid = false
        } else binding.emailInputLayout.error = null

        if (password.isEmpty()) {
            binding.passwordInputLayout.error = getString(R.string.error_password_empty)
            isValid = false
        } else if (password.length < 6) {
            binding.passwordInputLayout.error = getString(R.string.error_password_short)
            isValid = false
        } else binding.passwordInputLayout.error = null

        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordInputLayout.error = getString(R.string.error_confirm_password_empty)
            isValid = false
        } else if (password != confirmPassword) {
            binding.confirmPasswordInputLayout.error = getString(R.string.error_passwords_not_match)
            isValid = false
        } else binding.confirmPasswordInputLayout.error = null

        return isValid
    }

    private fun showLoading(isLoading: Boolean) {
        binding.registerProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.registerButton.isEnabled = !isLoading
        binding.loginTextView.isEnabled = !isLoading
    }

    private fun handleRegistrationSuccess() {
        Toast.makeText(requireContext(), getString(R.string.registration_success), Toast.LENGTH_SHORT).show()
        findNavController().navigate(R.id.action_registerFragment_to_profileFragment)
    }

    private fun handleRegistrationError(errorMessage: String) {
        when {
            errorMessage.contains("409") -> {
                binding.emailInputLayout.error = getString(R.string.error_email_already_registered)
                Toast.makeText(requireContext(), getString(R.string.error_email_already_registered), Toast.LENGTH_LONG).show()
            }
            errorMessage.contains("timeout") -> {
                Toast.makeText(requireContext(), getString(R.string.error_network_timeout), Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(requireContext(),
                    getString(R.string.error_registration_failed, errorMessage),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
