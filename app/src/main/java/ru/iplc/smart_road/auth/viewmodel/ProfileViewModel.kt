package ru.iplc.smart_road.auth.viewmodel

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
