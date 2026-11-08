package com.example.dogbreedsgallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

// ======================= Главный экран =======================

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val breeds: List<BreedEntity>) : HomeUiState
    data class Empty(val query: String) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(private val repository: BreedRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isRefreshing = MutableStateFlow(true)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)

    // Одноразовые события для snackbar
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.breeds, _query, _isRefreshing, _loadError
    ) { all, query, refreshing, error ->
        val filtered = all.filter { it.name.contains(query.trim(), ignoreCase = true) }
        when {
            all.isEmpty() && refreshing -> HomeUiState.Loading
            all.isEmpty() && error != null -> HomeUiState.Error(error)
            filtered.isEmpty() -> HomeUiState.Empty(query.trim())
            else -> HomeUiState.Success(filtered)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    init { refresh() }

    fun onQueryChange(value: String) { _query.value = value }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refresh()
                .onSuccess { _loadError.value = null }
                .onFailure { e ->
                    val message = if (e is IOException) {
                        "Нет подключения к интернету. Показаны сохранённые данные"
                    } else {
                        "Ошибка загрузки: ${e.message}"
                    }
                    _loadError.value = message
                    _events.tryEmit(message)
                }
            _isRefreshing.value = false
        }
    }

    fun toggleFavorite(breed: BreedEntity) {
        viewModelScope.launch { repository.setFavorite(breed.id, !breed.isFavorite) }
    }

    companion object {
        val Factory = viewModelFactory { initializer { HomeViewModel(AppDependencies.repository) } }
    }
}
