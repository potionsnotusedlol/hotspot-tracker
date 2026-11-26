package ru.moscow.lineaccess.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.moscow.lineaccess.data.CatalogError
import ru.moscow.lineaccess.data.CatalogRepository
import ru.moscow.lineaccess.data.CatalogResult

data class CatalogUiState(val result: CatalogResult? = null, val canRefresh: Boolean = false)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState())
    val state = _state.asStateFlow()
    private var request: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (request?.isActive == true) return
        request = viewModelScope.launch {
            _state.value = CatalogUiState()
            val result = repository.getPointCount()
            if (result is CatalogResult.Failure && result.retryAfterSeconds > 0) {
                _state.value = CatalogUiState(result)
                delay(result.retryAfterSeconds * 1_000L)
            }
            val configured = (result as? CatalogResult.Failure)?.reason != CatalogError.NOT_CONFIGURED
            _state.value = CatalogUiState(result, canRefresh = configured)
        }
    }
}
