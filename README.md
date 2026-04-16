# TJJupiter-demo-android

## Overview

TJJupiter-demo-android is a minimal Android sample app for integrating **TJLabs Jupiter SDK**.

This demo app uses **TJLabs Jupiter SDK 2.0.2**.

The app demonstrates a simple Jupiter service lifecycle with:
- Authentication (`AUTH`)
- Service start (`START`)
- Service stop (`STOP`)
- Mocking mode toggle (`MOCK TOGGLE`)

The UI is intentionally simple: full-width action buttons and a fixed log panel.

## Features

- Indoor positioning lifecycle example (auth/start/stop)
- Real-time Jupiter result callback handling
- Mocking mode ON/OFF toggle
- Runtime permission request flow
- Minimal UI for SDK integration testing

## Requirements

- Android `minSdk 26+`
- Android Studio (latest stable recommended)
- Kotlin-based Android app

### Required permissions

Declare in `AndroidManifest.xml`:

- `android.permission.INTERNET`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.ACCESS_COARSE_LOCATION`
- `android.permission.BLUETOOTH_SCAN` (Android 12+)
- `android.permission.BLUETOOTH_CONNECT` (Android 12+)

Runtime permission check in this demo requires:
- Location (`FINE` or `COARSE`)
- Bluetooth scan/connect on Android 12+

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
implementation("com.github.tjlabs:TJLabsJupiter-sdk-android:2.0.2")
```

## Quick Guide

### 1. Configure credentials

Set in `local.properties`:

```properties
sdk.dir=/Users/your_name/Library/Android/sdk
AUTH_ACCESS_KEY=YOUR_ACCESS_KEY
AUTH_SECRET_ACCESS_KEY=YOUR_SECRET_ACCESS_KEY
```

### 2. Create service manager

```kotlin
val manager = JupiterServiceManager(application, userId)
```

### 3. Authenticate

Input:
- `accessKey: String`
- `accessSecretKey: String`

Output:
- callback `(code: Int, success: Boolean)`

```kotlin
manager.auth(accessKey, accessSecretKey) { code, success ->
    // handle auth result
}
```

### 4. Start service

Input:
- `region: String` (example: `JupiterRegion.KOREA.value`)
- `sectorId: Int` (this demo uses `20`)
- `mode: UserMode` (this demo uses `UserMode.MODE_VEHICLE`)
- `callback: JupiterServiceManager.JupiterServiceManagerDelegate`

Output:
- `onJupiterSuccess(isSuccess, code)`
- `onJupiterReport(code, msg)`
- `onJupiterResult(result)`
- optional in/out and navigation callbacks

```kotlin
val callback = object : JupiterServiceManager.JupiterServiceManagerDelegate {
    override fun onJupiterSuccess(isSuccess: Boolean, code: JupiterErrorCode?) {}
    override fun onJupiterReport(code: JupiterServiceCode, msg: String) {}
    override fun onJupiterResult(result: JupiterResult) {}
    override fun isJupiterInOutStateChanged(state: InOutState) {}
    override fun isUserGuidanceOut() {}
    override fun isNavigationRouteChanged(routes: MutableList<JupiterNavigationRoute>) {}
    override fun isNavigationRouteFailed() {}
    override fun isWaypointChanged(waypoints: MutableList<out MutableList<Double>>) {}
}
```

```kotlin
manager.startService(
    JupiterRegion.KOREA.value,
    sectorId,
    UserMode.MODE_VEHICLE,
    callback
)
```

Sector ID note:
- `sectorId = 20` corresponds to **Songdo Convensia**.
- Sector IDs are assigned and managed by TJLabs.
- For production usage, use the sector ID provided by TJLabs.

### 5. Stop service

Input:
- no input parameters

Output:
- callback `(success: Boolean, message: String)`

```kotlin
manager.stopService { success, message ->
    // handle stop result
}
```

### 6. Toggle mocking mode

Input:
- `enabled: Boolean`

Output:
- mock mode state changes in SDK and sample log/toast feedback

```kotlin
manager.setMockingMode(true)  // ON
manager.setMockingMode(false) // OFF
```
