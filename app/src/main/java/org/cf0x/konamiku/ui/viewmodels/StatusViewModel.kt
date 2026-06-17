package org.cf0x.konamiku.ui.viewmodels

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import org.cf0x.konamiku.system.StatusDetector
import org.cf0x.konamiku.util.NfcRestart
import org.cf0x.konamiku.xposed.NfcHookProber
import org.cf0x.konamiku.xposed.XposedActivationState
import org.cf0x.konamiku.xposed.XposedState
import org.cf0x.konamiku.data.AppDataStore

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val dataStore = AppDataStore(context)

    private val _status       = MutableStateFlow<StatusDetector.AllStatus?>(null)
    val status: StateFlow<StatusDetector.AllStatus?> = _status.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        refresh()
        observeXposedState()
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

    fun refreshRoot() {
        viewModelScope.launch {
            _status.update { it?.copy(root = StatusDetector.detectRoot()) }
        }
    }

    fun onNfcLongPress() {
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
            delay(1200)
            refreshNfc()
            val enabled = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
            _toastEvent.emit(
                if (enabled) str(R.string.toast_nfc_enable_success)
                else         str(R.string.toast_nfc_enable_fail)
            )
        }
    }

    fun onRootLongPress() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_status.value?.root?.available != true) {
                _toastEvent.emit(str(R.string.toast_root_no_permission))
                return@launch
            }

            val result = NfcRestart.restart(context)
            if (result is NfcRestart.Result.Restarted || result is NfcRestart.Result.Killed || result is NfcRestart.Result.WasDead) {
                viewModelScope.launch {
                    delay(2000)
                    reprobeHook()
                    refreshNfc()
                    // Force the QS tile to call onStartListening() and re-render
                    android.service.quicksettings.TileService.requestListeningState(
                        context,
                        ComponentName(context, org.cf0x.konamiku.system.KonamikuTileService::class.java)
                    )
                }
            }

            val msg = when (result) {
                is NfcRestart.Result.WasDead   -> str(R.string.toast_nfc_was_dead)
                is NfcRestart.Result.KillFailed -> str(R.string.toast_nfc_restart_failed) + " (pid:${result.pid})"
                is NfcRestart.Result.Killed     -> str(R.string.toast_nfc_killed) + " (pid:${result.oldPid})"
                is NfcRestart.Result.Restarted  -> str(R.string.toast_nfc_restarted) + " (pid:${result.oldPid}→${result.newPid})"
            }
            _toastEvent.emit(msg)

            delay(300)
            refreshNfc()
        }
    }

    fun onXposedLongPress() {
        viewModelScope.launch {
            _toastEvent.emit(str(R.string.toast_xposed_refreshing))
            refreshXposed()
        }
    }

    private fun reprobeHook() {
        viewModelScope.launch {
            var hooked = false
            repeat(3) {
                hooked = NfcHookProber.probe(context)
                if (hooked) return@repeat
                delay(1000)
            }
            XposedState.activationState = if (hooked)
                XposedActivationState.ACTIVE
            else
                XposedActivationState.NEEDS_RESTART
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

    private fun str(@StringRes resId: Int): String =
        getApplication<Application>().getString(resId)
}
