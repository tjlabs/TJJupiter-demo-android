# TJJupiter-demo-android

## Overview

TJJupiter-demo-android is a minimal Android sample app for integrating **TJLabs Jupiter SDK**.

This demo app uses **TJLabs Jupiter SDK 2.0.37**.

The app demonstrates a Jupiter service lifecycle with:
- Authentication (`AUTH`)
- Service initialization (`INIT`) with a sector
- Service start (`START`) targeting a sector
- Optional mock item selection and apply (`APPLY MOCK ITEM`)
- Service stop (`STOP`)
- Result and route callback logging

The UI is intentionally simple: full-width controls and a fixed log panel.

## Features

- Indoor positioning lifecycle example
- `TJJupiterAuth` based server configuration and authentication (PROD / DEV switch)
- Real-time Jupiter result callback handling
- Mock data item selection and execution (sector-scoped)
- Runtime permission request flow
- Navigation route callback logging with route metadata
- Minimal UI for SDK integration testing

## Requirements

- Android `minSdk 26+`
- Android Studio (latest stable recommended)
- Kotlin-based Android app

### Required Permissions

Declare in `AndroidManifest.xml`:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.BLUETOOTH` (Android 11 and below)
- `android.permission.BLUETOOTH_ADMIN` (Android 11 and below)
- `android.permission.BLUETOOTH_SCAN` (Android 12+)

Runtime permission check in this demo requires:
- Location (`FINE`)
- Bluetooth scan on Android 12+

## Setup

Add JitPack repository:

```kotlin
// settings.gradle(.kts)
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Add dependency:

```kotlin
implementation("com.github.tjlabs:TJLabsJupiter-sdk-android:2.0.37")
```

Set credentials in `local.properties`:

```properties
sdk.dir=/Users/your_name/Library/Android/sdk
AUTH_ACCESS_KEY=YOUR_ACCESS_KEY
AUTH_SECRET_ACCESS_KEY=YOUR_SECRET_ACCESS_KEY
```

## Quick Guide

### 1. Create Service Manager

```kotlin
val manager = JupiterServiceManager(application, "sample_user_android")
```

The second argument is a stable user identifier (no spaces, URL-safe). In production,
use the real logged-in user's ID.

### 2. Configure Server And Authenticate

Server configuration and authentication are handled by `TJJupiterAuth`. The default
is `GCP / KOREA` with the **PROD** environment (`.jupiter.tjlabscorp.com`), applied
automatically when `setServerConfig(...)` is never called.

```kotlin
TJJupiterAuth.setServerConfig(
    ServerProvider.GCP.value,
    JupiterRegion.KOREA.value
)

TJJupiterAuth.auth(application, accessKey, accessSecretKey) { code, success ->
    // handle auth result
}
```

Input:
- `provider: String`
- `region: String`
- `accessKey: String`
- `accessSecretKey: String`

Output:
- callback `(code: Int, success: Boolean)`

#### 2.1 (Optional) Development / QA — point to DEV server

For internal testing against `.jupiter.tjlabs.dev`:

```kotlin
TJJupiterAuth.setServerConfigForDevelopment(
    context,                    // Activity or Application context
    ServerProvider.GCP.value,
    JupiterRegion.KOREA.value
)

TJJupiterAuth.auth(application, accessKey, accessSecretKey) { code, success ->
    // handle auth result
}
```

Notes:
- DEV server (`.jupiter.tjlabs.dev`) has **no SLA**.
- Calling this from a **non-debuggable (release) build** logs a warning but does not
  block the call. Release builds should always use `setServerConfig(...)`.
- The choice persists for the process lifetime. Call `setServerConfig(...)` to switch
  back to PROD without restarting.
- All downstream SDKs (Auth, Resource, Navi) inherit the env selection.

The rest of the lifecycle (`initialize`, `startService`, ...) is identical regardless
of which config call was used.

### 3. Initialize Service

`initialize(...)` uses the server config previously set through
`TJJupiterAuth.setServerConfig(...)` (or the default `GCP / KOREA`).

#### Single-sector (common case)

```kotlin
manager.initialize(
    sectorId = 20,
    callback = callback
)
```

Input:
- `sectorId: Int`
- `callback: JupiterServiceManager.JupiterServiceManagerDelegate`

Output:
- `onInitSuccess(isSuccess, errorCode)`

Sector ID note:
- `sectorId = 20` corresponds to **Songdo Convensia** (Korea).
- Sector IDs are assigned and managed by TJLabs.
- For production usage, use the sector ID provided by TJLabs.

#### Multi-sector (2.0.37 opt-in)

Load several sectors in one combined bundle:

```kotlin
manager.initialize(
    sectorIds = listOf(20, 111),
    callback = callback
)
```

The first sector in the list becomes the active sector. Subsequent `startService(...)`
calls can switch active sector by passing a different `sectorId` without re-initializing.

### 4. Start Service

**2.0.37 breaking change** — `sectorId` is now **required**. The SDK no longer falls
back to an "active sector" when the parameter is omitted; the caller must pass the
sector explicitly on every start. This prevents sector confusion in multi-sector
environments (iOS 2.0.37 parity, TJ-609).

```kotlin
manager.startService(UserMode.MODE_VEHICLE, sectorId = 20, callback)
```

Input:
- `mode: UserMode` — `MODE_PEDESTRIAN`, `MODE_VEHICLE`, or `MODE_AUTO`
- `sectorId: Int` — must be one of the sectors loaded via `initialize(...)`. If the
  sector was not loaded, the SDK fails with `JupiterErrorCode.INVALID_SECTOR`.
- `callback: JupiterServiceManager.JupiterServiceManagerDelegate`

Output:
- `onJupiterSuccess(isSuccess, code)`
- `onJupiterReport(code, msg)`
- `onJupiterResult(result)`
- optional in/out and navigation callbacks

### 5. Stop Service

```kotlin
manager.stopService { success, message ->
    // handle stop result
}
```

Output:
- callback `(success: Boolean, message: String)`

### 6. Mock Data Items

Select a `JupiterMockMode`, apply it with `setMockMode(mode, sectorId, completion)`,
then start the service with the **same** `sectorId`.

```kotlin
manager.setMockMode(JupiterMockMode.VEHICLE_INDOOR_OUTDOOR, sectorId = 20) { _ -> }
manager.startService(UserMode.MODE_VEHICLE, sectorId = 20, callback)
```

Both calls require the same `sectorId` since 2.0.37 — the mock timeline is
sector-scoped and the SDK rejects a start whose target sector differs from the mock
sector (`JupiterErrorCode.INVALID_SECTOR`).

The `completion: (Boolean) -> Unit` lambda must be passed explicitly: the published
AAR is R8-minified so Kotlin default-argument metadata is stripped, and consumers
cannot omit it.

Available mock items:
- `VEHICLE_INDOOR_OUTDOOR`
- `VEHICLE_OUTDOOR_PARKING`
- `PEDESTRIAN_INDOOR_PARKING`
- `PEDESTRIAN_PARKING_INDOOR`

Demo app flow:
- Select a mock item from the spinner.
- Tap `APPLY MOCK ITEM`.
- Tap `START`.

### 7. Replay

Replay execution is file-name based and sector-scoped as of 2.0.37:

```kotlin
manager.startReplayJupiterService(
    mode = UserMode.MODE_VEHICLE,
    sectorId = 20,
    fileName = "REPLAY_FILE_NAME",
    callback = callback
)
```

The pre-2.0.37 overload (`replayId + startServiceTime`) has been removed. Hosts that
batch-replay multiple sessions on the same sector can reuse one `initialize(...)` and
only vary the `fileName` per row (`sectorId` can stay the same when the sector
doesn't change — Jupiter SDK keeps the loaded resources).

### 8. Delegate

```kotlin
val callback = object : JupiterServiceManager.JupiterServiceManagerDelegate {
    override fun onInitSuccess(isSuccess: Boolean, errorCode: InitErrorCode?) {}

    override fun onJupiterSuccess(isSuccess: Boolean, code: JupiterErrorCode?) {}

    override fun onJupiterReport(code: JupiterServiceCode, msg: String) {}

    override fun onJupiterResult(result: JupiterResult) {}

    override fun isJupiterInOutStateChanged(state: InOutState) {}

    override fun isUserGuidanceOut() {}

    override fun isNavigationRouteChanged(
        routeId: String?,
        totalDistance: Int?,
        routes: List<JupiterNavigationRoute>
    ) {}

    override fun isNavigationRouteFailed() {}

    override fun isWaypointChanged(waypoints: List<List<Double>>) {}
}
```

### 9. Lifecycle Order

Required order:

1. Server config — pick **one**:
   - `TJJupiterAuth.setServerConfig(...)` (PROD, optional when using default `GCP / KOREA`)
   - `TJJupiterAuth.setServerConfigForDevelopment(context, ...)` (DEV, internal QA only)
2. `TJJupiterAuth.auth(...)`
3. `manager.initialize(sectorId=..., callback)` or `manager.initialize(sectorIds=..., callback)`
4. Optional: `manager.setMockMode(mode, sectorId) { _ -> }`
5. `manager.startService(mode, sectorId, callback)`
6. `manager.stopService(completion)`

If `startService(...)` is called before auth + init succeed, the SDK returns an
init / auth error through the callback.

## Error Codes

### `JupiterErrorCode`

| Name | Value | Description |
| --- | --- | --- |
| `NOT_INITIALIZED` | `0` | Service is not initialized |
| `DUPLICATED_SERVICE` | `1` | Service already running |
| `GENERATOR_FAIL` | `2` | Generator failed |
| `INVALID_ID` | `3` | Invalid user ID |
| `INVALID_MODE` | `4` | Invalid mode |
| `NETWORK_DISCONNECT` | `5` | Network disconnected |
| `LOGIN_FAIL` | `6` | Authentication failed |
| `CALC_INIT_FAIL` | `7` | Calc manager init failed |
| `BLUETOOTH_OFF` | `8` | Bluetooth off |
| `BLUETOOTH_UNAVAILABLE` | `9` | Bluetooth unavailable |
| `BLE_SCAN_STOP` | `10` | BLE scan stopped |
| `PERMISSION_DENIED` | `11` | Required permission denied |
| `SIMULATION_DATA_LOAD_FAIL` | `12` | Simulation data load failed |
| `GENERATOR_PRECHECK_FAIL` | `13` | Generator precheck failed |
| `INVALID_SECTOR` | `14` | start/mock sector was not loaded in `initialize(...)` (2.0.37+) |

## 2.0.37 Notes

### Breaking changes (iOS 2.0.37 parity, TJ-609)

- `startService(mode, callback)` → `startService(mode, sectorId, callback)`.
  `sectorId` is required; no "active sector" fallback.
- `startReplayJupiterService(mode, fileName, callback)` → `startReplayJupiterService(mode, sectorId, fileName, callback)`.
- `setMockMode(mode)` → `setMockMode(mode, sectorId) { _ -> }`. Mock timeline is
  sector-scoped. The `completion` lambda must be passed explicitly (R8 strips
  default-argument metadata from the published AAR).
- `JupiterErrorCode.INVALID_SECTOR` (`= 14`) fires when a start / mock / replay call
  references a sector that was not loaded in `initialize(...)`.
- Deprecated overloads from earlier releases (the `replayId + startServiceTime` style
  replay API, no-sector `setMockMode` / `startService`) have been removed.

### New capabilities

- **Multi-sector initialize** — `initialize(sectorIds: List<Int>, callback)` loads
  several sectors in one combined bundle and lets the host switch active sector via
  `startService(sectorId = ...)` without re-initializing.
- Resource SDK `1.1.20` is pulled transitively; geofence data (`entrance_area`,
  `entrance_matching_area`, `level_change_area`) is polygon-list format.

### Position-tracking stability fixes shipped with 2.0.37

- LSE `trace_id` is read live per request (no longer stuck on the init-time
  `tenant_user_name` across batch replays).
- `BuildingLevelChanger.checkInLevelChangeArea(...)` consumes polygon geofences and is
  fed from `onGeofenceData` — restores PathMatcher `checkAll` wider search, BLC
  level-change-area tagging, and multi-level candidate generation that were silently
  dead when the schema moved to polygons.
- DR misentry re-anchoring and `transitionExitConfirmed` gate ported from iOS.
- Session reset covers `curUvd` so a stop → start cycle does not leak the previous
  session's last UVD into the next LSE request context.

## Historical Notes

### 2.0.20
- `TJJupiterAuth.setServerConfigForDevelopment(context, provider, region)` opt-in
  DEV server switch introduced (see **2.1** above).
- PROD URL `.jupiter.tjlabscorp.com`; non-Korea regions (e.g. Saudi `me-central2`)
  routed automatically from the region prefix.
- Requires Auth SDK ≥ 1.0.28 and Resource SDK ≥ 1.1.8, both pulled transitively.

### 2.0.17
- Auth and server config centered on `TJJupiterAuth`.
- `initialize(...)` simplified to `sectorId + callback` (no `provider` / `region`).
- Mock data switched to `JupiterMockMode` item selection.
- Replay flow switched to file-name based (now `sectorId + fileName` in 2.0.37).
- LSE (single-epoch) based correction improvements for entering / searching stability.
