package com.example.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserViewModel(private val apiService: ApiService) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun fetchUserData(userId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val user = apiService.getUserDetail(userId)
                _uiState.value = UiState.Success(user)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Network request failed")
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState.Loading
    }
}
