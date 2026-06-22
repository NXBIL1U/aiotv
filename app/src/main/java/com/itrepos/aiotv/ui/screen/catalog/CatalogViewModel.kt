package com.itrepos.aiotv.ui.screen.catalog

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.domain.model.ContentSection
import com.itrepos.aiotv.domain.usecase.GetCatalogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogUiState(
    val isLoading: Boolean = true,
    val sections: List<ContentSection> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val getCatalog: GetCatalogUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    fun load(type: String) {
        Log.d("CatalogVM", "load() called with type=$type")
        viewModelScope.launch {
            _state.value = CatalogUiState(isLoading = true)
            try {
                val sections = getCatalog.sections(type)
                Log.d("CatalogVM", "got ${sections.size} sections for type=$type")
                _state.value = CatalogUiState(isLoading = false, sections = sections)
            } catch (e: Exception) {
                Log.e("CatalogVM", "catalog load failed for type=$type", e)
                _state.value = CatalogUiState(isLoading = false, error = e.message)
            }
        }
    }
}
