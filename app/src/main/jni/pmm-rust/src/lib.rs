const TARGET_PMM: [u8; 8] = [0x00, 0xF1, 0x00, 0x00, 0x00, 0x01, 0x43, 0x00];
const EMPTY_PMM: [u8; 8] = [0xFF; 8];
const SENSF_CMD_HEADER: [u8; 2] = [0x51, 0x08];

#[no_mangle]
pub unsafe extern "C" fn pmm_patch_params(ptr: *mut u8, len: usize) -> bool {
    if ptr.is_null() || len < 8 {
        return false;
    }

    let buf: &mut [u8] = unsafe { std::slice::from_raw_parts_mut(ptr, len) };
    let mut patched = false;

    let scan_len = if len >= 8 { len - 7 } else { 0 };
    let mut i = 0;
    while i < scan_len {
        let window = &buf[i..i + 8];

        if window[..2] == SENSF_CMD_HEADER && i + 10 <= len {
            let pmm_slice = &mut buf[i + 2..i + 10];
            if pmm_slice != TARGET_PMM {
                pmm_slice.copy_from_slice(&TARGET_PMM);
                android_log("PMm patched via SENSF_CMD_HEADER");
                patched = true;
            }
            i += 10;
            continue;
        }

        if window == EMPTY_PMM {
            buf[i..i + 8].copy_from_slice(&TARGET_PMM);
            android_log("PMm patched via EMPTY_PMM");
            patched = true;
            i += 8;
            continue;
        }

        i += 1;
    }

    patched
}

fn android_log(msg: &str) {
    extern "C" {
        fn __android_log_write(prio: i32, tag: *const u8, text: *const u8) -> i32;
    }
    let tag  = b"KonamikU_pmm\0";
    let mut text = msg.as_bytes().to_vec();
    text.push(0);
    unsafe {
        __android_log_write(4, tag.as_ptr(), text.as_ptr());
    }
}