package ru.iplc.smart_road.auth.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.iplc.smart_road.data.model.AuthResponse
import ru.iplc.smart_road.data.model.User
import ru.iplc.smart_road.data.model.UserProfile
import ru.iplc.smart_road.data.repository.AuthRepository

// AuthViewModel.kt
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _loginResult = MutableLiveData<AuthRepository.Result<AuthResponse>>()
    val loginResult: LiveData<AuthRepository.Result<AuthResponse>> = _loginResult

    private val _registerResult = MutableLiveData<AuthRepository.Result<AuthResponse>>()
    val registerResult: LiveData<AuthRepository.Result<AuthResponse>> = _registerResult

    private val _profileResult = MutableLiveData<AuthRepository.Result<UserProfile>>()
    val profileResult: LiveData<AuthRepository.Result<UserProfile>> = _profileResult

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginResult.value = authRepository.login(email, password)
        }
    }

    fun register(username: String, fio: String, phone: String, email: String, password: String) {
        viewModelScope.launch {
            _registerResult.value = authRepository.register(username, fio, phone, email, password)
        }
    }

    fun getProfile() {
        viewModelScope.launch {
            _profileResult.value = authRepository.getProfile()
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}

