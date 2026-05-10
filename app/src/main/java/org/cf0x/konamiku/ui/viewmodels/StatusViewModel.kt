package org.cf0x.konamiku.ui.viewmodels

import android.app.Application
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
import org.cf0x.konamiku.xposed.XposedActivationState
import org.cf0x.konamiku.xposed.XposedState
import java.io.File

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

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

            val oldPid = getNfcServicePid()

            runCatching {
                Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "pkill -f com.android.nfc"))
                    .waitFor()
            }
            delay(800)

            val newPid = getNfcServicePid()

            when {
                oldPid == null -> {
                    _toastEvent.emit(str(R.string.toast_nfc_was_dead))
                    val ok = tryRestartNfcAndVerify()
                    _toastEvent.emit(
                        if (ok) str(R.string.toast_nfc_start_success)
                        else    str(R.string.toast_nfc_start_fail)
                    )
                }
                newPid != null && newPid == oldPid -> {
                    _toastEvent.emit(str(R.string.toast_nfc_restart_failed))
                }
                newPid == null -> {
                    _toastEvent.emit(str(R.string.toast_nfc_killed))
                    val ok = tryRestartNfcAndVerify()
                    _toastEvent.emit(
                        if (ok) str(R.string.toast_nfc_start_success)
                        else    str(R.string.toast_nfc_start_fail)
                    )
                }
                else -> {
                    _toastEvent.emit(str(R.string.toast_nfc_restarted))
                }
            }

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

    private fun getNfcServicePid(): Int? {
        val pids = File("/proc").list()
            ?.filter { it.all { c -> c.isDigit() } }
            ?: return null
        for (pid in pids) {
            val processName = runCatching {
                File("/proc/$pid/cmdline").readBytes()
                    .takeWhile { it != 0.toByte() }
                    .toByteArray()
                    .toString(Charsets.UTF_8)
            }.getOrNull() ?: continue
            if (processName == "com.android.nfc") return pid.toIntOrNull()
        }
        return null
    }

    private suspend fun tryRestartNfcAndVerify(): Boolean {
        runCatching {
            Runtime.getRuntime()
                .exec(arrayOf("su", "-c", "svc nfc enable"))
                .waitFor()
        }
        delay(2000)
        return getNfcServicePid() != null
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