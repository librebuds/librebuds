# Changelog

All notable changes to the **LibreBuds+** community fork are documented here.

This fork is based on [LibreBuds](https://github.com/librebuds/librebuds), with interface inspiration from [QuickBuds by spizganed](https://github.com/spizganed/QuickBuds). See [NOTICE](NOTICE) and [LICENSE](LICENSE) for acknowledgments and license terms.

## [1.2.0] — Huawei-only stable

### Confirmed / retained
- Owner tested FreeBuds 6i on alpha.89 and confirmed functionality; Huawei controller, battery, ANC/Awareness, EQ, gestures and existing UX preserved.
- FreeBuds 5 / Pro 5 have earlier inherited hardware evidence; all other model profiles need device verification.

### Changed
- Narrowed the product to Huawei FreeBuds and related Huawei audio products, with 40 model profiles, stricter name matching and safe command gating.
- Refined model illustration mapping for close variants and replaced misleading non-TWS generic bud/case images with battery labels.
- Retained the call-audio coexistence safeguard and optional off-by-default microphone diagnostic, without claiming WhatsApp microphone repair.
- Updated the README, model matrix, compatibility docs and attribution for the Huawei-only product.

### Removed
- Experimental PairFree submodule and vendor core, Samsung, Nothing/CMF, TOZO and other non-Huawei protocols, model table, imported art and their test code. These integrations were not confirmed operational. Normal Bluetooth pairing remains Android's responsibility.
- Previous v1.1.0 stable release retained as an immutable download. Stable 1.2.0 uses a higher versionCode and the existing signing lineage.

## Historical alpha development notes

## Unreleased — Huawei support & artwork accuracy (alpha only)

- Document all 40 Huawei audio profiles by family and evidence tier in `docs/HUAWEI_MODEL_MATRIX.md`; distinguish working hardware from unverified profiles and art-only recognition.
- Map close FreeBuds 4E and Pro 2+ variants to their existing family illustrations. Use generic stem silhouettes on selected budget / in-ear models rather than unrelated round-bud images.
- Use text-only battery illustrations for FreeClip, FreeArc, FreeLace, Eyewear, W Buds and Studio, preventing misleading generic bud/case images. Preserve FreeBuds 6i artwork and controls.
- Add regression coverage for artwork aliases and generic fallback. These changes do not alter the Huawei Bluetooth packet protocol.

## Unreleased — Huawei-family protocol compatibility (alpha only)

- Prioritize Huawei's existing SPP controller for the whole FreeBuds / FreeClip / FreeArc / FreeLace / Eyewear family, avoiding accidental routing through PairFree's generic third-party driver.
- Recognize previously cataloged Huawei models across case, whitespace, omitted `HUAWEI` branding, and a factory `(ABCD)` suffix, while rejecting extra words and ambiguous model substrings.
- Add minimal, **unverified battery-only** model profiles for FreeBuds SE 3, FreeBuds Lite, and FreeLace Lite; don't guess unsupported proprietary feature controls.
- Add regression tests; no changes to the proven FreeBuds 6i packet transport or model-specific settings.
- Full compatibility for untested models and firmware is **not established** without device validation.

## Unreleased — call audio coexistence safeguard (alpha only)

### Optional voice-note route diagnostic (alpha only)
- Adds a **manual, 60-second Bluetooth voice-note test** in app Settings for recording comparisons in WhatsApp or Telegram.
- Off by default; selects a Bluetooth SCO / LE headset communication device through Android's public API on an explicit tap, and releases that route after 60 seconds, a stop tap, a call-mode change or service destruction.
- Does not record audio, request microphone capture permission, force Android into communication mode or override an active call. Android/WhatsApp can decline or override the requested route. It is a diagnostic experiment, **not an established microphone fix**.
- Design is independent, informed by public Android audio routing documentation and the general approach used by LiveClearMic. No LiveClearMic code, names, brand assets or licensed implementation is copied.


- During Android and WhatsApp calls, **pause an existing vendor RFCOMM control session as well as deferring automatic reconnect attempts**, to remove all background earbud-setting requests from the call period. Android's Bluetooth headset audio connection is not disconnected.
- Listen for Android communication-mode and Bluetooth headset audio-state changes so normal auto-connect can resume after calls. While calling, in-app earbud controls may temporarily become unavailable; the app reconnects its vendor control session after the call.
- Add regression tests for regular calls, WhatsApp-style VoIP calls, active headset audio and normal music playback.
- This is a precaution against vendor Bluetooth control traffic overlapping call setup; it does **not** change the WhatsApp microphone route, microphone gain, Bluetooth codec or the earbuds firmware. **The recorded issue is not yet proven to originate in LibreBuds+.**
- Test WhatsApp call audio on FreeBuds 6i against stable v1.1.0 with identical phone settings before promoting this build to stable.

## [1.1.0] — 2026-11-21

LibreBuds+ name and first **multi-brand preview in a stable FreeBuds-focused build**. HUAWEI FreeBuds 6i operation was confirmed by the owner using the alpha APK; **other manufacturer controls remain experimental**.

### Added
- PairFree GPLv3 protocol engine with capability-gated detection and settings across 17 upstream brand-driver families: SOUNDPEATS, Soundcore, Samsung, Sony, Bose, JBL, Apple/Beats, Nothing/CMF, Google, QCY, 1MORE, EarFun, Cambridge Audio, OnePlus/OPPO, Sennheiser, Jabra and Skullcandy.
- TOZO GATT A/J battery-only experimental adapter; no ANC, EQ, or gesture commands for TOZO.
- `docs/DEVICE_COMPATIBILITY.md`: confirmed, previously tested, partially implemented, needs-testing and Android fallback categories with per-family notes.
- Separately installable alpha APK (`io.github.librebuds.alpha`) and automated alpha prereleases.

### Changed
- App name to **LibreBuds+**, retaining the existing stable Android package identity `io.github.librebuds` for stable upgrades.
- Visually raised the charging-case image within the battery ring while keeping the charging indicator independent.
- README, compatibility disclaimers and upstream acknowledgments.
- Android stable version `1.1.0` / versionCode `21`.

### Hardware scope and artwork
- The HUAWEI FreeBuds 6i original controller and interface are preserved; one owner test confirms alpha operation.
- Other-brand protocol availability, ANC, EQ, battery and gesture features require real-device validation. Generic audio fallback is **not full device support**.
- Seven credited PairFree GPLv3 model-specific SVG illustrations were adapted into Android vectors for Galaxy Buds3 Pro, Sony WF-1000XM5, Soundcore A30i, Nothing Ear and AirPods Pro; generic and original FreeBuds artwork remains for other models. No unrelated application screenshots/icons were reused.
- No destructive vendor commands intentionally added for unknown devices.

[1.1.0]: https://github.com/vnxrgb/librebuds-6i/releases/tag/v1.1.0

## [1.0.0] — 2026-11-21

First independently signed stable release of the FreeBuds 6i-focused fork. **Important:** This version uses a new signing certificate. Users of APKs signed with the previous certificate must back up any important local settings and uninstall the old build before installing 1.0.0.

### Added
- Dedicated **FreeBuds 6i** profile with model-aware features and controls.
- **QuickBuds-inspired** home and device interface, with organized **Home / Controls / Details** pages and **Audio / Gestures / More** sub-sections.
- Compact, animated battery rings for left earbud, right earbud and case, including charging and unavailable states.
- Rounded and aligned noise-control selector for **Off, ANC, and Awareness**.
- **Quick Settings** for Auto pause, Low latency, **Audio priority**, and Multipoint, displayed when supported by the connected model.
- Sound-quality preference picker connected to the app's existing device commands.
- Animated equalizer curve, custom 10-band EQ editor and preset selection controls.
- Dedicated Find Earbuds controls for left and right earbuds, and multipoint device management.
- Accent choices (**Mint, Ocean blue, Violet, Coral**), plus AMOLED black support.
- Updated screenshots and documentation for the FreeBuds 6i fork.

### Changed
- QuickBuds-inspired appearance is now the **single default style**, including on existing installs; the old selectable Glass UI option was retired.
- Reworked cards, sheets, typography, settings groups, quick-control alignment, noise-control buttons and device navigation for a more compact consistent layout.
- Focused quick settings on everyday controls; less frequently used toggles remain in full Controls.
- README download links, supported-model descriptions, installation instructions and upstream credits now reflect this fork.
- Updated Android app version to **1.0.0** (`versionCode 20`).

### Fixed
- Inconsistent spacing and alignment in segmented navigation, ANC options, battery indicators and quick controls.
- Duplicated headings, clipped horizontal quick controls, and overly tall settings rows.
- Theme selection references left behind when the older Glass UI choice was removed.

### Notes
- Features are **capability- and firmware-dependent**. Not every option is available or fully hardware-verified for every supported model.
- Existing FreeBuds Bluetooth feature handling was retained through the UI redesign.
- This fork preserves the original LibreBuds, LibrePods, OpenFreebuds, Material Symbols, Inter and QuickBuds acknowledgments.
- The GPLv3 license and Huawei trademark disclaimer remain in effect.

[1.0.0]: https://github.com/vnxrgb/librebuds-6i/releases/tag/v1.0.0
