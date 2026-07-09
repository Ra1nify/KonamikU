# KonamikU

```
Extend NFC Capabilities
```


## Disclaimer

```
/* Your warranty is now void.
 *
 * I am not responsible for bricked devices, dead SD cards,
 * thermonuclear war, or you getting fired because the alarm app failed. Please
 * do some research if you have any concerns about features included in this ROM
 * before flashing it! YOU are choosing to make these modifications, and if
 * you point the finger at me for messing up your device, I will laugh at you.
 */
```

## Features

- **FeliCa card emulation** (NFC-F / HCE-F) — read, save, and emulate
- **ID conversion** (TestOnly, For Private Server) — KONAMI KID ↔ FeliCa IDm,
  Amusement IC access code ↔ IDm
- **Three emulation modes**
  - **Native** — HCE off, pass-through to hardware NFC chip (no Xposed, default fallback)
  - **Normal** — HCE on, real IDm (Xposed + PMm required)
  - **Compat** — HCE on, `02FE`-prefixed compatibility IDm (Xposed + PMm required)
- **Xposed module** — bypass Android NFC system-code validation, inject PMm patcher
- **Multi-card management** with foreground notification
- **Auto-update** via GitHub Releases with configurable mirror
- **6 languages** — EN / 简体中文 / 繁體中文 / 日本語 / 한국어 / Français

### Screenshots

| Cards | Tools | 
|-------|-------|
| Manage your emulated cards | ID converter & utilities |
|![cards.png](https://raw.githubusercontent.com/C-F0x/KonamikU/refs/heads/main/misc/Cards.png)|![tools.png](https://raw.githubusercontent.com/C-F0x/KonamikU/refs/heads/main/misc/Tools.png)|
---

## Requirements

- **Android 10+** (API 29+)
- **Device with HCE-F / NFC-F support**
- **Root** (Magisk / KernelSU / APatch) — optional, enables advanced features
- **LSPosed** — optional, enables hook optimizations

---

## FAQ

> **What is HCE-F / NFC-F? 
> How can I check if my devices support it?**
>
> https://source.android.com/docs/core/connect/felica

## About Translation

Contributions welcome!


## Credits

- Inspiration from **[AICEmu](https://github.com/tqlwsl/aicemu)**, **[eAMEMu_RN](https://github.com/juchan1220/eAMEMu_RN)**
- Native hooking via **[Dobby](https://github.com/LSPosed/Dobby)**

## LICENSE
KonamikU itself is licensed under **GNU GPL v3.0** — see [LICENSE](LICENSE).

The native hooking library **Dobby** (`app/src/main/jni/Dobby/`) is a
submodule of [LSPosed/Dobby](https://github.com/LSPosed/Dobby) and is
licensed under **Apache 2.0**.  When KonamikU is built, `libpmm.so` links
Dobby statically.  The combined work is distributed under GPL-3.0; Dobby
retains its original Apache-2.0 notice — see
[Dobby/LICENSE](app/src/main/jni/Dobby/LICENSE).