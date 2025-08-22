package ru.iplc.smart_road.auth.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import ru.iplc.smart_road.data.model.User
import ru.iplc.smart_road.data.model.UserProfile
import ru.iplc.smart_road.data.repository.AuthRepository

class ProfileViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _profile = MutableLiveData<AuthRepository.Result<UserProfile>>()
    val profile: LiveData<AuthRepository.Result<UserProfile>> = _profile

    fun loadProfile() {
        viewModelScope.launch {
            _profile.value = AuthRepository.Result.Loading
            val result = repository.getProfile()
            _profile.value = result
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun uploadAvatar(uri: Uri, context: Context) {
        val currentProfile = (_profile.value as? AuthRepository.Result.Success)?.data

        currentProfile?.let { user ->
            // 1. Сразу показываем локальный uri
            val tempProfile = user.copy(
                username = user.username ?: "",
                fio = user.fio ?: "",
                phone = user.phone ?: "",
                email = user.email ?: "",
                password = user.password ?: "",
                avatarUrl = uri.toString()
            )
            _profile.value = AuthRepository.Result.Success(tempProfile)
        }




        viewModelScope.launch {
            try {
                // 2. Загружаем аватар на сервер
                val uploadResult = repository.uploadAvatar(uri, context)
                if (uploadResult is AuthRepository.Result.Success) {
                    val newAvatarUrl = uploadResult.data.avatarUrl

                    // 3. PUT запрос для обновления профиля
                    val updatedProfile = currentProfile?.copy(avatarUrl = newAvatarUrl)
                    if (updatedProfile != null) {
                        val putResult = repository.updateProfile(updatedProfile)
                        if (putResult is AuthRepository.Result.Success) {
                            // 4. Обновляем LiveData окончательно
                            _profile.value = AuthRepository.Result.Success(putResult.data)
                        } else if (putResult is AuthRepository.Result.Error) {
                            _profile.value = AuthRepository.Result.Error(putResult.message)
                        }
                    }
                } else if (uploadResult is AuthRepository.Result.Error) {
                    _profile.value = AuthRepository.Result.Error(uploadResult.message)
                }
            } catch (e: Exception) {
                _profile.value = AuthRepository.Result.Error(e.message ?: "Ошибка загрузки аватара")
            }
        }
    }

    fun updateProfile(updatedProfile: UserProfile) {
        viewModelScope.launch {
            _profile.value = AuthRepository.Result.Loading
            try {
                val result = repository.updateProfile(updatedProfile)
                if (result is AuthRepository.Result.Success) {
                    _profile.value = AuthRepository.Result.Success(result.data)
                } else if (result is AuthRepository.Result.Error) {
                    _profile.value = AuthRepository.Result.Error(result.message)
                }
            } catch (e: Exception) {
                _profile.value = AuthRepository.Result.Error(e.message ?: "Ошибка обновления профиля")
            }
        }
    }


}

class ProfileViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
