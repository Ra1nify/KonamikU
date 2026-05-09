package org.cf0x.konamiku.xposed

enum class XposedActivationState { INACTIVE, NEEDS_RESTART, ACTIVE }

object XposedState {
    @Volatile var activationState: XposedActivationState = XposedActivationState.INACTIVE
    @Volatile var frameworkName: String = ""
    @Volatile var frameworkVersion: String = ""
    @Volatile var pmmActive: Boolean = false
}