package org.cf0x.konamiku.xposed

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class XposedActivationState { INACTIVE, NEEDS_RESTART, ACTIVE }

object XposedState {
    private val _activationState  = MutableStateFlow(XposedActivationState.INACTIVE)
    private val _frameworkName    = MutableStateFlow("")
    private val _frameworkVersion = MutableStateFlow("")
    private val _pmmActive        = MutableStateFlow(false)

    val activationStateFlow:  StateFlow<XposedActivationState> = _activationState.asStateFlow()
    val frameworkNameFlow:    StateFlow<String>  = _frameworkName.asStateFlow()
    val frameworkVersionFlow: StateFlow<String>  = _frameworkVersion.asStateFlow()
    val pmmActiveFlow:        StateFlow<Boolean> = _pmmActive.asStateFlow()

    var activationState: XposedActivationState
        get() = _activationState.value
        set(v) { _activationState.value = v }

    var frameworkName: String
        get() = _frameworkName.value
        set(v) { _frameworkName.value = v }

    var frameworkVersion: String
        get() = _frameworkVersion.value
        set(v) { _frameworkVersion.value = v }

    var pmmActive: Boolean
        get() = _pmmActive.value
        set(v) { _pmmActive.value = v }

    fun reset() {
        _activationState.value  = XposedActivationState.INACTIVE
        _frameworkName.value    = ""
        _frameworkVersion.value = ""
        _pmmActive.value        = false
    }
}