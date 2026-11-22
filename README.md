# LibreBuds+ — Huawei audio companion for Android

**LibreBuds+ is an independent, open-source Android companion app for HUAWEI FreeBuds and related Huawei audio devices.** It keeps the familiar QuickBuds-inspired interface and the original Huawei RFCOMM/SPP control implementation. No root or Huawei AI Life is required.

**Huawei-only release starting with v1.2.0.** The experimental Samsung, Nothing, Sony, PairFree, TOZO and other cross-brand integrations from v1.1.0 were **removed** because they were not confirmed working. Standard Bluetooth pairing and audio playback remain controlled by Android.

## Downloads

- [Latest stable release](https://github.com/vnxrgb/librebudsplus/releases/latest) — signed package `io.github.librebuds`, designed to upgrade v1.1.0.
- [All releases and optional alpha builds](https://github.com/vnxrgb/librebudsplus/releases). The alpha package `io.github.librebuds.alpha` installs separately.
- [Huawei compatibility and artwork matrix](docs/HUAWEI_MODEL_MATRIX.md) — 40 Huawei audio profiles, artwork accuracy, evidence tiers and test limitations.

## Tested and untested devices

| Device / family | Verification | Features / limitations |
| --- | --- | --- |
| **HUAWEI FreeBuds 6i** | **Owner-tested in alpha.89** | Battery (L/R/case), ANC / Awareness, EQ, gestures and other existing supported device settings; owner confirmed existing features working and provided an improved WhatsApp voice sample. Not every firmware or microphone/call condition has been verified. |
| **FreeBuds 5 and FreeBuds Pro 5** | Previously hardware-tested in inherited project | Model-specific battery and some controls tested upstream; not independently retested in this v1.2.0 build. |
| FreeBuds 3–7, i-series, Pro models, SE models, Lite, Neo, Lipstick | **Needs hardware testing per model** | Known Huawei protocol profiles. Battery and settings are model/firmware dependent; unverified controls are not guaranteed. |
| FreeClip, FreeArc, FreeLace, Eyewear and W Buds | **Needs testing; partial / generic fallback possible** | Recognized Huawei audio families; different designs and firmware may not expose all commands. |
| Other manufacturers (Samsung, Sony, Nothing/CMF, TOZO, Apple, etc.) | **Not supported by vendor controls** | Their experimental drivers have been removed. Pair/connect audio using Android Bluetooth settings and use the manufacturer's supported app for advanced settings. |

This application **does not guarantee** all features on all 40 model profiles. Recognition isn't proof of a working private protocol; actual values are displayed only when the earbuds report them. [Detailed status and limitations](docs/DEVICE_COMPATIBILITY.md).

## Main features on compatible Huawei devices

- Left/right/case battery values where the earphones actually report them.
- Noise cancellation, Awareness and Off on models that implement the corresponding commands.
- Model-aware EQ, gestures, wear detection, low latency, multipoint, sound quality and other settings where available.
- Connection popup, compact battery presentation, Quick Settings and customizable UI.
- Conservative **call-audio coexistence protection**: temporarily pauses the app's control link during active calls; doesn't change WhatsApp microphone gain, phone call routing or firmware.
- Optional short Bluetooth microphone routing diagnostic in Settings (experimental, off by default; not an established microphone repair).

### Earbud / case images

Dedicated in-app illustrations are available for FreeBuds 3/4/5/6 and Pro 2/3/4/5. Close family variants may use approximate art (e.g. 4E uses 4, Pro 2+ uses Pro 2); FreeBuds 6i retains its existing Pro 5-style approximation. Selected in-ear models use a generic stem silhouette. FreeClip, FreeArc, FreeLace, Eyewear, W Buds and Studio have **no verified matching images** and show text labels in battery slots rather than an invented earbud or case picture. These are app illustrations, not official product photos. See the [model-by-model artwork audit](docs/HUAWEI_MODEL_MATRIX.md).

## Install / upgrade

For stable v1.2.0 install the signed `librebuds-plus-v1.2.0.apk` from its official GitHub release. The CI process should verify `io.github.librebuds`, versionCode 22 and the exact signing certificate of the published v1.1.0 APK. Do **not** install an unsigned or different-key APK over stable. The separate alpha app is for prerelease testing only.

The older stable v1.1.0 release remains available as a historical download; due to Android's version rules, returning to an older version typically requires uninstalling the newer app and may remove app settings.

## Report hardware results

[Open a compatibility issue](https://github.com/vnxrgb/librebudsplus/issues) with model name, firmware, Android version, device detection, actual L/R/case values, working/nonworking ANC, gestures, EQ, calls and reconnect behavior. Redact Bluetooth addresses and serial numbers before posting logs. A model will only be marked **confirmed** after a real-device test.

## Credits, license and independence

LibreBuds+ builds on [LibreBuds](https://github.com/librebuds/librebuds), [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds), and modified GPLv3 components of [LibrePods](https://github.com/librepods-org/librepods). Interface design was inspired by [QuickBuds](https://github.com/spizganed/QuickBuds). Copyright and original contributors' credit remain in [NOTICE](NOTICE); see [LICENSE](LICENSE). GPL-3.0-or-later applies where indicated in the source.

This is an independent community project, **not affiliated with Huawei**. FreeBuds and HUAWEI are trademarks of Huawei Technologies Co., Ltd.
