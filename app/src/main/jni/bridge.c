#include <jni.h>
#include <string.h>
#include <dlfcn.h>
#include <android/log.h>
#include "Dobby/include/dobby.h"
#include <sys/system_properties.h>

#define TAG "KonamikU_pmm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern int pmm_patch_params(uint8_t* ptr, size_t len);

static void (*orig_nfa_dm_check_set_config)(
    uint8_t num_param,
    uint8_t* p_param_tlvs,
    uint8_t* p_cur_params
) = NULL;

static void hooked_nfa_dm_check_set_config(
    uint8_t num_param,
    uint8_t* p_param_tlvs,
    uint8_t* p_cur_params
) {
    if (p_param_tlvs != NULL && num_param > 0) {
        int patched = pmm_patch_params(p_param_tlvs, (size_t)num_param);
        if (patched) {
            LOGI("PMm patch applied");
        }
    }

    if (orig_nfa_dm_check_set_config) {
        orig_nfa_dm_check_set_config(num_param, p_param_tlvs, p_cur_params);
    }
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("pmmtool loading...");

    void* handle = dlopen("libnfc-nci.so", RTLD_NOW);
    if (!handle) {
        LOGI("Failed to open libnfc-nci.so: %s", dlerror());
        return JNI_VERSION_1_6;
    }

    void* target = dlsym(handle, "nfa_dm_check_set_config");
    if (!target) {
        LOGI("Symbol not found: nfa_dm_check_set_config");
        dlclose(handle);
        return JNI_VERSION_1_6;
    }

    int ret = DobbyHook(
        target,
        (dobby_dummy_func_t)hooked_nfa_dm_check_set_config,
        (dobby_dummy_func_t *)&orig_nfa_dm_check_set_config
    );

    if (ret == 0) {
        LOGI("Hook OK");
        __system_property_set("debug.konamiku.pmmtool", "1");
    } else {
        LOGI("Hook failed: %d", ret);
    }

    dlclose(handle);
    return JNI_VERSION_1_6;
}