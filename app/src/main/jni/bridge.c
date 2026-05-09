#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <android/log.h>
#include "dobby.h"

#define TAG     "KonamikU_pmm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)

/* ── Target PMm ──────────────────────────────────────────────────────────
 * 00 F1 00 00 00 01 43 00  (KONAMI reader compatible)
 */
static const uint8_t TARGET_PMM[8] = {
    0x00, 0xF1, 0x00, 0x00, 0x00, 0x01, 0x43, 0x00
};

/* Original function pointer — same signature as AICEmu */
static void *(*orig_nfa_dm_check_set_config)(uint8_t, uint8_t *, int) = NULL;

/*
 * Hook function for _Z23nfa_dm_check_set_confighPhb
 *
 * NCI TLV buffer (a2) can contain:
 *   Pattern 1: ... 51 08 [PMm 8B] ...   (set PMm command)
 *   Pattern 2: ... FF FF FF FF FF FF FF FF ...  (wildcard PMm)
 *
 * Scan the first 32 bytes for either pattern and replace PMm in-place.
 * Identical search logic to AICEmu main.cpp.
 */
static void *hooked_nfa_dm_check_set_config(uint8_t a1, uint8_t *a2, int a3) {
    if (a2 != NULL) {

        /* Pattern 1: 51 08 → set PMm command, PMm follows at +2 */
        for (int i = 0; i < 0x20; ++i) {
            if (a2[i] == 0x51 && a2[i + 1] == 0x08) {
                LOGI("PMm patch [51 08] at offset %d", i);
                memcpy(a2 + i + 2, TARGET_PMM, 8);
            }
        }

        /* Pattern 2: 8 consecutive 0xFF bytes = wildcard PMm field */
        for (int i = 0; i < 0x20; ++i) {
            if (a2[i]   == 0xFF && a2[i+1] == 0xFF && a2[i+2] == 0xFF && a2[i+3] == 0xFF
             && a2[i+4] == 0xFF && a2[i+5] == 0xFF && a2[i+6] == 0xFF && a2[i+7] == 0xFF) {
                LOGI("PMm patch [FF*8] at offset %d", i);
                memcpy(a2 + i, TARGET_PMM, 8);
            }
        }
    }

    return orig_nfa_dm_check_set_config(a1, a2, a3);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGI("pmmtool loading");

    /*
     * DobbySymbolResolver(NULL, mangled) searches the current process's
     * loaded libraries. libnfc-nci.so is already mapped by the NFC process
     * when JNI_OnLoad fires (we inject after NfcApplication.onCreate).
     */
    void *target = DobbySymbolResolver(NULL, "_Z23nfa_dm_check_set_confighPhb");
    if (!target) {
        LOGW("symbol _Z23nfa_dm_check_set_confighPhb not found — pmmtool inactive");
        return JNI_VERSION_1_6;
    }
    LOGI("symbol found @ %p", target);

    int ret = DobbyHook(
        target,
        (dobby_dummy_func_t)hooked_nfa_dm_check_set_config,
        (dobby_dummy_func_t *)&orig_nfa_dm_check_set_config
    );

    if (ret == 0) {
        LOGI("hook installed OK");
    } else {
        LOGW("DobbyHook failed: %d", ret);
    }

    return JNI_VERSION_1_6;
}