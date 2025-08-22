package ru.iplc.smart_road.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import ru.iplc.smart_road.R
import ru.iplc.smart_road.SmartRoadApp
import ru.iplc.smart_road.auth.viewmodel.ProfileViewModel
import ru.iplc.smart_road.auth.viewmodel.ProfileViewModelFactory
import ru.iplc.smart_road.data.repository.AuthRepository
import ru.iplc.smart_road.databinding.FragmentProfileBinding
import androidx.activity.result.contract.ActivityResultContracts

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProfileViewModel

    private val PICK_IMAGE_REQUEST = 1001
    private var selectedAvatarUri: Uri? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireActivity().application as SmartRoadApp
        viewModel = ViewModelProvider(
            this,
            ProfileViewModelFactory(app.authRepository)
        )[ProfileViewModel::class.java]

        setupViews()
        setupObservers()
        viewModel.loadProfile()
    }


    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                selectedAvatarUri = it
                // локальный аватар
                Glide.with(this).load(it).circleCrop().into(binding.avatarImageView)
                // показать спиннер
                binding.avatarProgress.visibility = View.VISIBLE
                viewModel.uploadAvatar(it, requireContext())
            }
        }


    private fun setupViews() {
        binding.changeAvatarButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.saveButton.setOnClickListener {
            val currentProfile = (viewModel.profile.value as? AuthRepository.Result.Success)?.data
            currentProfile?.let {
                val updatedProfile = it.copy(
                    username = binding.usernameEditText.text.toString().trim(),
                    fio = binding.fioEditText.text.toString().trim(),
                    phone = binding.phoneEditText.text.toString().trim(),
                    email = binding.emailEditText.text.toString().trim()
                )
                viewModel.updateProfile(updatedProfile)
            }
        }

        binding.logoutButton.setOnClickListener {
            viewModel.logout()
            // Навигация обратно на экран логина
            requireActivity().finish()
        }
    }



    private fun setupObservers() {
        viewModel.profile.observe(viewLifecycleOwner) { result ->
            when (result) {
                is AuthRepository.Result.Success -> {
                    val user = result.data
                    binding.usernameEditText.setText(user.username)
                    binding.fioEditText.setText(user.fio)
                    binding.phoneEditText.setText(user.phone)
                    binding.emailEditText.setText(user.email)

                    if (!user.avatarUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(user.avatarUrl)
                            .circleCrop()
                            .into(binding.avatarImageView)
                    }

                    binding.avatarProgress.visibility = View.GONE
                }
                is AuthRepository.Result.Error -> {
                    Toast.makeText(requireContext(), "Ошибка: ${result.message}", Toast.LENGTH_SHORT).show()
                    binding.avatarProgress.visibility = View.GONE
                }
                is AuthRepository.Result.Loading -> {
                    // Спиннер уже показан в момент выбора, можно оставить пустым
                }
            }
        }
    }


//    @Deprecated("Deprecated in Java")
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
//            selectedAvatarUri = data.data
//            Glide.with(this).load(selectedAvatarUri).circleCrop().into(binding.avatarImageView)
//
//            selectedAvatarUri?.let { uri ->
//                viewModel.uploadAvatar(uri, requireContext())
//            }
//        }
//    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
