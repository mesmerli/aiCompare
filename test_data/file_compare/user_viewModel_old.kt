package com.example.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserViewModel(private val apiService: ApiService) : ViewModel() {
    private val _state = MutableStateFlow<UserState>(UserState.Idle)
    val state: StateFlow<UserState> = _state

    fun fetchUser(userId: String) {
        viewModelScope.launch {
            _state.value = UserState.Loading
            try {
                val user = apiService.getUser(userId)
                _state.value = UserState.Success(user)
            } catch (e: Exception) {
                _state.value = UserState.Error(e.message ?: "Unknown error")
            }
        }
    }

    @Deprecated("Use fetchUser instead")
    fun loadUserLegacy(userId: String) {
        println("Loading user legacy: $userId")
    }
}
