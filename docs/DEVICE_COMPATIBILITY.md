# Huawei-only device compatibility — LibreBuds+ v1.2.0

**Evidence-based status, not a promise of universal functionality.** This project uses Huawei's RFCOMM/SPP control protocol. Models and firmware can differ. Android manages Bluetooth pairing and audio; LibreBuds+ manages only compatible Huawei controls.

| Family / model | Status | What works or may work | What remains unknown |
| --- | --- | --- | --- |
| **FreeBuds 6i** | **Owner-tested on alpha.89** | Reported functioning battery, ANC/Awareness, EQ, gestures and existing settings. Owner provided a short improved WhatsApp microphone recording. | Other phone/firmware combinations and lengthy real calls not verified |
| FreeBuds 5, Pro 5 | **Earlier upstream hardware-test evidence** | Some battery and model-specific controls tested before the Huawei-only fork | Not revalidated on this release |
| FreeBuds 3, 4, 6, 7; 3i, 4i, 5i, 7i; Pro, Pro 2, Pro 2+, Pro 3, Pro 4 | **Needs testing** | Known model profiles with Huawei protocol battery/control queries where configured | Actual ANC/EQ/gesture success on these devices |
| FreeBuds SE, SE 2, SE 3, SE 4 ANC, SE 5 Max; Lite, Neo, Lipstick, Studio | **Needs testing / some battery-only profiles** | Conservative model detection and supported/read-only Huawei queries | Vendor session, firmware-specific support, advanced controls |
| FreeClip / FreeClip 2; FreeArc; FreeLace / Lite / Pro / Pro 2; Eyewear; W Buds | **Needs testing / generic fallback possible** | Name/profile recognition and best-effort Huawei transport | Actual individual battery reports, case semantics, advanced settings, unusual form factors |
| Samsung, Nothing, Sony, Bose, Apple, TOZO and other non-Huawei brands | **Not supported by LibreBuds+ vendor controls** | Standard Android Bluetooth audio works independently of LibreBuds+ | Vendor-specific controls were intentionally removed |

**Call audio:** The unverified manual voice-note routing experiment has been removed. LibreBuds+ keeps only conservative call-audio coexistence behavior and does not select WhatsApp's microphone.

**Support policy:** Never claim that an earbud is supported merely because its name is recognized or artwork exists. A value must come from an actual device reply; unverified writes must stay unavailable until supported by the model profile and hardware evidence.

**Artwork:** Dedicated illustrations exist for FreeBuds 3/4/5/6 and Pro 2/3/4/5. FreeBuds 4E, Pro 2+ and 6i use approximate family art; budget i/SE models may use generic stem silhouettes. Non-TWS Huawei devices (FreeClip, FreeArc, FreeLace, Eyewear, W Buds, Studio) use text-only battery image slots until suitable art is sourced, to avoid showing a fake case.

See [40-model Huawei inventory and artwork audit](HUAWEI_MODEL_MATRIX.md).

## Removed in v1.2.0

Experimental PairFree multi-brand protocol engine, imported non-Huawei model table, Samsung/Nothing/TOZO adapters, non-Huawei artwork and their tests/documentation. Bluetooth connectivity remains managed by Android for every headset; LibreBuds+ itself is Huawei-focused.

## How to contribute a verified test

Please use [GitHub Issues](https://github.com/vnxrgb/librebudsplus/issues) and include exact model, firmware, phone Android version, whether ANC/awareness/EQ/gestures work, actual L/R/case values (if applicable), reconnection behavior and a redacted diagnostics export. Do not publish device addresses or serial numbers. The release is not endorsed by Huawei.
