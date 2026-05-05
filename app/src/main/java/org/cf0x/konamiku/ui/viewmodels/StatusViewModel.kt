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
import org.cf0x.konamiku.util.NfcRestart

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    private val _status = MutableStateFlow<StatusDetector.AllStatus?>(null)
    val status: StateFlow<StatusDetector.AllStatus?> = _status.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
    }

    /**
     * 核心刷新逻辑：调用 Detector 并更新 Flow[cite: 7, 9]
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _status.value = StatusDetector.detectAll(context)
            _isRefreshing.value = false
        }
    }

/**
* 请求 Root 授权：支持 KernelSU/APatch 的静默检测及 Magisk 的弹窗提权[cite: 7, 9]
*/
fun requestRootPermission() {
viewModelScope.launch {
    val success = StatusDetector.requestRoot()
    if (success) refresh()
}
}

/**
* 重启 NFC 服务：执行完重启后自动刷新状态[cite: 9]
*/
fun restartNfcService() {
val currentStatus = _status.value ?: return
if (!currentStatus.isPrivileged) return

viewModelScope.launch {
    val success = NfcRestart.restartNfcService(context, currentStatus)
    if (success) {
        kotlinx.coroutines.delay(1000)
        refresh()
    }
}
}
}