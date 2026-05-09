package org.cf0x.konamiku.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.cf0x.konamiku.system.StatusDetector
import org.cf0x.konamiku.xposed.XposedActivationState
import org.cf0x.konamiku.xposed.XposedState

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    private val _status       = MutableStateFlow<StatusDetector.AllStatus?>(null)
    val status: StateFlow<StatusDetector.AllStatus?> = _status.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
        observeXposedState()
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _status.value = StatusDetector.detectAll(context)
            _isRefreshing.value = false
        }
    }

    fun refreshNfc() {
        viewModelScope.launch {
            val nfc = StatusDetector.detectNfc(context)
            _status.update { it?.copy(nfc = nfc) }
        }
    }

    fun refreshXposed() {
        viewModelScope.launch {
            val xposed = StatusDetector.detectXposed()
            _status.update { it?.copy(xposed = xposed) }
        }
    }

    private fun observeXposedState() {
        viewModelScope.launch {
            combine(
                XposedState.activationStateFlow,
                XposedState.pmmActiveFlow,
                XposedState.frameworkNameFlow,
                XposedState.frameworkVersionFlow
            ) { state, pmm, name, version ->
                StatusDetector.XposedStatus(
                    active       = state == XposedActivationState.ACTIVE,
                    needsRestart = state == XposedActivationState.NEEDS_RESTART,
                    provider     = "$name $version".trim(),
                    pmmActive    = pmm
                )
            }.collect { xposedStatus ->
                _status.update { current ->
                    current?.copy(xposed = xposedStatus)
                        ?: StatusDetector.AllStatus(
                            nfc    = StatusDetector.detectNfc(context),
                            xposed = xposedStatus
                        )
                }
            }
        }
    }
}