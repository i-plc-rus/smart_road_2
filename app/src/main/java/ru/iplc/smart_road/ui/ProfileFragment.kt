package ru.iplc.smart_road.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import ru.iplc.smart_road.R
import ru.iplc.smart_road.SmartRoadApp
import ru.iplc.smart_road.auth.viewmodel.ProfileViewModel
import ru.iplc.smart_road.auth.viewmodel.ProfileViewModelFactory
import ru.iplc.smart_road.data.repository.AuthRepository
import ru.iplc.smart_road.databinding.FragmentProfileBinding
import java.io.File
import java.io.FileOutputStream

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
        setupToolbar()
        viewModel.loadProfile()
    }

    private fun setupToolbar() {
        binding.toolbarSettings.apply {
            title = "Профиль"
            setNavigationOnClickListener {
                findNavController().navigate(R.id.nav_home)
            }
        }
    }


    private fun showLoadingOverlay(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.profileContent.isEnabled = !show
    }



    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                selectedAvatarUri = it
                // Сжимаем при необходимости
                val finalUri = compressImageIfNeeded(it)

                // Локальный предпросмотр
                Glide.with(this).load(finalUri).circleCrop().into(binding.avatarImageView)

                // Показываем overlay и блокируем форму
                showLoadingOverlay(true)

                // Отправляем на сервер
                viewModel.uploadAvatar(finalUri, requireContext())
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
                    showLoadingOverlay(false) // ✅ разблокируем форму
                }
                is AuthRepository.Result.Error -> {
                    Toast.makeText(requireContext(), "Ошибка: ${result.message}", Toast.LENGTH_SHORT).show()
                    binding.avatarProgress.visibility = View.GONE
                    showLoadingOverlay(false) // ✅ тоже разблокируем
                }
                is AuthRepository.Result.Loading -> {
                    // ✅ теперь всегда блокируем форму
                    showLoadingOverlay(true)
                }
            }
        }
    }

    private fun compressImageIfNeeded(uri: Uri): Uri {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val byteArray = inputStream?.readBytes()
        inputStream?.close()

        if (byteArray == null) return uri

        // Если меньше 1 МБ, отдаем как есть
        if (byteArray.size <= 1 * 1024 * 1024) {
            return uri
        }

        // Сжимаем в JPEG
        val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
        val file = File(requireContext().cacheDir, "compressed_avatar.jpg")
        val outputStream = FileOutputStream(file)

        var quality = 90
        do {
            outputStream.flush()
            outputStream.close()
            file.delete()

            val newFile = File(requireContext().cacheDir, "compressed_avatar.jpg")
            val newStream = FileOutputStream(newFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, newStream)
            newStream.flush()
            newStream.close()

            quality -= 10
        } while (newFile.length() > 1 * 1024 * 1024 && quality > 10)

        return Uri.fromFile(file)
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