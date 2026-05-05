package org.cf0x.konamiku.xposed

/**
 * Xposed 激活状态，三态：
 * - INACTIVE       : 框架未激活 / 模块未启用
 * - NEEDS_RESTART  : 框架已激活，但 com.android.nfc 尚未被 hook（需要重启宿主）
 * - ACTIVE         : 框架已激活且 hook 正常工作
 */
enum class XposedActivationState { INACTIVE, NEEDS_RESTART, ACTIVE }

object XposedState {
    @Volatile var activationState: XposedActivationState = XposedActivationState.INACTIVE
    @Volatile var frameworkName: String = ""
    @Volatile var frameworkVersion: String = ""
}