# LibreBuds 6i v1.0.0

The first versioned stable-release candidate of the FreeBuds 6i-focused fork.

## Highlights
- QuickBuds-inspired interface is the single default style (the previous glass design selector was retired).
- Refined Home / Controls / Details layout, including Audio / Gestures / More sections.
- Compact left / case / right battery rings, rounded and aligned ANC mode selector.
- Quick Settings for Auto pause, Low latency, Audio priority and Multipoint when available.
- Built-in equalizer presets, live 10-band custom EQ curve and preset editor.
- Find Earbuds, multipoint management, per-earbud gestures and other model-specific controls.
- AMOLED black theme and Mint / Ocean blue / Violet / Coral accents.
- Updated README, screenshots and acknowledgments to upstream LibreBuds and QuickBuds.

## Compatibility
- Android 13 or newer.
- FreeBuds 6i support, with other supported models retaining their existing model-specific controls.
- Some settings depend on the earbud firmware and capability reports; the complete feature set is not verified on every model.

## Install / update — new signing key
- Download the signed `librebuds.apk` attached to this release.
- **This fork uses a newly generated, independently controlled signing certificate starting with v1.0.0.** It is not compatible with older APKs signed with the previous key, including the old `Stable` release.
- Back up any settings you need before uninstalling the previous APK. You must uninstall the old app before installing this version if its signing certificate differs; uninstalling may erase local app data.
- Once v1.0.0 is installed, future fork releases must be signed with this same new key to support in-place upgrades.

## Attribution
Built on [LibreBuds](https://github.com/librebuds/librebuds). The interface is independently implemented using [QuickBuds](https://github.com/spizganed/QuickBuds) as a visual reference. Original GPLv3 licensing and project credits are preserved in README and NOTICE.
