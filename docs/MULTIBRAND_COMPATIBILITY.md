# Experimental cross-brand compatibility — LibreBuds 6i fork

Last audited: 2026-11-21. This table describes **this fork**, not the complete capabilities of upstream projects. Every feature needs an actual compatible model/firmware and Android implementation. A GitHub Actions success only proves compilation/tests, **not** hardware function.

| Device/family | Recognized in LibreBuds | Vendor transport in fork | Battery | ANC / EQ / gestures | Physical test status |
| --- | --- | --- | --- | --- | --- |
| HUAWEI FreeBuds 6i | Yes | Original LibreBuds Huawei controller, unchanged | Left/right/case reports | Original FreeBuds features | **User confirmed existing functionality works** |
| Other Huawei FreeBuds | Existing profile dependent | Original LibreBuds controller | Profile dependent | Profile dependent | Not validated by this integration |
| SOUNDPEATS, Soundcore, Samsung Galaxy Buds, Sony, Bose, JBL | PairFree model table / maker+UUID detection | Pinned PairFree driver families | Where device reports it | Where driver announces verified capabilities | **Experimental in this fork** |
| Apple AirPods / Beats | Names/family, PairFree maker detector | PairFree Apple backend if identifiable and Android permits | Android overall fallback; vendor status if received | **Not guaranteed**; many AACP features require Android stack access | User screenshot: renamed AirPods shown as generic audio, not connected |
| Nothing / CMF, Pixel Buds, QCY, 1MORE, EarFun | PairFree model table / maker+UUID | Pinned PairFree drivers | When reported | When reported and capability-checked | **Experimental in this fork** |
| Cambridge Audio, OnePlus / OPPO, Sennheiser, Jabra, Skullcandy | PairFree model table / maker+UUID | Pinned PairFree drivers | When reported | When reported and capability-checked | **Experimental in this fork** |
| TOZO (supported A/J service revisions) | TOZO family and selected BluntyBuds model names | **New isolated, read-only GATT A/J probe** | Attempts protocol A left/right/case or protocol J left/right; source-specific decoding | **Not implemented** — no ANC/EQ/gesture writes | **Unverified** on real TOZO hardware |
| TOZO Airoha revisions and cases with display | May show as TOZO | No A/J GATT control if services absent | Android overall level only if available | **Not implemented** | Not supported; see BluntyBuds limitations |
| Unrecognized Bluetooth audio | Android pairing/class | Android audio/media only | Overall Android-reported level if supplied | **Not implemented** | Depends on Android device |

## What was actually integrated

- **PairFree** (GPL-3.0): pinned compiled native driver core and its model table, in PR #20. This is a broad implementation with model-dependent capabilities but **not a real-world guarantee for every advertised model**.
- **BluntyBuds** (MIT): reviewed Android protocol A/J framing, model families and battery-report semantics, and implemented an isolated TOZO battery-only A/J GATT adapter. It discovers actual GATT characteristics before opening its read-only path. **No proprietary setting writes or firmware actions.** Attribution: Copyright (c) 2026 arkivx0.
- **earshot** (MIT): reviewed compatibility, Android constraints, experimental Bose/Soundcore/Sennheiser/Sony/Skullcandy/JBL coverage and read-before-write approach. No earshot protocol code is currently embedded; where PairFree already has a driver, adding a second uncontrolled session would be unsafe. Attribution: Copyright (c) 2026 Earshot contributors.
- **DevPods**: reviewed its provider-selection model, 10-provider mesh and practical separation between standard Android media handling and vendor protocols. It targets developer/voice events rather than a drop-in headphone settings SDK. No DevPods code copied; repository does not expose a root LICENSE file, so don't assume blanket permission.
- Additional projects reviewed: **LibrePods** (GPL-3.0) for AirPods/AACP constraints, **QuickBuds** (GPL-3.0) for OnePlus/OPPO/realme, **OpenPeats** for SoundPEATS, and **Gadgetbridge** (AGPL-3.0) for additional vendor reference. No new source from these projects was copied here.

## Artwork policy

The three supplied repositories contain **UI screenshots, launcher graphics and icons** in their tracked trees but **not a licensed library of individual earbud/case product cutouts**. An application's screenshot or launcher logo is not a manufacturer-specific earbud render. Therefore **no screenshots or logos have been substituted as device product pictures**. The original LibreBuds model art is retained; generic visual fallback applies to unsupported models. Do not silently re-license manufacturer photos or reuse screenshots as earbud art.

## Tests needed before a stable APK

1. Connect actual TOZO A/J and Airoha hardware; confirm the GATT service and validate battery packet formats. Confirm unsupported models never receive vendor writes.
2. Re-run Android compilation and catalog unit tests following the TOZO addition.
3. Re-test FreeBuds 6i connection, charge indicators, left/case/right battery, ANC, EQ, gestures and multipoint.
4. Test PairFree representative hardware per brand; report per-feature results including Android version and firmware.
5. Only label a feature **Working** after live hardware evidence; show **Experimental**, **Read-only**, or **Not available** otherwise.

## References

- https://github.com/Omega-Destroyer/PairFree (GPL-3.0)
- https://github.com/arkivx0/BluntyBuds (MIT)
- https://github.com/HellzYeahh/earshot (MIT)
- https://github.com/ORION2809/DevPods (license not confirmed at repository root)
- https://github.com/librepods-org/librepods (GPL-3.0)
- https://github.com/spizganed/QuickBuds (GPL-3.0)
- https://github.com/Wasabules/OpenPeats
- https://github.com/Freeyourgadget/Gadgetbridge (AGPL-3.0)
