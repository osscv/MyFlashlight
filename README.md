# My Flashlight

> A hand-crafted Android flashlight — not another WebView wrapper.
> Real camera torch control, real brightness adjustment, real animations.

[English](README.md) | [中文](README.zh-CN.md)

---

## What it does

Turn your phone's camera flash into a flashlight with a single tap. Adjust brightness with a slider. Toggle from your notification shade. Leave it running in the background. It just works.

## Features

| Feature | Details |
|---|---|
| **One-tap toggle** | Big power button, instant on/off |
| **Brightness control** | Vertical slider on Android 13+ devices with multi-level torch hardware |
| **Quick Settings tile** | Toggle without opening the app |
| **Animated UI** | Custom light beam, flashlight body, and smooth brightness transitions — all drawn with Compose Canvas |
| **Background mode** | Foreground service keeps the torch alive when you leave the app |
| **Start on launch** | Optionally turn on the moment the app opens |
| **Keep screen awake** | Prevents the display from sleeping while the flashlight is active |
| **Haptic feedback** | Vibration on every control interaction |
| **Smart warnings** | Battery-low and device-overheat alerts when the torch is on |
| **Auto-off** | Torch shuts down when the app is destroyed |
| **No-flash detection** | Gracefully handles devices without camera flash |

## How it works

```
  User taps power
        |
        v
  FlashlightController
        |
        +---> CameraManager.getCameraCharacteristics()
        |         |
        |         +---> Find back-facing camera with flash
        |         +---> Read FLASH_INFO_STRENGTH_MAXIMUM_LEVEL
        |
        +---> setTorchMode()                          -- basic on/off
        +---> turnOnTorchWithStrengthLevel()           -- brightness (API 33+)
        |
        v
  CameraManager.TorchCallback  <-- live torch state updates
        |
        v
  Compose recomposes UI  (beam brightness, button state, status text)
```

A `TileService` handles Quick Settings. A `ForegroundService` keeps the torch running in the background. `SharedPreferences` persists your settings between sessions.

## Brightness support

Real torch brightness control requires:

1. **Android 13 (API 33)** or newer
2. Camera hardware that reports `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL > 1`

On older Android versions, the app provides reliable on/off control. The UI adapts — showing a fixed-brightness notice with an explanation when the slider isn't available.

## The UI

The interface is a dark gradient background with a custom-drawn flashlight and light beam using Compose `Canvas`. When the torch is on:

- The beam animates to match the current brightness level
- The power button scales up slightly for tactile feedback
- Status text updates in real time

When the torch is off, the beam fades to a dim glow so you can still see the layout.

Safety warnings appear automatically:

- **Low battery** (under 15%, not charging) — "Battery is low. The flashlight may drain it quickly."
- **Device overheating** (thermal status severe or worse) — "The device is hot. Turn the flashlight off for a moment."

## Tech stack

| Layer | What |
|---|---|
| Language | Kotlin |
| UI framework | Jetpack Compose + Material 3 |
| Camera | Camera2 API (`CameraManager`, `CameraCharacteristics`) |
| Persistence | SharedPreferences |
| System integration | `TileService` (Quick Settings), `ForegroundService` (background torch) |
| Build system | Gradle Kotlin DSL |

## Project structure

```
net/dkly/myflashlight/
|
+-- MainActivity.kt               Main UI screen, state management, camera callbacks
|   +-- FlashlightScreen()        Root composable — layout, animations, settings panel
|   +-- BeamBackdrop()            Custom Canvas drawing: light beam, flashlight body
|   +-- FlashlightPowerButton()   Animated power button with flashlight icon
|   +-- VerticalBrightnessSlider() Rotated slider for torch strength
|   +-- SettingsPanel()           Toggle switches for preferences
|   +-- BrightnessHelpDialog()    Explains why brightness isn't available
|
+-- FlashlightController.kt       Camera torch logic — discovery, on/off, brightness
+-- FlashlightSettings.kt         SharedPreferences wrapper for all settings
+-- FlashlightTileService.kt      Quick Settings tile provider
+-- FlashlightForegroundService.kt Background service to keep the torch alive
+-- ui/theme/                     Material 3 colors, typography, theme
```

## Requirements

| | |
|---|---|
| Min SDK | **24** (Android 7.0 Nougat) |
| Target SDK | **36** |
| JDK | 11+ |
| Device | Physical Android phone with camera flash |

## Build & run

```powershell
# Build the debug APK
.\gradlew.bat assembleDebug

# If JAVA_HOME isn't set:
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

To run directly: open in Android Studio, connect a device, select the `app` config, and hit Run. Emulators usually lack a real flash — use a physical device.

## Permissions

| Permission | Why |
|---|---|
| `CAMERA` | Access the camera torch hardware |
| `POST_NOTIFICATIONS` | Show foreground service notification (Android 13+) |
| `FOREGROUND_SERVICE` | Keep the torch running in the background |
| `FOREGROUND_SERVICE_CAMERA` | Declare the foreground service type |

The `camera.flash` feature is declared as `required="false"` — the app installs fine on flash-less devices and shows a friendly "not available" message.

## What could be next

- Custom app icon (the current one is the Android default)
- Lock-screen widget
- Auto-shutoff timer
- SOS / strobe mode
- More device-specific testing
- Accessibility improvements

## License

MIT License

Copyright (c) 2026 [dkly](https://www.dkly.net)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
