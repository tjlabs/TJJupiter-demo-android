# TJJupiter-demo-android

## Overview

TJJupiter-demo-android is a minimal Android sample app for integrating **TJLabs Jupiter SDK**.

This demo app uses **TJLabs Jupiter SDK 2.0.23**.

The app demonstrates a simple Jupiter service lifecycle with:
- Authentication (`AUTH`)
- Mock item selection and apply (`APPLY MOCK ITEM`)
- Service start (`START`)
- Service stop (`STOP`)
- Result and route callback logging

The UI is intentionally simple: full-width controls and a fixed log panel.

## Features

- Indoor positioning lifecycle example
- `TJJupiterAuth` based server configuration and authentication
- Real-time Jupiter result callback handling
- Mock data item selection and execution
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
implementation("com.github.tjlabs:TJLabsJupiter-sdk-android:2.0.23")
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

### 2. Configure Server And Authenticate

In SDK 2.0.17, server configuration and authentication are handled by `TJJupiterAuth`.

Default server config is `GCP / KOREA` with the **PROD** environment (`.jupiter.tjlabscorp.com`).
This demo sets it explicitly before auth:

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

#### 2.1 (Optional) Development / QA — Point to DEV server

Since SDK 2.0.20, the SDK exposes an explicit **DEV opt-in** for internal testing against
`.jupiter.tjlabs.dev`. Use this only in debuggable builds; production apps must keep the
default PROD configuration.

```kotlin
// Replace the setServerConfig(...) call above with this one.
TJJupiterAuth.setServerConfigForDevelopment(
    context,                    // Activity or Application context
    ServerProvider.GCP.value,
    JupiterRegion.KOREA.value
)

TJJupiterAuth.auth(application, accessKey, accessSecretKey) { code, success ->
    // handle auth result
}
```

Input:
- `context: Context`  (needed to detect release-build misuse)
- `provider: String`
- `region: String`

Output:
- No return value. Server URL and auth env are internally switched to DEV.

Notes:
- DEV server (`.jupiter.tjlabs.dev`) has **no SLA** and may serve incorrect or partial data.
- Calling this from a **non-debuggable (release) build** logs a warning
  (`⚠ setServerConfigForDevelopment called by NON-DEBUGGABLE (release) app`) but does not
  block the call. Release builds should always use `setServerConfig(...)`.
- The choice persists for the process lifetime. Call `setServerConfig(...)` again to switch
  back to PROD without restarting the app.
- All downstream SDKs (Auth, Resource, Navi) receive the same env selection automatically.

The rest of the lifecycle (`initialize`, `startService`, ...) is identical regardless of
which of the two config calls you used.

### 3. Initialize Service

`initialize(...)` no longer receives `provider` or `region`.

The SDK uses the server config previously set through `TJJupiterAuth.setServerConfig(...)`, or the default `GCP / KOREA` config.

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
- `sectorId = 20` corresponds to **Songdo Convensia**.
- Sector IDs are assigned and managed by TJLabs.
- For production usage, use the sector ID provided by TJLabs.

### 4. Start Service

```kotlin
manager.startService(UserMode.MODE_VEHICLE, callback)
```

Input:
- `mode: UserMode`
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

SDK 2.0.17 uses item-based mock data.

Select a `JupiterMockMode`, apply it with `setMockMode(...)`, then start the service.

```kotlin
manager.setMockMode(JupiterMockMode.VEHICLE_INDOOR_OUTDOOR)
manager.startService(UserMode.MODE_VEHICLE, callback)
```

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

SDK 2.0.17 organizes replay execution around replay file names.

```kotlin
manager.startReplayJupiterService(
    mode = UserMode.MODE_VEHICLE,
    fileName = "REPLAY_FILE_NAME",
    callback = callback
)
```

The older `replayId + startServiceTime` overload remains available, but internally resolves to a replay file name.

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

1. Server config — pick **one** :
   - `TJJupiterAuth.setServerConfig(...)` (PROD, optional when using default `GCP / KOREA`)
   - `TJJupiterAuth.setServerConfigForDevelopment(context, ...)` (DEV, internal QA only)
2. `TJJupiterAuth.auth(...)`
3. `manager.initialize(...)`
4. Optional: `manager.setMockMode(...)`
5. `manager.startService(...)`
6. `manager.stopService(...)`

If `startService(...)` is called before auth and initialize success, SDK can return an initialization or authorization error flow.

## 2.0.17 Notes

- Auth and server config are centered on `TJJupiterAuth`.
- Default server config is `GCP / KOREA`.
- `initialize(...)` is simplified to `sectorId + callback`.
- Mock data runs through `JupiterMockMode` item selection.
- Replay flow is file-name based.
- Telemetry upload uses the updated collections presign flow inside SDK.
- Navigation route callbacks include `routeId`, `totalDistance`, and route points.
- SDK includes LSE(Single Epoch) based correction improvements for entering/searching stability.

## 2.0.20 Notes

- New : `TJJupiterAuth.setServerConfigForDevelopment(context, provider, region)` opt-in
  DEV server switch. See **2.1 (Optional) Development / QA** above.
- Default remains PROD (`.jupiter.tjlabscorp.com`); any consumer that never calls
  `setServerConfig` still lands on PROD.
- PROD URL updated from the previous placeholder to `.jupiter.tjlabscorp.com`. Non-Korea
  regions (e.g. Saudi `me-central2`) are now routed automatically from the region prefix.
- Requires Auth SDK ≥ 1.0.28 and Resource SDK ≥ 1.1.8, both pulled transitively.
