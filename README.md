# My Flashlight

A small Android flashlight app built for fun while learning Android APIs.

This project is intentionally simple: it turns the phone camera torch on and off, shows a custom flashlight-style UI, and uses the Android camera APIs to control torch brightness when the device supports it.

## Why this exists

I made this as a basic learning project to experiment with:

- Android app structure
- Kotlin
- Jetpack Compose UI
- `CameraManager`
- Camera torch callbacks
- Device capability checks
- Android API version differences

Nothing too serious. Just learning, testing, and having fun with Android.

## Features

- Turn the phone flashlight on and off
- Detect whether the device has a camera flash
- Show torch availability status
- Custom full-screen flashlight UI
- Light beam and flashlight body drawn with Compose
- Smooth on/off and beam brightness animation
- Haptic feedback on controls
- Brightness slider on supported devices
- Optional keep-screen-awake mode
- Optional start-on-launch behavior
- Android Quick Settings tile for toggling the flashlight
- Automatically turns the torch off when the app closes

## Brightness support note

Flashlight brightness control is only available through the public Android API on Android 13 and newer, and only when the device camera hardware reports multiple torch strength levels.

The app uses:

- `CameraManager.setTorchMode(...)` for basic on/off control
- `CameraManager.turnOnTorchWithStrengthLevel(...)` for brightness control on supported Android 13+ devices
- `CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL` to check whether brightness control is available
- `TileService` for the Quick Settings shortcut
- `SharedPreferences` for simple local settings

On Android 12 and below, the app still supports reliable on/off flashlight control, but real torch brightness control is not exposed by the public Android SDK.

## Tech stack

- Kotlin
- Jetpack Compose
- Material 3
- Android Camera2 API
- Gradle Kotlin DSL

## Requirements

- Android Studio
- Android SDK
- JDK 11 or newer
- Android device with camera flash for real flashlight testing

Minimum SDK:

```text
Android 7.0 Nougat, API 24
```

Target SDK:

```text
Android API 36
```

## Build

From the project root:

```powershell
.\gradlew.bat assembleDebug
```

If Java is not on your `PATH`, set `JAVA_HOME` first. For example, when using Android Studio's bundled JBR on Windows:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

The debug APK will be generated under:

```text
app/build/outputs/apk/debug/
```

## Run

Open the project in Android Studio, connect a real Android phone, then run the `app` configuration.

An emulator usually does not have a real camera flash, so testing on a physical device is recommended.

## Project status

This is a learning project, not a production flashlight app.

Possible future improvements:

- Better app icon
- More device-specific testing
- Lock-screen widget support
- Automatic shutoff timer
- Better accessibility polish

## License

No license selected yet. Add one before publishing or reusing this project seriously.
