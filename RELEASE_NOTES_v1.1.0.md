# LibreBuds+ 1.1.0 — Stable (FreeBuds 6i confirmed)

**LibreBuds+ is the renamed LibreBuds community fork.** The original FreeBuds 6i protocol and QuickBuds-inspired interface remain. The owner confirmed LibreBuds+ alpha operates with actual HUAWEI FreeBuds 6i earbuds.

## What's new
- FreeBuds 6i charging-case artwork optically centered inside its battery ring.
- A separate, experimental multi-brand Bluetooth engine based on PairFree's GPLv3 drivers.
- Experimental TOZO protocol A/J battery-query support (no TOZO ANC, EQ or gesture writes).
- New per-device support matrix in [docs/DEVICE_COMPATIBILITY.md](https://github.com/vnxrgb/librebuds-6i/blob/main/docs/DEVICE_COMPATIBILITY.md), distinguishing confirmed, partial, untested and generic fallback.
- Existing Huawei FreeBuds protocol, battery, ANC, gestures, equalizer and multipoint handling retained.

## Device support you can rely on
- **HUAWEI FreeBuds 6i:** owner-confirmed overall operation; individual advanced features can depend on firmware.
- **FreeBuds 5 / FreeBuds Pro 5:** listed as previously hardware-tested by the inherited README, not newly tested in this fork.
- **Other Huawei models:** existing profile-dependent implementation, not newly validated.

## Experimental — NOT fully supported or hardware-verified in this fork
SOUNDPEATS, Soundcore, Samsung Galaxy Buds, Sony, Bose, JBL, Nothing/CMF, Google Pixel Buds, QCY, 1MORE, EarFun, Cambridge Audio, OnePlus/OPPO, Sennheiser, Jabra, Skullcandy, Apple AirPods/Beats, and TOZO. Some vendor settings may work if a model/firmware exposes compatible capabilities. TOZO is specifically battery-only on supported GATT A/J revisions. Generic Bluetooth audio support is not full proprietary device control.

## Installation
Download `librebuds-plus-v1.1.0.apk` and install it as the stable LibreBuds+ update, package `io.github.librebuds`. It uses the **same signing certificate** as stable v1.0.0; signature, package and versionCode are verified in CI. The separate alpha app is `io.github.librebuds.alpha` and can remain installed for testing.

Seven model-specific, attributed PairFree GPLv3 SVG illustrations have been adapted for Android use on exact matches to Galaxy Buds3 Pro, Sony WF-1000XM5, Soundcore A30i, Nothing Ear and AirPods Pro. These visuals do not imply working vendor controls. No third-party app screenshots/launcher logos have been repurposed as product images. All original authors are credited in [NOTICE](https://github.com/vnxrgb/librebuds-6i/blob/main/NOTICE). GPLv3 applies.
