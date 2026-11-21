# Changelog

All notable changes to the **LibreBuds 6i** community fork are documented here.

This fork is based on [LibreBuds](https://github.com/librebuds/librebuds), with interface inspiration from [QuickBuds by spizganed](https://github.com/spizganed/QuickBuds). See [NOTICE](NOTICE) and [LICENSE](LICENSE) for acknowledgments and license terms.

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
