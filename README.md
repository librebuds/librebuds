<div align="center">

<img src=".github/assets/icon.png" width="128" height="128" alt="LibreBuds icon" />

# LibreBuds+

**An open-source Android companion for HUAWEI FreeBuds, with a FreeBuds 6i-focused fork.**<br />
A modern, QuickBuds-inspired interface for battery monitoring, noise control, sound settings, gestures and more. No root or Huawei AI Life required.

Built on [LibreBuds](https://github.com/librebuds/librebuds) · UI inspired by [QuickBuds](https://github.com/spizganed/QuickBuds) · Licensed under GPLv3.

<a href="https://github.com/vnxrgb/librebudsplus/releases"><img src="https://img.shields.io/github/v/release/vnxrgb/librebuds-6i?include_prereleases&label=fork%20release" alt="Latest fork release" /></a>
<a href="https://github.com/vnxrgb/librebudsplus/releases"><img src="https://img.shields.io/github/downloads/vnxrgb/librebuds-6i/total?label=fork%20downloads" alt="Fork downloads" /></a>
<a href="LICENSE"><img src="https://img.shields.io/github/license/librebuds/librebuds?color=blue" alt="License: GPL-3.0" /></a>
<img src="https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white" alt="Android 13+" />
<img src="https://img.shields.io/badge/platform-Android-3DDC84" alt="Platform: Android" />
<a href="https://github.com/librebuds/librebuds-windows"><img src="https://img.shields.io/badge/Windows-librebuds--windows-0078D4" alt="Windows version" /></a>

<br />

<a href="https://github.com/vnxrgb/librebudsplus/releases"><b>Download this fork</b></a> ·
<a href="CHANGELOG.md">Changelog</a> ·
<a href="#features">Features</a> ·
<a href="#screenshots">Screenshots</a> ·
<a href="#supported-models">Supported models</a> ·
<a href="#faq-and-troubleshooting">FAQ</a> ·
<a href="#building-from-source">Build</a>

</div>

<br />

> [!IMPORTANT]
> **FreeBuds 6i is confirmed working by the owner on LibreBuds+ alpha.** Other brands are experimental or partial and must not be advertised as fully functional without real hardware tests.
>
> **Stable vs alpha:** Stable uses Android package `io.github.librebuds`; alpha uses `io.github.librebuds.alpha`, allowing side-by-side testing. Download only the version you intend from [GitHub Releases](https://github.com/vnxrgb/librebudsplus/releases). This project preserves the original Huawei controller; experimental other-brand drivers remain capability-gated.
>
> **Full compatibility report:** [Confirmed / previously tested / partially implemented / needs testing / generic Bluetooth fallback](docs/DEVICE_COMPATIBILITY.md). The [implementation notes](docs/MULTIBRAND_COMPATIBILITY.md) explain the upstream sources and limitations.

## Screenshots

The screenshots below show this fork running with **FreeBuds 6i** in its QuickBuds-inspired dark theme. The UI also supports AMOLED black and customizable accent colors.

<p align="center">
  <img src=".github/assets/screenshots/fork-2026/home.webp" width="190" alt="Device list" />
  <img src=".github/assets/screenshots/fork-2026/device-dashboard.webp" width="190" alt="Battery and ANC dashboard" />
  <img src=".github/assets/screenshots/fork-2026/audio-controls.webp" width="190" alt="Audio controls" />
  <img src=".github/assets/screenshots/fork-2026/custom-equalizer.webp" width="190" alt="Custom 10-band EQ" />
</p>
<p align="center">
  <img src=".github/assets/screenshots/fork-2026/preset-picker.webp" width="190" alt="EQ presets" />
  <img src=".github/assets/screenshots/fork-2026/gesture-controls.webp" width="190" alt="Gestures" />
  <img src=".github/assets/screenshots/fork-2026/find-and-multipoint.webp" width="190" alt="Find Earbuds and multipoint" />
  <img src=".github/assets/screenshots/fork-2026/appearance-settings.webp" width="190" alt="Appearance settings" />
</p>

<details>
<summary>Screenshot guide</summary>

| Screen | What it shows |
| --- | --- |
| Device list | Your paired FreeBuds 6i and quick battery summary |
| Home | Left / case / right battery rings, ANC / Awareness / Off, and rounded quick settings |
| Controls → Audio | Awareness mode, EQ presets, sound quality and low-latency controls |
| Custom equalizer | Animated EQ curve above the 10-band editor |
| EQ preset picker | Built-in EQ profiles with the selected setting highlighted |
| Controls → Gestures | Left/right tap and long-press assignments |
| Controls → More | Wear detection, Find Earbuds and multipoint access |
| Settings → Appearance | AMOLED black and Mint / Ocean blue / Violet / Coral accents |

</details>

> [!NOTE]
> This is a community fork built on LibreBuds and inspired by QuickBuds' design language. Some model-specific capabilities are still experimental. A successful APK build or a screenshot does **not** mean every feature has been verified with FreeBuds 6i hardware. Please include a diagnostics export when reporting an issue.

## Multi-brand Bluetooth control — experimental preview

This fork keeps **all existing LibreBuds FreeBuds features and the exact same QuickBuds-inspired Compose interface**. Non-Huawei devices use a **separate Bluetooth backend** built from the official [PairFree](https://github.com/Omega-Destroyer/PairFree) source, pinned as a GPLv3 Git submodule (`third_party/PairFree`). The app's visual components are not copied from PairFree.

**Broad protocol coverage:** PairFree's upstream core includes brand drivers for SOUNDPEATS, Soundcore, Samsung, Sony, Bose, JBL, Apple/Beats, Nothing/CMF, Google Pixel Buds, QCY, 1MORE, EarFun, Cambridge Audio, OnePlus/OPPO, Sennheiser, Jabra, and Skullcandy. The fork uses PairFree's real model identification, RFCOMM/BLE/GATT transports and capability-aware setting handlers rather than just showing a name in a list.

- **Home:** Paired Bluetooth audio devices, Android-reported connection/battery data, plus separate left/right/case battery levels whenever the vendor backend reports them.
- **Noise control:** The same rounded **Off / Noise cancellation / Awareness** selector, driven by the protocol's actual ANC state and commands where supported.
- **Controls:** The same **Audio / Gestures / More** navigation, Android media playback/volume, plus vendor-provided switches, choices and sliders (only after a driver has announced those capabilities).
- **Details:** Model/brand and the original product information design.
- **Safety:** No speculative commands are sent to unknown earbuds; power-off/factory-reset and other destructive actions are deliberately excluded from generic controls.
- **FreeBuds 6i:** Always routed to the original LibreBuds Bluetooth session and features, not PairFree. No existing FreeBuds controls were removed or redesigned.

**Hardware support is not universal.** PairFree's model database is much larger than its real-device test coverage, and some earbuds require permissions, firmware-specific commands or newer Android Bluetooth APIs (notably some AirPods). A listed brand is not a guarantee that every model or every feature works. The non-Huawei vendor support remains experimental even when included in a stable app build.

### Building with the PairFree submodule

Use `git clone --recurse-submodules https://github.com/vnxrgb/librebuds-6i.git` and build the multi-brand feature branch. For an existing checkout, run `git submodule update --init --recursive` before running `./gradlew :app:assembleDebug` in `android/`. The upstream PairFree modules are pinned to commit `af534dca43627a9c76903fbb9a118ea4a783c856`; changing that commit must be audited and tested.


See **[cross-brand feature matrix and testing limitations](docs/MULTIBRAND_COMPATIBILITY.md)** for a per-family summary of what is working, experimental, read-only or not implemented. The app also probes **TOZO A/J GATT services** for battery-only reads based on MIT-licensed BluntyBuds protocol research; ANC, EQ and gestures are intentionally disabled until tested on real TOZO hardware. Sources reviewed: [BluntyBuds](https://github.com/arkivx0/BluntyBuds), [earshot](https://github.com/HellzYeahh/earshot) and [DevPods](https://github.com/ORION2809/DevPods). PairFree includes GPLv3 stylized model-specific SVG artwork. Seven exact-model earbud/case SVGs have been adapted to Android vectors for selected models; no unrelated app screenshots/icons were reused. Existing FreeBuds 6i art and controller remain unchanged.

## Features

### QuickBuds-inspired interface

- **One consistent default design.** The former selectable Glass / Apple-style interface has been retired. New and existing installations use the QuickBuds-inspired Material / Compose UI.
- **Home / Controls / Details.** A focused product dashboard instead of one endlessly scrolling settings page; Controls is further organized into **Audio, Gestures, More**.
- **Compact battery overview.** Animated, equal-sized battery rings for the left earbud, charging case and right earbud, including charging / unavailable states.
- **Rounded ANC selector.** Equal-width Off / Noise cancellation / Awareness buttons with aligned icons and labels.
- **Everyday Quick Settings.** Auto pause, Low latency, **Audio priority** (opens a real sound-quality picker), and Multipoint (opens device management), shown according to device capabilities.
- **Appearance options.** Dark/light themes, **AMOLED black** surfaces and four persistent accent presets: **Mint, Ocean blue, Violet, Coral**.
- **Modern settings.** Consistent rounded cards, option sheets, compact status rows and dedicated left/right Find Earbuds actions.

### Earbud functionality

| Feature | Details |
| --- | --- |
| **Battery and charging** | Left / right / case battery levels, charging state, persistent notification, widget, and Android Quick Settings tile. |
| **Noise control** | Off, ANC and Awareness, with supported noise cancelling strengths and awareness / voice modes. |
| **Audio priority** | Choose sound quality or connectivity preference where available, directly from Quick Settings on the Home dashboard or the Audio page. |
| **Equalizer** | Built-in presets, custom preset management, a live EQ curve and 10-band custom editor. |
| **Gestures** | Per-earbud double / triple tap, touch-and-hold and model-supported call / playback gestures. |
| **Wear detection** | Pause playback when removing an earbud, where supported. |
| **Low latency** | Toggle low-latency mode for supported earbuds. |
| **Find Earbuds** | Separate left/right Play sound and Stop controls with a loud-sound safety warning. |
| **Multipoint** | View paired hosts, manage connections and preferred device using the existing device commands. |
| **Case-open popup / connection island** | Popup or island notifications for supported devices, with configurable options. |
| **Other supported features** | Single-earbud ANC, HD calls, voice prompt language, adaptive features, ear-tip support and reminders, subject to device / firmware capabilities. |

**Important:** Controls are only displayed when the app has an applicable model capability or device report. Not all features above are available or hardware-verified on every model. The UI redesign did not replace LibreBuds' underlying Bluetooth protocol.

## Supported models

The following is a **status-by-evidence** summary, not a guarantee of manufacturer-wide compatibility. Read the [full device compatibility matrix](docs/DEVICE_COMPATIBILITY.md) and [40-model Huawei support / artwork accuracy audit](docs/HUAWEI_MODEL_MATRIX.md) for model coverage, illustration placeholders, and hardware-test requirements.

| Device / family | Status in this fork | Verified capabilities / caveats |
| --- | --- | --- |
| **HUAWEI FreeBuds 6i** | **Working — owner tested** | App and original Huawei controller verified on actual FreeBuds 6i; individual ANC, EQ, gestures, multipoint and charging-case features remain model- and firmware-dependent |
| HUAWEI FreeBuds 5, FreeBuds Pro 5 | **Previously hardware-tested (inherited)** | Listed as tested in the original fork documentation, but not retested on this new multi-brand release |
| Other Huawei FreeBuds, FreeBuds SE, FreeClip/FreeArc, FreeLace, Lipstick, W Buds and Eyewear families | **Needs testing** | Shared Huawei SPP controller with strict per-model capability gates; alpha recognizes case/spacing/branding aliases, adds basic unverified profiles for SE 3, Lite and FreeLace Lite; see full compatibility matrix |
| SOUNDPEATS, Soundcore, Samsung Galaxy Buds, Sony, Bose, JBL | **Needs testing (PairFree)** | Vendor-driver integration; battery, ANC, EQ and controls only when the actual matched device reports capability |
| Nothing / CMF, Google Pixel Buds, QCY, 1MORE, EarFun | **Needs testing (PairFree)** | Model-dependent Bluetooth vendor support; no confirmation on hardware in this fork |
| Cambridge Audio, OnePlus / OPPO, Sennheiser, Jabra, Skullcandy | **Needs testing (PairFree)** | Model-dependent vendor transport and controls; not validated in this fork |
| Apple AirPods / Beats | **Generic fallback / needs testing** | Standard Android audio and any Android-reported battery; advanced Apple controls not guaranteed |
| TOZO GATT protocols A / J | **Partial implementation / needs testing** | Battery queries only on detected compatible services; **no ANC, EQ or gestures** |
| TOZO Airoha revisions or unknown Bluetooth headsets | **Generic fallback** | Android audio and any system-provided battery; no proprietary controls |

**Device imagery:** Original FreeBuds art is retained. Credited PairFree GPLv3 SVG vectors are included for exact matches to Galaxy Buds3 Pro, Sony WF-1000XM5, Soundcore A30i, Nothing Ear and AirPods Pro. Remaining devices use generic artwork; unrelated app screenshots/logos are not substituted. See [artwork credits](NOTICE).

**Submit a test:** [Open a device compatibility issue](https://github.com/vnxrgb/librebudsplus/issues) with model, Android version, and which controls you tested. Do not post private Bluetooth identifiers.

## Download and install

1. Download an APK from [this fork’s GitHub Releases](https://github.com/vnxrgb/librebudsplus/releases), or use an APK artifact from a successful [Android debug build](https://github.com/vnxrgb/librebuds-6i/actions/workflows/build-apk.yml). Release and debug APKs may have different signing keys; Android may not allow one to update the other in place.
2. Open the APK on your phone and allow installs from your browser or file manager when asked.
3. Start LibreBuds+ and grant the permissions on the welcome screen.

Requires Android 13 or newer. LibreBuds is not on Google Play yet.

<details>
<summary><b>"Display over other apps" is greyed out or blocked</b></summary>

<br />

Android treats this permission as a *restricted setting* for apps installed from a file. Open
**App info** for LibreBuds, tap the **⋮** menu, choose **Allow restricted settings**, then grant
the permission again.

</details>

## Permissions

| Permission | Why |
| --- | --- |
| **Nearby devices** (Bluetooth connect and scan) | Talk to your paired earbuds, and notice when their case opens nearby for the popup. Declared as never used for location. |
| **Notifications** | Battery status and the background connection notice. |
| **Display over other apps** | Draw the popup card and the island over other apps. Without it, the popup shows as a notification. |
| **Companion device** (optional) | "Reliable background start" lets Android start LibreBuds when your earbuds connect, even after the system stopped the app. Android asks you to confirm. |

LibreBuds has no internet permission. It does not collect or send any data. Fonts are loaded
through Android's downloadable fonts service.

## Windows

The Windows app lives in a separate repository: [librebuds/librebuds-windows](https://github.com/librebuds/librebuds-windows),
a fork of [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds).

## FAQ and troubleshooting

<details>
<summary><b>The popup does not show when I open the case</b></summary>

<br />

- Check that **Popup when the case opens** is on in LibreBuds settings.
- Grant **Nearby devices** and **Display over other apps** (see above for the restricted setting).
- Bluetooth must be on, and the earbuds must already be paired with the phone.
- The popup appears once per opening. Close the case for a few seconds before trying again.

</details>

<details>
<summary><b>The popup shows as a notification instead of a card</b></summary>

<br />

LibreBuds falls back to a notification when it is not allowed to draw over other apps. Grant
**Display over other apps** in App info.

</details>

<details>
<summary><b>LibreBuds stops updating in the background</b></summary>

<br />

Some phones stop background apps aggressively. Open **App info → Battery** and choose
**Unrestricted**, and turn on **Reliable background start** in LibreBuds settings.

</details>

<details>
<summary><b>"Controlled by another device"</b></summary>

<br />

Another phone or computer is currently connected to the earbuds' control channel. Tap
**Take over** to control them from this phone.

</details>

<details>
<summary><b>A setting does not change or is unavailable</b></summary>

<br />

Settings are model-dependent. LibreBuds only exposes controls that the connected earbuds report as supported or that have been confirmed for the model. If a supported 6i feature does not appear or does not work, please open an issue with a diagnostics export.

</details>

## Building from source

You need JDK 17 or newer and the Android SDK (compile SDK 37). No keystore is needed for debug
builds.

```bash
git clone https://github.com/vnxrgb/librebuds-6i.git
cd librebuds-6i/android
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                      # unit tests
```

Release builds are signed only when `android/keystore.properties` (or the path in
`LIBREBUDS_KEYSTORE_PROPERTIES`) is present; otherwise `./gradlew :app:assembleRelease`
produces an unsigned APK.

## Contributing

Bug reports, test results on new models and pull requests are welcome.

- When reporting a problem, attach a diagnostics export: **Settings → Share diagnostics** or **Save diagnostics to file**. The export leaves out serial numbers, host device names and full
  Bluetooth addresses.
- Mention your earbud model, firmware version (shown on the device screen) and phone.
- Before a pull request, run `./gradlew test` in `android/` and `python3 tools/check_ui_import.py`.

## Credits and upstream acknowledgments

- **[LibreBuds](https://github.com/librebuds/librebuds)** — original open-source application and Bluetooth feature implementation on which this fork is built. Original project credits and GPLv3 licensing remain in effect.
- **[QuickBuds by spizganed](https://github.com/spizganed/QuickBuds)** (GPLv3) — design and interaction reference for compact battery indicators, segmented noise-control buttons, settings sheets and EQ visualization. The Compose UI adaptations in this fork are independently implemented; QuickBuds Bluetooth code is not included.
- [LibrePods](https://github.com/librepods-org/librepods) (GPL-3.0): LibreBuds started as a
  remake of the LibrePods Android app. Its UI components, theme, island and popup window are
  imported and modified. No LibrePods media or fonts are included.
- [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds): the base of
  [LibreBuds for Windows](https://github.com/librebuds/librebuds-windows).
- Material Symbols icons by Google (Apache License 2.0) and the Inter typeface (SIL Open Font
  License 1.1).

See [NOTICE](NOTICE) for details.

## License

LibreBuds is free software, licensed under the [GNU General Public License v3.0](LICENSE).

## Disclaimer

LibreBuds is an independent project. It is not affiliated with or endorsed by Huawei.
FreeBuds and HUAWEI are trademarks of Huawei Technologies Co., Ltd. Product names are used only
to identify compatible devices.
