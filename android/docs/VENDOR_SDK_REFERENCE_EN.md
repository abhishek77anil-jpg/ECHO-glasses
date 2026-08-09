# QC Wireless Glasses SDK — English Reference

English translation and distillation of `android/Android_SDK_Development_Guide_CN.pdf`
(青橙无线眼镜SDK使用说明).

| Field | Value |
| --- | --- |
| Vendor | Shenzhen QC.wireless Technology Co., Ltd. (深圳青橙无线科技) |
| Author | James |
| Doc version | 1.0.0 |
| Changelog | 2025/07/23 scan, connect, measurement commands · 2025/07/23 added setting commands |
| Artifact | `android/CyanBridge/app/libs/glasses_sdk_20250723_v01.aar` (387 KB) |
| Root package | `com.oudmon.ble.base.*` |

The PDF is 13 pages and is the **only authoritative source** for this SDK. It is
terse: it documents the API surface by example, with no prose on semantics,
error codes, or threading. Everything below is either a direct translation or a
cross-reference to where the behaviour is implemented in this repository.

Anything marked **[undocumented]** appears nowhere in the vendor PDF and was
reverse-engineered — see `android/AGENTS.md` for that lineage.

## 1. Requirements

- Android 5.0+ (API 21+)
- Bluetooth 4.0+
- Intended reader per the vendor: an Android engineer already familiar with BLE
  and Wi-Fi development.

### Permissions the vendor lists

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

This list is **stale for modern Android** and will not work as written on the
app's `targetSdk = 35`. The doc predates the runtime-permission split. You also
need, at minimum:

- `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (API 31+)
- `NEARBY_WIFI_DEVICES` (API 33+) for the Wi-Fi Direct media transfer
- `ACCESS_FINE_LOCATION` — still required for BLE/P2P peer discovery on many builds
- Scoped-storage `MediaStore` writes instead of `WRITE_EXTERNAL_STORAGE`

## 2. Scanning and connection

All entry points are singletons.

```kotlin
// Scan
BleScannerHelper.getInstance().scanDevice(context, mUuid: UUID?, scanCallBack: ScanWrapperCallback)
BleScannerHelper.getInstance().stopScan(context)
BleScannerHelper.getInstance().scanTheDevice(context, macAddress: String, scanResult: OnTheScanResult)

// Connect
BleOperateManager.getInstance().connectDirectly(deviceAddress)   // direct, by MAC
BleOperateManager.getInstance().connectWithScan(deviceAddress)    // scan-then-connect
BleOperateManager.getInstance().unBindDevice()                    // disconnect + unbind
BleOperateManager.getInstance().setNeedConnect(needConnect: Boolean)  // auto-reconnect
BleOperateManager.getInstance().disconnect()

// System Bluetooth state listening
BleOperateManager.getInstance().setBluetoothTurnOff(true)   // enable listening
BleOperateManager.getInstance().setBluetoothTurnOff(false)  // call when BT is turned off
```

> **Vendor warning, verbatim:** you must register the listener in your
> `Application` class *before* connecting, or you will not receive connect and
> disconnect events.

In this repo: `ui/DeviceBindActivity.kt` drives scanning; note it passes `null`
for the UUID filter, so every nearby BLE device is surfaced and classification
happens afterwards in `devices/DeviceClassifier.kt`.

### Classic Bluetooth pairing (for audio)

BLE carries control; A2DP/HFP audio needs a classic BT bond.

```kotlin
BleOperateManager.getInstance().classicBluetoothStartScan()

// In your BroadcastReceiver:
BluetoothDevice.ACTION_FOUND -> {
    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
    // Pair only when the classic BT address matches the currently connected BLE address.
    BleOperateManager.getInstance().createBondBluetoothJieLi(device)
}
```

`JieLi` (杰理) in the method name refers to the JL7018F main controller — the
same chip family named in the firmware notes in `android/AGENTS.md`.

## 3. The event listener

One listener receives every asynchronous report from the glasses.

```kotlin
LargeDataHandler.getInstance().addOutDeviceListener(100, deviceNotifyListener)
LargeDataHandler.getInstance().removeOutDeviceListener(100)

inner class MyDeviceNotifyListener : GlassesDeviceNotifyListener() {
    override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
        when (response.loadData[6].toInt()) { /* ... */ }
    }
}
```

**`loadData[6]` is the event opcode.** Bytes 0–5 are framing the vendor never
documents. Payload starts at `loadData[7]`.

The `100` is a listener-slot key, and slots are global singletons — which is
exactly why `glasses/GlassesSessionCoordinator.kt` exists, to stop media sync,
OTA, and live preview from stealing each other's callback slot.

Slot keys in use in this repo:

| Slot | Owner |
| --- | --- |
| `100` | `MainActivity` — the general-purpose listener (`MainActivity.kt:773`) |
| `2` | Media/album transfer and live preview; matches the official app's `cmdType=2` album-import listener |
| `OTA_NOTIFY_CMD_TYPE` | `ota/OtaManager.kt` |

Register in `Application`, and always pair registration with
`removeOutDeviceListener(slot)` on teardown — a leaked slot silently breaks the
next feature that needs it.

### Event opcode table (`loadData[6]`)

| Op | Meaning | Payload | Implemented in this repo |
| --- | --- | --- | --- |
| `0x02` | AI quick-recognition / photo ready | `loadData[9] == 0x02` → an intent prompt should be set, e.g. "tell me what's in front of me". Then call `getPictureThumbnails` to fetch the JPEG. | Yes — `MainActivity.kt:9917`, the main AI-image entry point |
| `0x03` | Microphone activated | `loadData[7] == 1` → glasses mic opened, user is speaking | Yes — `MainActivity.kt` voice route |
| `0x04` | OTA progress | `[7]` firmware download %, `[8]` SoC download %, `[9]` NOR flash upgrade % | Yes — routed to `ota/OtaManager.kt` |
| `0x05` | Battery report | `[7]` battery level, `[8]` charging flag | Yes — `handleBatteryReport()` |
| `0x08` | **[undocumented]** Glasses Wi-Fi IP | `[7..10]` = IPv4 address | Yes — the media-transfer trigger |
| `0x09` | **[undocumented]** P2P / Wi-Fi error | `[7]` error code; `0xFF` (255) is common and **not** fatal | Yes |
| `0x0c` | Pause event / voice broadcast | `loadData[7] == 1` | Stub |
| `0x0d` | App unbind requested | `loadData[7] == 1` | Stub |
| `0x0e` | Glasses storage low | — | Stub |
| `0x10` | Translation paused | — | Stub |
| `0x12` | Volume changed | See byte map below | Stub |

The vendor doc contains **no** `0x08` or `0x09`. Those two — the ones the entire
media-sync feature depends on — were reverse-engineered from the official app.

`0x0c`, `0x0d`, `0x0e`, `0x10`, and `0x12` are documented but only stubbed in
CyanBridge. **These are free features.** If your PRD wants low-storage warnings,
translation state, or reacting to the glasses' own volume buttons, the events
already arrive; nobody wrote the handler.

### `0x12` volume-change byte map

| Byte | Meaning |
| --- | --- |
| `[8]` `[9]` `[10]` | Music: min, max, current |
| `[12]` `[13]` `[14]` | Call: min, max, current |
| `[16]` `[17]` `[18]` | System: min, max, current |
| `[19]` | Current volume mode |

Note the gaps at `[11]` and `[15]` — one unexplained byte between each group.

## 4. Commands

Everything else goes through `glassesControl(ByteArray) { cmdType, response -> }`.

### Command table

| Command | Bytes | Notes |
| --- | --- | --- |
| Take photo | `0x02, 0x01, 0x01` | |
| Start video | `0x02, 0x01, 0x02` | |
| Stop video | `0x02, 0x01, 0x03` | |
| Start audio recording | `0x02, 0x01, 0x08` | |
| Stop audio recording | `0x02, 0x01, 0x0C` | Note: stop is `0x0C`, not `0x09` |
| AI recognition + thumbnail | `0x02, 0x01, 0x06, size, size, 0x02` | `size` is `0..6`, sent twice; see deviation below |
| Media count | `0x02, 0x04` | Response has `dataType == 4` |
| **[undocumented]** Enter transfer mode | `0x02, 0x01, 0x04` | Starts the Wi-Fi HTTP server; triggers the `0x08` IP report |
| **[undocumented]** Reset P2P | `0x02, 0x01, 0x0F` | |
| **[undocumented]** | `0x02, 0x01, 0x0B` | Used at `MainActivity.kt:3504`, purpose unlabelled |
| **[undocumented]** | `0x01, 0x02` / `0x01, 0x06` | `MainActivity.kt:5653`, `5656` |

### Response: `workTypeIng` (current device mode)

Returned when `dataType == 1 && errorCode == 0`. This is a **mode-conflict
report**, and it is the most useful thing in the whole SDK: it tells you the
glasses are busy doing something else, so your command was ignored.

| Value | Mode |
| --- | --- |
| `1`, `6` | Photo mode |
| `2` | Recording video |
| `4` | Transfer mode |
| `5` | OTA mode |
| `7` | AI conversation |
| `8` | Audio recording |

When `errorCode != 0`, the vendor comment reads "执行开始和结束" — the command ran
as a plain start/stop toggle rather than reporting a mode.

### Media count

```kotlin
LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x04)) { _, it ->
    if (it.dataType == 4) {
        val total = it.imageCount + it.videoCount + it.recordCount
    }
}
```

Counts media on the glasses not yet synced to the phone.

## 5. Other documented calls

```kotlin
// Time sync
LargeDataHandler.getInstance().syncTime { _, _ -> }

// Battery
LargeDataHandler.getInstance().addBatteryCallBack("init") { _, response -> }
LargeDataHandler.getInstance().syncBattery()
LargeDataHandler.getInstance().removeBatteryCallBack("init")

// Version info
LargeDataHandler.getInstance().syncDeviceInfo { _, response ->
    response?.wifiFirmwareVersion   // Wi-Fi firmware
    response?.wifiHardwareVersion   // Wi-Fi hardware/product
    response?.firmwareVersion       // Bluetooth firmware
    response?.hardwareVersion       // Bluetooth hardware/product
}

// Volume
LargeDataHandler.getInstance().getVolumeControl { _, response ->
    response?.minVolumeMusic;  response?.maxVolumeMusic;  response?.currVolumeMusic
    response?.minVolumeCall;   response?.maxVolumeCall;   response?.currVolumeCall
    response?.minVolumeSystem; response?.maxVolumeSystem; response?.currVolumeSystem
    response?.currVolumeType
}

// Thumbnail fetch (JPEG bytes)
LargeDataHandler.getInstance().getPictureThumbnails { cmdType, success, data ->
    // `data` is JPEG; write it to a file
}
```

The four version strings matter for OTA: `wifiHardwareVersion` and the ROM
version are the fields posted to the vendor's `last-ota` endpoint. See
`android/AGENTS.md`.

## 6. Deviations found between the doc and this codebase

Verified by reading both. Neither is asserted to be a bug — the app is reported
working — but both should be confirmed on hardware before you build on them.

**The thumbnail command drops its trailing byte.**

The vendor specifies six bytes:

```kotlin
byteArrayOf(0x02, 0x01, 0x06, thumbnailSize, thumbnailSize, 0x02)
```

Both call sites in this repo send five, omitting the final `0x02`:

- `MainActivity.kt:4231` → `byteArrayOf(0x02, 0x01, 0x06, thumbnailSize, thumbnailSize)`
- `media/autocapture/AutoLoopVisualNoteGenerator.kt:248` → `byteArrayOf(0x02, 0x01, 0x06, 0x02, 0x02)`

The second is `size = 0x02` twice with no trailing byte, not size+size+trailer.
If AI image capture is ever flaky, restoring the sixth byte is the first thing
to try.

**Undocumented opcodes outnumber documented ones in the transfer path.** The
media-sync flow — the app's headline feature — runs almost entirely on
reverse-engineered opcodes (`0x02,0x01,0x04`, notify `0x08`, notify `0x09`). The
vendor doc does not mention Wi-Fi Direct, `media.config`, or the HTTP server at
all. Treat `android/AGENTS.md` as the authority there, not this file.

## 7. What the vendor doc does not cover

Do not expect to find these in the PDF; they are unknown or documented elsewhere:

- Wi-Fi Direct / the on-glasses HTTP file server (`/files/media.config`)
- The `.opus` recording container format — see the wrapping heuristics in `android/AGENTS.md`
- Error-code enumerations for `errorCode`
- Threading and callback-thread guarantees
- What happens when two callers use the same listener slot (empirically: they clobber each other)
- OTA command sequence beyond the `0x04` progress event
- `writeIpToSoc(...)` and `startSocOtaServer(...)`, which the official app uses for pull-mode OTA

## 8. Source

Regenerate the raw text with:

```bash
python -c "
import fitz
d = fitz.open('android/Android_SDK_Development_Guide_CN.pdf')
print('\n'.join(p.get_text() for p in d))
"
```
