package org.cf0x.konamiku.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.cf0x.konamiku.system.StatusDetector

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    private val _status = MutableStateFlow<StatusDetector.AllStatus?>(null)
    val status: StateFlow<StatusDetector.AllStatus?> = _status.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _status.value = StatusDetector.detectAll(context)
            _isRefreshing.value = false
        }
    }
}