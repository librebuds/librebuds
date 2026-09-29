# LibreBuds

LibreBuds brings earbud features to phones and PCs from other vendors: battery levels,
noise control, multipoint and per-model settings, plus a popup when you open the case.
No root, no vendor app.

Status: early development. `android/protocol` is the protocol library; `android/app` is the Android app (currently on demo data; Bluetooth arrives in the next milestone).

See [docs/devices.md](docs/devices.md) for per-model support and
[docs/protocol](docs/protocol) for the wire formats.

## Build the app

```bash
cd android
./gradlew :protocol:test       # protocol library unit tests
./gradlew :app:assembleDebug   # APK: app/build/outputs/apk/debug/app-debug.apk
```

## License

GPL-3.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE).

HUAWEI and FreeBuds are trademarks of Huawei Technologies Co., Ltd. LibreBuds is not affiliated
with or endorsed by Huawei. Product names are used only to identify compatible devices.
