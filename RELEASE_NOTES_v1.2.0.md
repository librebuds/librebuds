# LibreBuds+ v1.2.0 — Huawei-only stable

**A focused, safer stable update.** v1.2.0 removes the experimental non-Huawei integrations from v1.1.0 and retains the same Huawei control stack and familiar QuickBuds-inspired UI.

## What works

- **HUAWEI FreeBuds 6i — owner-tested:** The owner tested alpha.89 on real earbuds and confirmed existing controls were working. The provided WhatsApp microphone sample was clearer than earlier examples, though a short recording does not prove every call condition.
- Battery percentages for left, right, and case **when the earbuds actually report them**.
- ANC, Awareness, EQ, gestures, wear detection, low latency, multipoint and other model-specific features **where the Huawei model profile and firmware support them**.
- Dedicated visual art for FreeBuds 3/4/5/6 and Pro 2/3/4/5; closely related models sometimes use explicitly approximate family artwork.
- Optional call-audio coexistence safeguards that pause the app's vendor control traffic during phone/VoIP calls. No forced WhatsApp microphone gain or firmware modifications.

## Earlier hardware evidence — not newly tested

- **FreeBuds 5 and FreeBuds Pro 5:** inherited project documented some successful hardware tests. This stable update has **not** been independently tested on those devices.

## Needs testing / incomplete

- **Other Huawei FreeBuds and Huawei audio products:** 40 audio model profiles are available, including SE, i-series, Pro, FreeClip, FreeArc, FreeLace, Eyewear and W Buds. Their controls are *not* all verified on physical hardware; names and profiles do not guarantee that proprietary settings work.
- Some models may provide only battery readings or basic detection; case battery, ANC, EQ, gestures and other vendor controls can be unsupported or vary with firmware.
- Non-TWS devices without accurate licensed art deliberately use text labels rather than a made-up earbud/case illustration. FreeBuds 6i keeps the existing Pro 5-style *approximation*, not an official 6i render.
- The unverified manual Bluetooth voice-note routing experiment has been **removed**. This release does not override WhatsApp's microphone or promise a call-quality fix.

## Removed (not supported)

All experimental **Samsung, Sony, Nothing/CMF, Apple, Soundcore, TOZO and other non-Huawei vendor drivers, PairFree submodule/model data, and third-party artwork** from v1.1.0. Android Bluetooth settings still pair and play audio for other brands, but LibreBuds+ no longer offers their manufacturer-specific controls. This is deliberate, not a regression in confirmed Huawei support.

## Install / upgrade

Signed stable Android package: `io.github.librebuds`. Download `librebuds-plus-v1.2.0.apk` from the official GitHub release. CI must verify that the candidate's certificate matches published stable v1.1.0, that its versionCode increased to 22, and that the package/version are `io.github.librebuds` / `1.2.0`. The old v1.1.0 release will remain available for historical reference.

**Do not assume all Huawei models are tested.** See [device compatibility](https://github.com/vnxrgb/librebudsplus/blob/main/docs/DEVICE_COMPATIBILITY.md), [40-model Huawei artwork and support matrix](https://github.com/vnxrgb/librebudsplus/blob/main/docs/HUAWEI_MODEL_MATRIX.md), [changelog](https://github.com/vnxrgb/librebudsplus/blob/main/CHANGELOG.md) and [credits](https://github.com/vnxrgb/librebudsplus/blob/main/NOTICE). Report hardware results to [GitHub Issues](https://github.com/vnxrgb/librebudsplus/issues).

LibreBuds+, built on LibreBuds and OpenFreebuds, is an independent GPLv3 community project, not affiliated with Huawei.
