# Changelog

All notable changes to the **LibreBuds+** community fork are documented here.

This fork is based on [LibreBuds](https://github.com/librebuds/librebuds), with interface inspiration from [QuickBuds by spizganed](https://github.com/spizganed/QuickBuds). See [NOTICE](NOTICE) and [LICENSE](LICENSE) for acknowledgments and license terms.

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
