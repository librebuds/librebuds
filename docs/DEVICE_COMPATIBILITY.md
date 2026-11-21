# LibreBuds+ device compatibility

Updated: 2026-11-21. **The feature status below applies to this fork, not to upstream apps or an entire manufacturer's catalog.** A successful Android build does not demonstrate functioning Bluetooth controls. A recognized product name also does not prove the vendor protocol works.

## Status key

- **Confirmed working:** This fork has been tested with real hardware. A general app test does not independently verify every listed control.
- **Previously hardware-tested (inherited):** Original LibreBuds README lists an upstream/fork hardware test; not revalidated with LibreBuds+ multi-brand changes.
- **Partial / read-only:** An implementation exists for some features, but the remaining proprietary controls are absent; real-device validation may still be needed.
- **Needs testing:** A real vendor implementation or existing model profile is present, but no successful physical-device report for this alpha.
- **Generic fallback:** Android Bluetooth audio and, where the phone supplies it, an overall battery level; not vendor ANC/EQ/gestures.

## Confirmed / existing Huawei hardware

| Model / family | Level | What the app offers | Limits |
| --- | --- | --- | --- |
| **HUAWEI FreeBuds 6i** | **Confirmed working in LibreBuds+ alpha** | Original Huawei protocol; separate left, right and case battery; ANC/Awareness/Off, EQ, gestures, wear detection, low latency, multipoint and other model/firmware-dependent controls | Owner confirmed the app works with actual FreeBuds 6i. Not every individual feature was separately measured in the report |
| HUAWEI FreeBuds 5 | **Previously hardware-tested (inherited)** | Original Huawei controller and model-specific art | Not independently tested in LibreBuds+ |
| HUAWEI FreeBuds Pro 5 | **Previously hardware-tested (inherited)** | Original Huawei controller and model-specific art | Not independently tested in LibreBuds+ |
| HUAWEI FreeBuds 6; Pro 2 / 3 / 4; FreeBuds 3 / 4 | **Needs testing** | Existing model profiles; profile/firmware-dependent battery and controls | No owner confirmation for this alpha |
| HUAWEI FreeBuds 3i, 4i, 4E, 5i, 7, 7i, Pro, Pro 2+, Neo, Studio | **Needs testing** | Existing profiles and basic FreeBuds support | Some models do not have ANC; controls depend on profile |
| HUAWEI FreeBuds SE, SE 2, SE 4 ANC, SE 5 MAX | **Needs testing** | Existing model/profile support | Basic support only where reported |
| HUAWEI FreeBuds Lipstick / Lipstick 2; FreeClip / FreeClip 2 / Collector's Edition; FreeArc; FreeLace / Pro / Pro 2; W Buds / W Buds 2; Eyewear / Eyewear 2 | **Needs testing** | Legacy family/profile recognition, generic artwork on several models | No model-specific LibreBuds+ hardware report |

## Huawei protocol compatibility improvements (alpha, pending real-device tests)

- All recognized Huawei FreeBuds, FreeClip, FreeArc, FreeLace, Lipstick, Studio, and Eyewear models use the **original Huawei SPP controller**, not PairFree's unrelated-brand driver.
- The model registry now recognizes complete known Bluetooth names case-insensitively, tolerates extra whitespace, an optional `HUAWEI ` prefix, and a factory-style `(A1B2)` suffix without confusing similar models (for example FreeBuds 6 vs 6i).
- Adds conservative profiles for **FreeBuds SE 3**, **FreeBuds Lite**, and **FreeLace Lite**. These request basic battery information only; no unverified ANC, EQ or gesture commands are enabled.
- Hardware identifiers still take precedence over name matching. Unknown Huawei models retain a limited generic profile where the headset exposes the Huawei control channel.
- **Important:** Sharing a Huawei SPP transport does not mean identical feature sets or guaranteed compatibility. Model-specific support beyond confirmed hardware remains experimental and must be verified using actual earbuds. Bluetooth audio pairing is always managed by Android.

## New cross-brand experimental support (PairFree)

LibreBuds+ includes the pinned [PairFree](https://github.com/Omega-Destroyer/PairFree) GPLv3 protocol engine (17 upstream brand-driver families) with Android transport, capability discovery, vendor battery data, supported ANC commands, dynamic settings panels and EQ handling **only if a compatible model/firmware actually reports these capabilities**. The app will not invent settings when unsupported. **All following families need real hardware testing in this fork.**

| Brand / family | Current level | Implemented path / limits |
| --- | --- | --- |
| SOUNDPEATS | **Needs testing** | PairFree driver/model detection; battery and vendor settings dependent on model |
| Soundcore / Anker | **Needs testing** | PairFree driver; battery, ANC, EQ/settings where supported |
| Samsung Galaxy Buds (including Buds2 Pro) | **Needs testing** | Strict Galaxy Buds model-name aliases and optional four-digit factory suffix recognized in alpha; PairFree's Samsung protocol is experimental and real-hardware controls remain unverified. The main FreeBuds-only service intentionally does not manage Galaxy Buds. |
| Sony (WF / WH) | **Needs testing** | PairFree driver; feature/firmware support not confirmed |
| Bose | **Needs testing** | PairFree driver; feature/firmware support not confirmed |
| JBL | **Needs testing** | PairFree driver; feature/firmware support not confirmed |
| Nothing Ear / CMF Buds | **Needs testing** | PairFree driver, existing Nothing-compatible experimental framing; no confirmed real-device session |
| Google Pixel Buds | **Needs testing** | PairFree driver and model matching |
| QCY | **Needs testing** | PairFree driver and model matching |
| 1MORE | **Needs testing** | PairFree driver and model matching |
| EarFun | **Needs testing** | PairFree driver and model matching |
| Cambridge Audio | **Needs testing** | PairFree driver and model matching |
| OnePlus / OPPO | **Needs testing** | PairFree driver and model matching |
| Sennheiser | **Needs testing** | PairFree driver and model matching |
| Jabra | **Needs testing** | PairFree driver and model matching |
| Skullcandy | **Needs testing** | PairFree driver and model matching |
| Apple AirPods / Beats | **Generic fallback / needs testing** | Device recognition and standard Android audio/battery; PairFree Apple identification when possible, but advanced AirPods controls cannot be promised on stock Android |

These are families rather than a promise of *all models*. The shipped PairFree model table is in `android/app/src/main/assets/pairfree-models.tsv`. Identifying a device in the list does **not** mean successful transport, a working ANC command or an individual earbud battery report. 

## Additional TOZO implementation and generic fallback

| Device | Current level | Features | Not supported |
| --- | --- | --- | --- |
| TOZO devices exposing Bluetooth GATT protocol **A** | **Partial implementation, needs physical validation** | Adapter probes the actual A characteristic pair and requests battery packets (left/right, case on supported packets) | ANC, EQ, gesture writes; unsupported chip revisions |
| TOZO devices exposing Bluetooth GATT protocol **J** | **Partial implementation, needs physical validation** | Adapter probes the J service and queries left/right batteries | ANC, EQ, gestures and case level where the J protocol does not report it |
| TOZO Airoha / display-case variants | **Generic fallback only** | Device listing, Android-reported overall battery if available | A/J vendor control if service absent |
| Any unknown paired Bluetooth headset | **Generic fallback only** | Playback, volume, connectivity, Android overall battery if published | Manufacturer-specific features |

TOZO names include A1, A3, NC9, NC9 Pro, NC3, T6, T9, T10, T12, T20, Openbuds and more, but **name recognition is not proof that this adapter is compatible**. The implementation follows BluntyBuds' published protocol observations and does not copy their UI.

## Artwork and licensing

The original FreeBuds illustrations remain. PairFree additionally supplies a GPLv3 **model-specific SVG studio illustration library**, which was confirmed in its pinned tree and credited in [NOTICE](../NOTICE). Seven of its SVGs have now been adapted into Android VectorDrawable resources for **exact model matches only**: Samsung Buds3 Pro, Sony WF-1000XM5, Soundcore A30i (bud and case), Nothing Ear, and AirPods Pro (bud and case). Nonmatched models still use the generic fallback. The vectors are stylized drawings, not official manufacturer photographs. No screenshots, launcher icons, or other apps' logos were imported. We also reviewed [BluntyBuds](https://github.com/arkivx0/BluntyBuds), [earshot](https://github.com/HellzYeahh/earshot), and [DevPods](https://github.com/ORION2809/DevPods); their screenshots and icons were not repurposed as device art. Product art presence **does not** prove protocol functionality. The third-party SVGs remain credited under PairFree's GPLv3.

## How to submit a hardware test

Open an [issue](https://github.com/vnxrgb/librebuds-6i/issues) and include: precise model, firmware, phone model / Android version, whether left/right/case battery values update, ANC Off / On / Transparency results, EQ, gestures, disconnect/reconnect behavior, and an anonymized diagnostics export when appropriate. Never include Bluetooth addresses, serial numbers or private account data in public logs.

References and legal credits: [NOTICE](../NOTICE), [multibrand implementation notes](MULTIBRAND_COMPATIBILITY.md). Testing a Bluetooth function on one model does not verify it for every firmware revision.
