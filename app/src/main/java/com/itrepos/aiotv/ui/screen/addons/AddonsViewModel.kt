package com.itrepos.aiotv.ui.screen.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.local.AppDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AddonsViewModel @Inject constructor(store: AppDataStore) : ViewModel() {
    val addonUrls = store.addonUrls.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
}
