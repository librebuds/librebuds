# LibreBuds

LibreBuds brings earbud features to phones and PCs from other vendors: battery levels,
noise control, multipoint and per-model settings, plus a popup when you open the case.
No root, no vendor app.

Status: test builds. The Android app connects to FreeBuds over Bluetooth for battery, noise
control, per-model settings and multipoint, and shows a popup when the case opens. Download test
builds from the [releases page](https://github.com/librebuds/librebuds/releases).

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
