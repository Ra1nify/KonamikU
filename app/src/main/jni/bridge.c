#include <jni.h>
#include <stddef.h>
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

/* Mangled symbol resolved from libnfc-nci.so. Confirmed present on stock
 * Android 17 (RMX8899) at a fixed offset with size 1684. */
#define TARGET_SYMBOL "_Z23nfa_dm_check_set_confighPhb"

/*
 * Real signature (verified by disassembling libnfc-nci.so):
 *
 *   int nfa_dm_check_set_config(uint8_t tlv_len, uint8_t *tlv, int flags)
 *
 *  - tlv_len : TOTAL length of the TLV buffer, not the payload length.
 *              The prologue does `and w21, w0, #0xff` then walks the buffer
 *              with `w8 = tlv_len - 2`, comparing against each TLV's own
 *              length byte. It is uint8_t, so <= 255.
 *  - tlv     : the TLV buffer itself.
 *  - flags   : NOT a length. The body only ever tests bit 0 (`tbz w8, #0x0`)
 *              and the low byte (`tst w1, #0xff`), i.e. option bits.
 *
 * The original hook ignored tlv_len entirely and scanned a fixed 0x20 bytes,
 * so a short TLV (e.g. 4 bytes) caused an out-of-bounds read of up to 28
 * bytes and an out-of-bounds 8-byte memcpy write into com.android.nfc's heap.
 */
typedef int (*nfa_dm_check_set_config_t)(uint8_t tlv_len, uint8_t *tlv, int flags);
static nfa_dm_check_set_config_t orig_nfa_dm_check_set_config = NULL;

/* Set from JNI_OnLoad so Kotlin can distinguish "hook live" from "silently
 * inactive" (the old code reported success even when the symbol was missing). */
static int g_hook_installed = 0;

#define PMM_LEN 8

/*
 * Replace the PMm inside a buffer of [len] bytes.
 *
 * Only full 8-byte windows that START inside the bounds are touched, and the
 * write is skipped unless the whole destination is inside the buffer.
 */
static void patch_pmm_in_buffer(uint8_t *buf, int len) {
    if (buf == NULL || len < PMM_LEN) {
        return;
    }

    for (int i = 0; i + PMM_LEN <= len; ++i) {
        /* Pattern 1: 51 08 -> set-PMm command, PMm payload follows at +2.
         * Needs 2 header bytes plus the 8-byte PMm. */
        if (buf[i] == 0x51 && i + 1 < len && buf[i + 1] == 0x08 &&
            i + 2 + PMM_LEN <= len) {
            LOGI("PMm patch [51 08] at offset %d (len=%d)", i, len);
            memcpy(buf + i + 2, TARGET_PMM, PMM_LEN);
            i += 2 + PMM_LEN - 1;   /* skip the region we just wrote */
            continue;
        }

        /* Pattern 2: eight consecutive 0xFF bytes = wildcard PMm field. */
        if (buf[i]     == 0xFF && buf[i + 1] == 0xFF &&
            buf[i + 2] == 0xFF && buf[i + 3] == 0xFF &&
            buf[i + 4] == 0xFF && buf[i + 5] == 0xFF &&
            buf[i + 6] == 0xFF && buf[i + 7] == 0xFF) {
            LOGI("PMm patch [FF*8] at offset %d (len=%d)", i, len);
            memcpy(buf + i, TARGET_PMM, PMM_LEN);
            i += PMM_LEN - 1;       /* skip the region we just wrote */
        }
    }
}

static int hooked_nfa_dm_check_set_config(uint8_t tlv_len, uint8_t *tlv, int flags) {
    patch_pmm_in_buffer(tlv, (int) tlv_len);

    /* orig_ is only non-NULL when DobbyHook succeeded; guard anyway so a
     * partial hook can never jump through a null pointer. */
    if (orig_nfa_dm_check_set_config == NULL) {
        return 0;
    }
    return orig_nfa_dm_check_set_config(tlv_len, tlv, flags);
}

/**
 * Attempt the hook once.
 *
 * @return 1 on success, 0 when the symbol could not be found or DobbyHook
 *         failed. Safe to call repeatedly.
 */
static int try_install_hook(void) {
    if (g_hook_installed) {
        return 1;
    }

    /*
     * Which library owns the symbol depends on the platform version.
     *
     * Up to Android 13 the NCI stack lived in libnfc-nci.so. From 14 the
     * stack was folded into the JNI layer: /system/lib64/libnfc-nci.so still
     * exists on disk but is never mapped, and libnfc_nci_jni.so carries its
     * own statically linked copy of nfa_dm_check_set_config. Resolving with a
     * NULL module name only searches already-loaded objects, which is how the
     * old code silently found nothing on newer builds.
     */
    static const char *const kModules[] = {
        "libnfc_nci_jni.so",   /* Android 14+ */
        "libnfc-nci.so",       /* Android 13 and older */
    };

    void *target = NULL;
    const char *module = NULL;
    for (size_t i = 0; i < sizeof(kModules) / sizeof(kModules[0]); ++i) {
        void *candidate = DobbySymbolResolver(kModules[i], TARGET_SYMBOL);
        if (candidate) {
            target = candidate;
            module = kModules[i];
            break;
        }
    }
    if (!target) {
        /* Last resort: any already-loaded object that happens to export it. */
        target = DobbySymbolResolver(NULL, TARGET_SYMBOL);
        module = "(loaded objects)";
    }
    if (!target) {
        return 0;
    }

    LOGI("symbol found in %s @ %p", module, target);

    int ret = DobbyHook(
        target,
        (dobby_dummy_func_t) hooked_nfa_dm_check_set_config,
        (dobby_dummy_func_t *) &orig_nfa_dm_check_set_config
    );

    if (ret == 0 && orig_nfa_dm_check_set_config != NULL) {
        g_hook_installed = 1;
        LOGI("hook installed OK");
        return 1;
    }

    LOGW("DobbyHook failed: ret=%d orig=%p", ret, (void *) orig_nfa_dm_check_set_config);
    return 0;
}

/**
 * Called by the module once it believes libnfc-nci should be mapped.
 *
 * Injection happens in NfcApplication.onCreate(), which can run before the
 * NCI stack is dlopen'd - the NFC process sits idle until the first reader
 * or HCE use. Returning 0 then is correct but temporary, so callers may retry.
 *
 * @return 1 when the hook is live, 0 otherwise.
 */
JNIEXPORT jint JNICALL
Java_org_cf0x_konamiku_xposed_KonamikuModule_nativePmmHooked(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    if (!g_hook_installed) {
        try_install_hook();
    }
    return g_hook_installed;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGI("pmmtool loading");
    try_install_hook();
    return JNI_VERSION_1_6;
}
