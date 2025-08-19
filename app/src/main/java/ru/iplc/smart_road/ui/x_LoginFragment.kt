package ru.iplc.smart_road.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ru.iplc.smart_road.databinding.FragmentLoginBinding

//import org.koin.androidx.viewmodel.ext.android.viewModel

class x_LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

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

        /*binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            if (email.length > 100 || password.length > 100) {
                binding.errorTextView.text = "Input is too long"
                return@setOnClickListener
            }
            if (email.isEmpty() || password.isEmpty()) {
                binding.errorTextView.text = "All fields are required"
                return@setOnClickListener
            }
            viewModel.login(email, password)
        }

        binding.registerButton.setOnClickListener {
            if (isAdded) {
                findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
            }
        }*/
        // Наблюдение за authResult
        //findNavController().navigate(R.id.action_loginFragment_to_profileFragment)
        //viewModel.authResult.observe(viewLifecycleOwner) { result ->
        //    if (result != null) {
                //findNavController().navigate(R.id.action_loginFragment_to_profileFragment)
        //    }
        //if (isAdded && _binding != null) {

                //result.onSuccess {
                    /*try {
                        findNavController().navigate(R.id.action_loginFragment_to_profileFragment)
                    } catch (e: IllegalArgumentException) {
                        // Логируем ошибку навигации
                        android.util.Log.e("LoginFragment", "Navigation error: ${e.message}")
                    }*/
                //}.onFailure { exception ->
                    /*binding.errorTextView.text = when (exception) {
                        is HttpException -> "Server error: ${exception.code()}"
                        is IOException -> "Network error"
                        else -> exception.message ?: "Unknown error"
                    }*/
                //}
            //}
       // }
        /*loginViewModel.tokenLiveData.observe(viewLifecycleOwner, Observer { token ->
            //progressBar.visibility = View.GONE
            if (token != null) {
                findNavController().navigate(R.id.action_loginFragment_to_profileFragment)
            }
        })*/
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


