# CLAUDE.md

Orientation for this repository. Distilled so sessions don't re-parse the large
research documents (`BRIDGE_RESEARCH_NOTES.md` 106 KB, `BRIDGE_AGENT_PROMPT.md`
52 KB, `COMPOSE_MIGRATION_PLAN.md` 50 KB) on every turn.

## What this repo actually is

Not a drop-in SDK. It is a research workspace around a small, undocumented
vendor BLE library, plus one large Android app you fork.

- **The vendor SDK** — `android/CyanBridge/app/libs/glasses_sdk_20250723_v01.aar`
  (387 KB, `com.oudmon.ble.*`). Closed, terse, and the only way to talk to the
  glasses. Full English API reference: **`android/docs/VENDOR_SDK_REFERENCE_EN.md`**.
- **The app** — `android/CyanBridge`, 376 Kotlin files, `com.fersaiyan.cyanbridge`,
  `versionName 2.1.0`, compileSdk 35, minSdk 24 (29 if the Meta DAT SDK is enabled),
  Kotlin 2.3.10, Compose Multiplatform 1.8.2, Room 2.7.0 via KSP.
- **Everything else** — vendor references, other device families, prototypes.

Active target is Android. iOS is a CI-validated simulator host only; the vendor
`QCSDK.framework` path has never been validated on hardware.

## Non-obvious facts that cost time to rediscover

**`MainActivity.kt` is 10,094 lines.** It holds BLE orchestration, Wi-Fi P2P,
media download, AI routing, OTA, live preview, TTS, and dashboard actions. It is
the single biggest obstacle to any customization. Second largest file is 2,510
lines, so this is one god object, not a codebase-wide pattern.

**The vendor SDK's callback slots are global singletons.** Only one workflow may
talk to the glasses at a time. `glasses/GlassesSessionCoordinator.kt` enforces
this with leases (`MEDIA_SYNC`, `LIVE_PREVIEW`, `OTA`, `WIFI_ADB_DEBUG`,
`META_CAMERA`) plus short-lived permits for one-shot commands. Any new feature
that sends a command must acquire a lease or a permit, or it will silently
clobber an in-flight transfer.

**`WifiP2pInfo.groupOwnerAddress` is the phone, not the glasses.** It is
`192.168.49.1` on most devices. Never use it as the HTTP target. Use the IP the
glasses report in notify `0x08`, bytes `[7..10]`.

**Notify error `0x09` with code `255` is noise, not failure.** The official app
sees it constantly and still completes transfers. Do not treat it as fatal.

**Bind the process to the P2P network** via `ConnectivityManager.bindProcessToNetwork()`
before HTTP, or sockets route over the wrong default network on Samsung and
other multi-network devices.

**Untracked Compose files break the build.** Gradle compiles files in the working
tree even when they are not committed to the current branch. Symptom is
`kaptDebugKotlin` failing with `NonExistentClass cannot be converted to Annotation`.
Run `git status --short` and look for `??` under `ui/components/`, `ui/glasses/`,
`ui/onboarding/`, `ui/plugins/` before blaming protocol code.

**`main` is deliberately not the Material 3 migration.** That work lives on
`compose_material3_migration` and `compose-material3-kmp-v2`. Do not merge it
into `main` piecemeal.

## Media sync in six steps

1. Connect over BLE.
2. `glassesControl(byteArrayOf(0x02, 0x01, 0x04))` — enter transfer mode.
3. Read the glasses IP from notify `0x08`, bytes `[7..10]`.
4. Join Wi-Fi Direct, bind the process to that network.
5. `GET http://<glasses-ip>/files/media.config` — plaintext, one filename per line.
6. `GET http://<glasses-ip>/files/<name>` for each; write to `MediaStore` under
   `DCIM/CyanBridge`.

Cleartext HTTP is permitted via `app/src/main/res/xml/network_security_config.xml`.

`.opus` files from the glasses are usually **raw Opus packets with no Ogg
container** (often fixed 40-byte blocks; the official app uses `packetSize=40`,
`hasHead=false`). The app wraps them into Ogg before saving. If they start with
`OggS` already, keep as-is.

## Where things live

| Path | Purpose |
| --- | --- |
| `android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/` | The app |
| `.../glasses/` | Session coordinator — read this first |
| `.../devices/` | Device classification, capability gating, profiles |
| `.../ota/` | Firmware update, live preview |
| `.../bridge/` | Other device families (MemoMind, Mentra, EvenHub) |
| `.../localagent/` | Accessibility-based phone control |
| `.../plugins/` | Walking aid, local agent settings |
| `android/CyanBridge/shared/` | KMP + Compose Multiplatform, shared with iOS |
| `heycyan-core/` | Clean BLE/audio/data modules, composite build |
| `android/HeyCyanOfficialApp/` | Decompiled vendor app — protocol ground truth |
| `android/CyanBridge/vercel_server/` | Relay: subscriptions + OpenRouter proxy |

## Documentation map

| Read this | For |
| --- | --- |
| `android/docs/VENDOR_SDK_REFERENCE_EN.md` | Vendor API, opcodes, notify events |
| `android/AGENTS.md` | Protocol truth, transfer flow, OTA endpoints, MITM workflow |
| `WIFI_TRANSFER_ARCHITECTURE.md` | Background on the transfer design |
| `BRIDGE_RESEARCH_NOTES.md` | MemoMind/XGIMI only — a different device family |
| `android/CyanBridge/docs/` | Per-feature plans (local models, memory vault, walking aid, OTA sources) |

## Build

Java 17+ required (AGP 8.12.1). Always set `JAVA_HOME`.

```bash
cd android/CyanBridge
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug
JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest
```

The emulator cannot validate pairing or media transfer. Physical device only.

### Logcat

```bash
adb logcat -s DataDownload DeviceNotify WifiP2pManagerSingleton \
  WifiP2pBroadcastReceiver BleIpBridge LDHMethods
```

`DeviceNotify` prints every decoded frame and is the fastest way to learn the
protocol on real hardware.

## Networked behaviour to be aware of

The app talks to `https://cyanbridge.vercel.app` — hardcoded as
`DEFAULT_PUBLIC_RELAY_URL` in `AiProviderPrefs.kt`. It handles subscriptions
(Asaas/Paddle) and proxies all AI chat/voice/image calls to OpenRouter. A fork
that does not want the vendor's billing or AI relay must replace this. Endpoint
list is in `android/AGENTS.md`.

## Working conventions

- Keep glue code thin. Treat the vendor AAR and the decompiled official app as
  authoritative for protocol details.
- Do not over-edit `ui/wifi/p2p/WifiP2pManagerSingleton.kt`. Prefer adding logs
  over changing connect/discovery logic.
- When something works in the official app but not here, compare **method
  sequences and payloads** first, **state machines** second.
- Never send unknown opcodes or OTA payloads to personal hardware.
- Capture logcat before changing code, and keep the log alongside the change.
