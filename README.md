# TJJupiter-demo-android

## Overview

TJJupiter-demo-android is a minimal Android sample app for integrating **TJLabs Jupiter SDK**.

This demo app uses **TJLabs Jupiter SDK 2.0.7**.

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
implementation("com.github.tjlabs:TJLabsJupiter-sdk-android:2.0.7")
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
JupiterServiceManager.auth(application, accessKey, accessSecretKey) { code, success ->
    // handle auth result
}
```

### 4. Initialize service

Input:
- `provider: String` (default: `ServerProvider.AWS.value`)
- `region: String` (default: `JupiterRegion.KOREA.value`)
- `sectorId: Int`
- `callback: JupiterServiceManager.JupiterServiceManagerDelegate`

Output:
- `onInitSuccess(isSuccess, errorCode)`

Explicit example:

```kotlin
JupiterServiceManager.setAuthServer(ServerProvider.AWS.value, JupiterRegion.KOREA.value)

manager.initialize(
    ServerProvider.AWS.value,
    JupiterRegion.KOREA.value,
    sectorId,
    callback
)
```

Default-value example (`provider`, `region` omitted):

```kotlin
manager.initialize(
    sectorId = sectorId,
    callback = callback
)
```

### 5. Start service

Input:
- `mode: UserMode` (this demo uses `UserMode.MODE_VEHICLE`)
- `callback: JupiterServiceManager.JupiterServiceManagerDelegate`

Output:
- `onJupiterSuccess(isSuccess, code)`
- `onJupiterReport(code, msg)`
- `onJupiterResult(result)`
- optional in/out and navigation callbacks

```kotlin
val callback = object : JupiterServiceManager.JupiterServiceManagerDelegate {
    override fun onInitSuccess(isSuccess: Boolean, errorCode: InitErrorCode?) {}
    override fun onJupiterSuccess(isSuccess: Boolean, code: JupiterErrorCode?) {}
    override fun onJupiterReport(code: JupiterServiceCode, msg: String) {}
    override fun onJupiterResult(result: JupiterResult) {}
    override fun isJupiterInOutStateChanged(state: InOutState) {}
    override fun isUserGuidanceOut() {}
    override fun isNavigationRouteChanged(routes: List<JupiterNavigationRoute>) {}
    override fun isNavigationRouteFailed() {}
    override fun isWaypointChanged(waypoints: List<List<Double>>) {}
}
```

```kotlin
manager.startService(UserMode.MODE_VEHICLE, callback)
```

Sector ID note:
- `sectorId = 20` corresponds to **Songdo Convensia**.
- Sector IDs are assigned and managed by TJLabs.
- For production usage, use the sector ID provided by TJLabs.

### 6. Stop service

Input:
- no input parameters

Output:
- callback `(success: Boolean, message: String)`

```kotlin
manager.stopService { success, message ->
    // handle stop result
}
```

### 7. Toggle mocking mode

Input:
- `enabled: Boolean`

Output:
- mock mode state changes in SDK and sample log/toast feedback

```kotlin
manager.setMockingMode(true)  // ON
manager.setMockingMode(false) // OFF
```

### 8. Lifecycle order (required)

`2.0.5` lifecycle must follow:
1. `JupiterServiceManager.setAuthServer(...)` (optional but recommended)
2. `JupiterServiceManager.auth(...)`
3. `manager.initialize(...)`
4. `manager.startService(...)`

If `startService` is called before `initialize` success, SDK can return init-related error flow.

## JVM Annotation Support (2.0.5)

`JupiterServiceManager` now includes JVM annotations for Java interoperability:

- `@JvmStatic`
  - `setSdkInfos(...)`
  - `setAuthServer(...)`
  - `auth(...)`
- `@JvmOverloads`
  - `setAuthServer(provider, region = KOREA)`
  - `initialize(provider = ..., region = ..., sectorId, callback)`
  - `startService(mode = MODE_VEHICLE, callback)`

Default behavior:
- If `provider` and `region` are omitted in `initialize(...)`, SDK uses current auth server config.
- Initial default is `provider=AWS`, `region=KOREA`.

### Java usage example

```java
JupiterServiceManager.setAuthServer(ServerProvider.AWS.getValue(), JupiterRegion.KOREA.getValue());

JupiterServiceManager.auth(getApplication(), accessKey, secretKey, (code, success) -> {
    if (!success) return;

    JupiterServiceManager manager = new JupiterServiceManager(getApplication(), "sample_user_android");
    manager.initialize(ServerProvider.AWS.getValue(), JupiterRegion.KOREA.getValue(), 20, callback);
    manager.startService(UserMode.MODE_VEHICLE, callback);
});
```
