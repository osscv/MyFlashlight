# My Flashlight

> An Android flashlight that does one thing, well — no ads, no trackers, no thirty‑megabyte WebView wrapper.
> Five Kotlin files, one camera permission, real hardware brightness control.

[English](README.md) | [中文](README.zh-CN.md)

---

## The pitch

Search "flashlight" on the Play Store and you will find a hundred apps that bundle a compass, a mirror, a weather widget, and an analytics SDK to flip a single LED. This one does not.

**My Flashlight** is a deliberate minimalist build:

- A single screen, drawn entirely with Jetpack Compose `Canvas` — no XML, no images, no SVGs.
- One declared dangerous permission: `CAMERA`. Nothing leaves the device. Nothing phones home.
- Three entry points to toggle the torch — the app, a Quick Settings tile, and a foreground service — all kept in lock‑step by a single `CameraManager.TorchCallback`.
- Real brightness control on devices that expose it, with a graceful, explanatory fallback when they do not.

The whole project is small enough to read in an afternoon and modify in an evening.

## Highlights

| | |
|---|---|
| **Tap to toggle** | One big round power button, with a soft scale animation and a long‑press haptic |
| **Hardware brightness** | A rotated vertical slider that drives `turnOnTorchWithStrengthLevel()` on Android 13+ |
| **Quick Settings tile** | Toggle the torch from anywhere, without launching the app |
| **Background mode** | An opt‑in `FOREGROUND_SERVICE_TYPE_CAMERA` keeps the LED lit after you swipe away |
| **Live state sync** | `TorchCallback` reflects external changes (other apps, the tile, the system) back into the UI in real time |
| **Battery & thermal guards** | Warns when the battery drops to ≤ 15 % unplugged, or when `PowerManager` reports a severe thermal state |
| **Soft fallback** | Devices without a flash install fine and show a friendly "not available" notice |
| **Persistent settings** | Six keys in `SharedPreferences` — haptics, screen wake, start on launch, background mode, last brightness, last state |
| **Edge‑to‑edge dark UI** | A vertical gradient, a hand‑drawn flashlight, and an animated light beam |

## Architecture at a glance

```
                              ┌──────────────────────────────┐
                              │     CameraManager (system)   │
                              └──────────────┬───────────────┘
                                             │
                                  TorchCallback (single source of truth)
                                             │
        ┌────────────────────┬───────────────┼───────────────┬────────────────┐
        ▼                    ▼               ▼               ▼                ▼
  MainActivity        TileService     ForegroundService   Settings        Compose UI
  (foreground UI)    (Quick Settings)  (background torch) (persistence)   (recomposes)
        │                    │               │
        └────────────────────┴───────────────┘
                             │
                  FlashlightController
                  ├─ loadFlashlight()                  → finds the back camera with a flash
                  ├─ setPower(id, on, level, max)      → turnOnTorchWithStrengthLevel / setTorchMode
                  ├─ setStrength(id, level, max)       → live brightness update (API 33+)
                  └─ supportsStrengthControl(max)      → API 33+ AND max level > 1
```

Each surface (Activity, Tile, foreground Service) owns its own `FlashlightController` and `FlashlightSettings` instance and never talks directly to the others. They stay coherent because every torch change funnels through `CameraManager`, and every callback comes back through `TorchCallback`. That is the trick: there is no shared state to invalidate.

## The brightness question

True torch brightness on Android is not a software setting — it is a hardware capability that the kernel must expose. The app probes for it on launch:

```kotlin
val maxStrength = characteristics
    .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
    ?.coerceAtLeast(1) ?: 1
```

The slider appears only when **both** conditions hold:

1. The device is running **Android 13 (API 33)** or newer, and
2. `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL` is greater than `1`.

If either is missing, the slider is replaced by a small card titled *Fixed brightness* with a "Why not here?" button that opens an honest explanation. No fake slider that pretends to do something.

## Safety logic

The app watches two system signals while the torch is on:

| Signal | Source | Trigger | Message |
|---|---|---|---|
| Battery | `ACTION_BATTERY_CHANGED` broadcast | `level ≤ 15` and not plugged in | *"Battery is low. The flashlight may drain it quickly."* |
| Thermal | `PowerManager.OnThermalStatusChangedListener` (API 29+) | `THERMAL_STATUS_SEVERE` or worse | *"The device is hot. Please turn the flashlight off for a moment."* |

The warning bar appears and disappears with an animated visibility transition. Both listeners are attached in `onStart` and detached in `onStop`, so they cost nothing when the app is in the background.

## The UI, drawn from code

There is not a single bitmap in the resources for the main screen. The whole illustration is rendered every frame by `BeamBackdrop`:

- A vertical gradient from slate blue to a slight green tint at the bottom.
- A radial highlight near the top to suggest light spilling onto a wall.
- A `Path` shaped like a truncated cone — the beam — filled with a layered vertical gradient whose alpha is multiplied by the current brightness.
- Two `drawRoundRect` calls for the flashlight head and handle, each with their own gradient and a thin specular highlight strip on the side.

The power button is a `Surface` clipped to a circle, scaling to `1.08x` when on, with the flashlight icon (cap, body, and three radiating "shine" lines) drawn by hand inside a `Canvas`. The brightness slider is an ordinary Material 3 `Slider` rotated `-90°` via `graphicsLayer` — the laziest way to get a vertical slider that still feels native.

## Tech stack

| Layer | Detail |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, `androidx.activity:compose` |
| Camera | Camera2 (`CameraManager`, `CameraCharacteristics`, `TorchCallback`) |
| System integration | `TileService` for Quick Settings, `Service` with `FOREGROUND_SERVICE_TYPE_CAMERA` for background mode |
| Sensors | `BatteryManager` broadcast, `PowerManager` thermal listener |
| Persistence | `SharedPreferences` (six keys, no DB) |
| Build | Gradle Kotlin DSL, AGP, `minSdk` 24, `targetSdk` 36 |

## Project layout

```
net/dkly/myflashlight/
├── MainActivity.kt                ─ Compose UI, permission flow, lifecycle, sensor wiring
│   ├─ FlashlightScreen()          · root layout
│   ├─ BeamBackdrop()              · Canvas illustration (beam + body)
│   ├─ FlashlightPowerButton()     · animated power button
│   ├─ VerticalBrightnessSlider()  · rotated Material 3 slider
│   ├─ SettingsPanel()             · the four toggles
│   └─ BrightnessHelpDialog()      · "why no slider?"
│
├── FlashlightController.kt        ─ camera discovery + torch on/off + strength
├── FlashlightSettings.kt          ─ SharedPreferences wrapper (six keys)
├── FlashlightTileService.kt       ─ Quick Settings tile (toggle without opening app)
├── FlashlightForegroundService.kt ─ background torch (FOREGROUND_SERVICE_TYPE_CAMERA)
└── ui/theme/                      ─ Material 3 colors, typography, theme
```

## Requirements

| | |
|---|---|
| Min SDK | 24 (Android 7.0 Nougat) |
| Target SDK | 36 |
| JDK | 11+ |
| Recommended | A physical Android device with a camera flash. Emulators rarely simulate one. |

## Build & run

```powershell
# Debug APK
.\gradlew.bat assembleDebug

# If JAVA_HOME isn't set, point it at the JBR bundled with Android Studio:
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Or open the project in Android Studio, attach a device, and press Run.

## Permissions, in plain English

| Permission | What it actually does here |
|---|---|
| `CAMERA` | Required by `CameraManager.setTorchMode()` — the torch is part of the camera subsystem. |
| `POST_NOTIFICATIONS` | Android 13+ requires it before the background notification can show. Only requested when you enable background mode. |
| `FOREGROUND_SERVICE` | Lets the app start a long‑running service when background mode is on. |
| `FOREGROUND_SERVICE_CAMERA` | Declares that the service uses the camera — required by the manifest's `foregroundServiceType="camera"`. |

`android.hardware.camera.flash` is declared as `required="false"`, so the app installs cleanly on flash‑less devices.

## Roadmap

- A real app icon (the current one is the Android default — please send a design)
- Lock‑screen widget
- Auto‑shutoff timer
- SOS and strobe modes
- Wider device testing, especially on Android 14/15 OEM skins
- Accessibility pass (TalkBack labels, contrast)

Small, focused pull requests welcome.

## License

MIT — Copyright © 2026 [dkly](https://www.dkly.net)

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
