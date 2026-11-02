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

    /**
     * Why the PMm hook is or is not live.
     *
     * Kept separate from [pmmActive] because the reasons need different
     * answers from the user: WAITING_NCI resolves on the next tag contact,
     * NO_SYMBOL never will, and both used to render as a flat "not injected".
     */
    private val _pmmState = MutableStateFlow(KonamikuModule.PmmState.NOT_ATTEMPTED)

    val pmmStateFlow: StateFlow<KonamikuModule.PmmState> = _pmmState.asStateFlow()

    /**
     * Set once the Xposed framework itself reports our module as loaded into
     * com.android.nfc (see KonamikuApp.onServiceBind). The broadcast-based ping
     * from the NFC process is only honoured after this is true, because that
     * broadcast cannot be authenticated — any app could send it.
     */
    @Volatile
    var frameworkConfirmed: Boolean = false
        private set

    fun markFrameworkConfirmed() {
        frameworkConfirmed = true
    }

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

    /** Current PMm hook state. Setting it also updates [pmmActive]. */
    var pmmState: KonamikuModule.PmmState
        get() = _pmmState.value
        set(v) {
            _pmmState.value = v
            _pmmActive.value = v == KonamikuModule.PmmState.INSTALLED
        }

    fun reset() {
        frameworkConfirmed      = false
        _activationState.value  = XposedActivationState.INACTIVE
        _frameworkName.value    = ""
        _frameworkVersion.value = ""
        _pmmActive.value        = false
        _pmmState.value         = KonamikuModule.PmmState.NOT_ATTEMPTED
    }
}