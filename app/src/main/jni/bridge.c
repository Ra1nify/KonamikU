#include <jni.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <dlfcn.h>
#include <android/log.h>
#include <sys/system_properties.h>
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

/*
 * Hook state, returned to Kotlin as an int.
 *
 * These are part of the module's contract with the UI, so the numeric values
 * are stable and mirrored in KonamikuModule.PmmState. "Not installed" is not
 * one state: waiting for the NCI stack resolves itself on the next tag contact,
 * while a missing symbol never will - telling the user which is which is the
 * difference between "try again" and "this device cannot".
 */
#define PMM_STATE_NOT_ATTEMPTED 0   /* library not loaded yet */
#define PMM_STATE_INSTALLED     1   /* hook live */
#define PMM_STATE_WAITING_NCI   2   /* loader has no NCI stack mapped yet */
#define PMM_STATE_NO_SYMBOL     3   /* searched, symbol absent on this ROM */
#define PMM_STATE_HOOK_FAILED   4   /* symbol found, DobbyHook refused */

static int g_hook_state = PMM_STATE_NOT_ATTEMPTED;

static const char *state_name(int state) {
    switch (state) {
        case PMM_STATE_INSTALLED:   return "INSTALLED";
        case PMM_STATE_WAITING_NCI: return "WAITING_NCI";
        case PMM_STATE_NO_SYMBOL:   return "NO_SYMBOL";
        case PMM_STATE_HOOK_FAILED: return "HOOK_FAILED";
        default:                    return "NOT_ATTEMPTED";
    }
}

#define PMM_LEN 8

/*
 * Replace the PMm inside a buffer of [len] bytes. Returns non-zero when
 * anything was rewritten.
 *
 * Only full 8-byte windows that START inside the bounds are touched, and the
 * write is skipped unless the whole destination is inside the buffer.
 */
static int patch_pmm_in_buffer(uint8_t *buf, int len) {
    int patched = 0;
    if (buf == NULL || len < PMM_LEN) {
        return patched;
    }

    for (int i = 0; i + PMM_LEN <= len; ++i) {
        /* Pattern 1: 51 08 -> set-PMm command, PMm payload follows at +2.
         * Needs 2 header bytes plus the 8-byte PMm. */
        if (buf[i] == 0x51 && i + 1 < len && buf[i + 1] == 0x08 &&
            i + 2 + PMM_LEN <= len) {
            LOGI("PMm patch [51 08] at offset %d (len=%d)", i, len);
            memcpy(buf + i + 2, TARGET_PMM, PMM_LEN);
            patched = 1;
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
            patched = 1;
            i += PMM_LEN - 1;       /* skip the region we just wrote */
        }
    }
    return patched;
}

/* Sony CXD224x (Xperia, NCI 1.0) reads the 2-byte LF_T3T_FLAGS (0x53) value
 * big-endian. AOSP writes it little-endian, so "enable identifier 1" (01 00)
 * reaches the chip as bit 8 - an identifier past LF_T3T_MAX - and is
 * rejected with STATUS_INVALID_PARAM, leaving the host IDm disabled: the
 * NFCC never answers SENSF_REQ with it. Observed on G8441: 01 00 rejected,
 * 00 01 accepted and the reader reads the IDm. Other controllers read it
 * little-endian as the spec says, so this is gated on the CXD224x config. */
static int is_cxd224x(void) {
    static int cached = -1;
    if (cached < 0) {
        char prop[PROP_VALUE_MAX] = {0};
        __system_property_get("persist.nfc_cfg.config_file_name", prop);
        cached = strstr(prop, "cxd224x") != NULL;
        LOGI("CXD224x LF_T3T_FLAGS byte swap %s", cached ? "enabled" : "disabled");
    }
    return cached;
}

/* Walks the TLVs (never a raw byte scan: 53 02 can occur inside an IDm). */
static int swap_t3t_flags(uint8_t *buf, int len) {
    int swapped = 0;
    for (int i = 0; i + 2 <= len; ) {
        uint8_t type = buf[i], vlen = buf[i + 1];
        if (i + 2 + vlen > len) break;
        if (type == 0x53 && vlen == 2) {
            uint8_t lo = buf[i + 2];
            buf[i + 2] = buf[i + 3];
            buf[i + 3] = lo;
            LOGI("LF_T3T_FLAGS swapped to %02x %02x", buf[i + 2], buf[i + 3]);
            swapped = 1;
        }
        i += 2 + vlen;
    }
    return swapped;
}

static int hooked_nfa_dm_check_set_config(uint8_t tlv_len, uint8_t *tlv, int flags) {
    /* Never write through [tlv]: callers may pass read-only data. On this
     * stack nfa_dm_set_init_nci_params() hands in a buffer in .rodata, and
     * patching it in place faulted (SEGV_ACCERR) on every NFC start. Patch a
     * copy instead; the callee copies the TLVs before returning, so a stack
     * buffer is safe. tlv_len is a uint8_t, so 255 bytes always fit. */
    uint8_t copy[256];
    uint8_t *arg = tlv;
    if (tlv != NULL && tlv_len > 0) {
        memcpy(copy, tlv, tlv_len);
        int patched = patch_pmm_in_buffer(copy, (int) tlv_len);
        if (is_cxd224x()) {
            patched |= swap_t3t_flags(copy, (int) tlv_len);
        }
        if (patched) {
            arg = copy;
        }
    }

    /* orig_ is only non-NULL when DobbyHook succeeded; guard anyway so a
     * partial hook can never jump through a null pointer. */
    if (orig_nfa_dm_check_set_config == NULL) {
        return 0;
    }
    return orig_nfa_dm_check_set_config(tlv_len, arg, flags);
}

/**
 * Attempt the hook once.
 *
 * @return a PMM_STATE_* value. Safe to call repeatedly; once installed it is a
 *         no-op, and after a temporary miss (NCI not mapped) it retries.
 */
static int try_install_hook(void) {
    if (g_hook_state == PMM_STATE_INSTALLED) {
        return g_hook_state;
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
    int any_loaded = 0;

    for (size_t i = 0; i < sizeof(kModules) / sizeof(kModules[0]); ++i) {
        /* dlopen with RTLD_NOLOAD only reports whether the library is already
         * mapped; it never loads anything. That distinguishes "not mapped yet"
         * (temporary) from "mapped but does not export the symbol" (permanent),
         * which DobbySymbolResolver alone cannot tell apart - it returns NULL
         * for both. */
        void *handle = dlopen(kModules[i], RTLD_NOLOAD | RTLD_NOW);
        if (handle != NULL) {
            any_loaded = 1;
            dlclose(handle);
        }
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
        if (target) {
            module = "(loaded objects)";
        }
    }

    if (!target) {
        /* Neither library is mapped: the NCI stack is loaded lazily, so this
         * resolves on a later call. If one of them IS mapped and still lacks
         * the symbol, the ROM diverges from stock and retrying will not help. */
        g_hook_state = any_loaded ? PMM_STATE_NO_SYMBOL : PMM_STATE_WAITING_NCI;
        LOGW("symbol %s not found (nci loaded=%d) -> %s",
             TARGET_SYMBOL, any_loaded, state_name(g_hook_state));
        return g_hook_state;
    }

    LOGI("symbol found in %s @ %p", module, target);

    int ret = DobbyHook(
        target,
        (dobby_dummy_func_t) hooked_nfa_dm_check_set_config,
        (dobby_dummy_func_t *) &orig_nfa_dm_check_set_config
    );

    if (ret == 0 && orig_nfa_dm_check_set_config != NULL) {
        g_hook_state = PMM_STATE_INSTALLED;
        LOGI("hook installed OK");
        return g_hook_state;
    }

    g_hook_state = PMM_STATE_HOOK_FAILED;
    LOGW("DobbyHook failed: ret=%d orig=%p -> %s",
         ret, (void *) orig_nfa_dm_check_set_config, state_name(g_hook_state));
    return g_hook_state;
}

/**
 * Called by the module to query (and, if needed, retry) the hook.
 *
 * Injection happens in NfcApplication.onCreate(), which can run before the
 * NCI stack is dlopen'd - the NFC process sits idle until the first reader or
 * HCE use. WAITING_NCI is therefore a temporary answer, not a failure.
 *
 * @return a PMM_STATE_* value, mirrored by KonamikuModule.PmmState.
 */
JNIEXPORT jint JNICALL
Java_org_cf0x_konamiku_xposed_KonamikuModule_nativePmmHooked(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    return try_install_hook();
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGI("pmmtool loading");
    try_install_hook();
    return JNI_VERSION_1_6;
}
