package org.cf0x.konamiku.ui.viewmodels

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.notification.LiveUpdateManager
import org.cf0x.konamiku.system.StatusDetector
import org.cf0x.konamiku.util.NfcRestart
import org.cf0x.konamiku.xposed.XposedActivationState
import org.cf0x.konamiku.xposed.XposedState

/** Describes a pending user action awaiting confirmation via dialog. */
sealed class PendingAction {
    data object NfcEnable : PendingAction()
    data object NfcRestart : PendingAction()
    data object XposedRefresh : PendingAction()
}

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val dataStore = AppDataStore(context)

    private val _status       = MutableStateFlow<StatusDetector.AllStatus?>(null)
    val status: StateFlow<StatusDetector.AllStatus?> = _status.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction.asStateFlow()

    private val _pmmEnabled = MutableStateFlow(true)
    val pmmEnabled: StateFlow<Boolean> = _pmmEnabled.asStateFlow()

    private val _showRestartDialog = MutableStateFlow(false)
    val showRestartDialog: StateFlow<Boolean> = _showRestartDialog.asStateFlow()

    init {
        refresh()
        observeXposedState()
        viewModelScope.launch {
            dataStore.pmmEnabled.collect { _pmmEnabled.value = it }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _status.value       = StatusDetector.detectAll(context)
            _isRefreshing.value = false
        }
    }

    fun refreshNfc() {
        viewModelScope.launch {
            _status.update { it?.copy(nfc = StatusDetector.detectNfc(context)) }
        }
    }

    fun refreshXposed() {
        viewModelScope.launch {
            _status.update { it?.copy(xposed = StatusDetector.detectXposed()) }
        }
    }

    // ── Long-press triggers: show confirmation dialog ──

    fun onNfcLongPress() {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        if (adapter?.isEnabled == true) {
            viewModelScope.launch { _toastEvent.emit(str(R.string.toast_nfc_already_on)) }
            refreshNfc()
            return
        }
        _pendingAction.value = PendingAction.NfcEnable
    }

    fun onRootLongPress() {
        if (_status.value?.root?.available != true) {
            viewModelScope.launch { _toastEvent.emit(str(R.string.toast_root_no_permission)) }
            return
        }
        _pendingAction.value = PendingAction.NfcRestart
    }

    fun onXposedLongPress() {
        _pendingAction.value = PendingAction.XposedRefresh
    }

    // ── Dialog callbacks ──

    fun confirmAction() {
        val action = _pendingAction.value ?: return
        _pendingAction.value = null
        deactivateActiveCard()
        when (action) {
            PendingAction.NfcEnable     -> executeNfcEnable()
            PendingAction.NfcRestart    -> executeNfcRestart()
            PendingAction.XposedRefresh -> executeXposedRefresh()
        }
    }

    /**
     * Deactivates all active emulated cards: clears the active card ID from
     * DataStore, removes the foreground notification, and clears the NFC
     * binder cache so the EmuCard service picks up the fresh state.
     */
    private fun deactivateActiveCard() {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.saveActiveCardId(null)
            LiveUpdateManager.cancel(context)
            NfcRestart.clearNfcFCache()
        }
    }

    fun cancelAction() {
        _pendingAction.value = null
    }

    /** Dismiss the restart-app dialog. */
    fun dismissRestartDialog() {
        _showRestartDialog.value = false
    }

    /**
     * Restart the entire app process: launch the main activity with a fresh
     * task stack, then kill the current process.
     */
    fun restartApp() {
        _showRestartDialog.value = false
        runCatching {
            val intent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }

    // ── Execution methods ──

    private fun executeNfcEnable() {
        viewModelScope.launch(Dispatchers.IO) {
            val adapter = NfcAdapter.getDefaultAdapter(context)
            if (adapter?.isEnabled == true) {
                _toastEvent.emit(str(R.string.toast_nfc_already_on))
                refreshNfc()
                return@launch
            }
            _toastEvent.emit(str(R.string.toast_nfc_enabling))
            runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "svc nfc enable")).waitFor()
            }
            refreshNfc()
            val enabled = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
            _toastEvent.emit(
                if (enabled) str(R.string.toast_nfc_enable_success)
                else         str(R.string.toast_nfc_enable_fail)
            )
        }
    }

    private fun executeNfcRestart() {
        viewModelScope.launch {
            NfcRestart.restart(context) { step, oldPid, newPid ->
                when (step) {
                    NfcRestart.Step.KILL_PROCESS -> {
                        if (oldPid != null) {
                            _toastEvent.emit(str(R.string.toast_nfc_pid_killed, oldPid.toString()))
                        }
                    }
                    NfcRestart.Step.ENABLE_NFC -> {
                        _toastEvent.emit(str(R.string.toast_nfc_service_refreshed))
                    }
                    NfcRestart.Step.CLEAR_CACHE -> {
                        _toastEvent.emit(str(R.string.toast_nfc_restarting))
                    }
                    NfcRestart.Step.DONE -> {
                        if (newPid != null && newPid != oldPid && oldPid != null) {
                            _toastEvent.emit(str(R.string.toast_nfc_pid_restarted, newPid.toString()))
                        }
                        _showRestartDialog.value = true
                        refreshNfc()
                        runCatching {
                            android.service.quicksettings.TileService.requestListeningState(
                                context,
                                ComponentName(context, org.cf0x.konamiku.system.KonamikuTileService::class.java)
                            )
                        }
                    }
                    else -> { /* CAPTURE_PID, DISABLE_NFC, NFC_READY, CHECK_PID — no toast needed */ }
                }
            }
        }
    }

    private fun executeXposedRefresh() {
        viewModelScope.launch {
            _toastEvent.emit(str(R.string.toast_xposed_refreshing))
            refreshXposed()
        }
    }

    // ── Xposed state observation ──

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

    private fun str(@StringRes resId: Int, vararg formatArgs: Any): String =
        getApplication<Application>().getString(resId, *formatArgs)
}
