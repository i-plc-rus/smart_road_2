package ru.iplc.smart_road.auth.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import ru.iplc.smart_road.data.model.UserProfile
import ru.iplc.smart_road.data.repository.AuthRepository

class ProfileViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _profile = MutableLiveData<AuthRepository.Result<UserProfile>>()

    private var cachedProfile: UserProfile? = null
    val profile: LiveData<AuthRepository.Result<UserProfile>> = _profile

    fun loadProfile() {
        viewModelScope.launch {
            _profile.value = AuthRepository.Result.Loading
            try {
                val result = withTimeout(60000) { repository.getProfile() }
                if (result is AuthRepository.Result.Success) {
                    cachedProfile = result.data
                }
                _profile.value = result
            } catch (e: Exception) {
                _profile.value = AuthRepository.Result.Error(e.message ?: "Ошибка загрузки профиля")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun uploadAvatar(uri: Uri, context: Context) {
        // сохраняем ссылку на текущий профиль ДО Loading
        val currentProfile = (profile.value as? AuthRepository.Result.Success)?.data
        _profile.value = AuthRepository.Result.Loading

        viewModelScope.launch {
            try {
                // 1. Загружаем аватар на сервер
                val uploadResult = withTimeout(60000) { repository.uploadAvatar(uri, context) }

                if (uploadResult is AuthRepository.Result.Success) {
                    val newAvatarUrl = uploadResult.data.avatarUrl

                    // 2. Обновляем профиль
                    val updatedProfile = currentProfile?.copy(avatarUrl = newAvatarUrl)
                    if (updatedProfile != null) {
                        val putResult = withTimeout(60000) { repository.updateProfile(updatedProfile) }
                        if (putResult is AuthRepository.Result.Success) {
                            _profile.value = AuthRepository.Result.Success(putResult.data)
                        } else {
                            _profile.value = putResult
                        }
                    } else {
                        // если профиля не было в LiveData → просто перезагружаем
                        loadProfile()
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
        _profile.value = AuthRepository.Result.Loading
        viewModelScope.launch {
            try {
                val result = withTimeout(60000) { repository.updateProfile(updatedProfile) }
                _profile.value = result
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
