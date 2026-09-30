# LibreBuds

LibreBuds brings earbud features to phones and PCs from other vendors: battery levels,
noise control, multipoint and per-model settings, plus a popup when you open the case.
No root, no vendor app.

Status: The Android app connects to FreeBuds over Bluetooth (battery, noise control, quick settings tile,
widgets) and shows a popup when the case opens nearby, with animated vector artwork per model shape.
Per-model settings are also in, shown only when the connected model's profile lists the capability:
wear detection, gestures (double tap, triple tap, touch and hold, noise-control cycle, swipe, with
in-call actions where the model has them), an equalizer preset switch, low latency, a sound-quality
priority, multipoint (host list, preferred host, connect/disconnect, no unpair) and a read-only voice
language row. A control with no `verified` date in the model's profile shows an "Experimental" label
until a live test confirms it.

See [docs/devices.md](docs/devices.md) for per-model support,
[docs/protocol](docs/protocol) for the wire formats,
[docs/testing/m3-device-checklist.md](docs/testing/m3-device-checklist.md) for the case-open popup
device checklist, and
[docs/testing/m4-device-checklist.md](docs/testing/m4-device-checklist.md) for the per-model settings
and multipoint device checklist.

Debug builds add a Settings section to test the popup without real hardware: a "Popup artwork" switch
between the vector drawing (variant A) and the generated video clip (variant B, round-shaped models
only), a "Show test popup" button, and a "Last beacon" row with the most recently parsed beacon.

Release builds always show the vector artwork. The AI-generated clips (CC BY-SA 4.0, credited in
[NOTICE](NOTICE) and [art/README.md](art/README.md)) live in `android/app/src/debug/res/raw` and are a
debug-only comparison: they are not in release APKs.

## Build the app

```bash
cd android
./gradlew :protocol:test       # protocol library unit tests
./gradlew :app:assembleDebug   # APK: app/build/outputs/apk/debug/app-debug.apk
```

If you install the APK from a file, Android may block "Display over other apps" as a restricted
setting: open App info for LibreBuds, tap the ⋮ menu, choose Allow restricted settings, then grant it again.

## License

GPL-3.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE).

HUAWEI and FreeBuds are trademarks of Huawei Technologies Co., Ltd. LibreBuds is not affiliated
with or endorsed by Huawei. Product names are used only to identify compatible devices.
