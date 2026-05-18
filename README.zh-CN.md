# My Flashlight

> 一款只做好一件事的 Android 手电筒——没有广告、没有追踪、也没有体积臃肿的 WebView 套壳。
> 五个 Kotlin 文件，一个相机权限，真正的硬件亮度控制。

[English](README.md) | [中文](README.zh-CN.md)

---

## 这个应用想做什么

在应用商店里搜「手电筒」，你会看到上百款应用——为了点亮一颗 LED，它们打包了指南针、镜子、天气小部件，外加埋藏的数据采集 SDK。这一款不是这样。

**My Flashlight** 是一次刻意的极简实现：

- 整个界面只有一个屏幕，全部用 Jetpack Compose 的 `Canvas` 绘制——没有 XML、没有位图、没有 SVG。
- 只申请一项危险权限：`CAMERA`。任何数据都不会离开设备，也不会回传到任何服务器。
- 三种入口可以切换闪光灯——主界面、快捷设置磁贴、前台服务——它们由同一个 `CameraManager.TorchCallback` 统一保持同步。
- 在硬件支持的设备上提供真正的亮度控制；在不支持的设备上，会以友好、清晰的方式说明原因。

整个项目小到一个下午就能读完，一个晚上就能改完。

## 功能亮点

| | |
|---|---|
| **一键开关** | 大号圆形电源按钮，配有轻柔的缩放动画和长按触感反馈 |
| **硬件亮度调节** | 一个旋转过的垂直滑块，在 Android 13+ 上调用 `turnOnTorchWithStrengthLevel()` |
| **频闪与 SOS 模式** | 由 pattern runner 按时序切换 `setTorchMode`——快速频闪，或 `...---...` 摩尔斯电码 |
| **自动关闭定时器** | 可循环切换：关闭 / 1 / 5 / 15 / 30 / 60 分钟；主界面和前台服务都会遵守 |
| **主屏与锁屏小部件** | 一个 `AppWidgetProvider`，类别声明为 `home_screen\|keyguard`——点击切换，状态与其他入口保持同步 |
| **快捷设置磁贴** | 不必打开应用，也能从任意界面切换闪光灯 |
| **后台模式** | 可选的 `FOREGROUND_SERVICE_TYPE_CAMERA`，让你划掉应用后闪光灯依旧常亮 |
| **状态实时同步** | `TorchCallback` 会把外部变更（其他应用、磁贴、小部件、系统）即时反映到界面上 |
| **电量与温度守护** | 当电量降到 ≤ 15% 且未充电，或 `PowerManager` 报告严重发热时主动提示 |
| **无障碍友好** | 每个控件都带有 TalkBack 标签和 `Role.Button` 语义，文字对比度提高，模式按钮会播报选中状态 |
| **优雅降级** | 没有闪光灯的设备同样可以安装，并显示「不可用」的友好提示 |
| **持久化设置** | 用 `SharedPreferences` 保存八个键：触感反馈、屏幕常亮、启动即开、后台模式、上次亮度、上次开关、模式、关闭定时器 |
| **沉浸式深色界面** | 垂直渐变背景，手绘的手电筒，动态光束 |

## 架构一览

```
                              ┌──────────────────────────────┐
                              │      CameraManager（系统）   │
                              └──────────────┬───────────────┘
                                             │
                              TorchCallback（唯一的状态来源）
                                             │
        ┌────────────────────┬───────────────┼───────────────┬────────────────┐
        ▼                    ▼               ▼               ▼                ▼
   MainActivity         TileService     ForegroundService   Settings      Compose UI
   （前台界面）          （快捷磁贴）     （后台闪光灯）       （持久化）     （自动重组）
        │                    │               │
        └────────────────────┴───────────────┘
                             │
                  FlashlightController
                  ├─ loadFlashlight()                  → 查找带闪光灯的后置摄像头
                  ├─ setPower(id, on, level, max)      → turnOnTorchWithStrengthLevel / setTorchMode
                  ├─ setStrength(id, level, max)       → 实时更新亮度（API 33+）
                  └─ supportsStrengthControl(max)      → 仅当 API 33+ 且 max > 1 时为 true
```

主界面、磁贴、前台服务三者各自持有一份 `FlashlightController` 和 `FlashlightSettings`，彼此之间不会直接通信。它们之所以能始终保持一致，是因为每一次开关都通过 `CameraManager`，每一次结果都经由 `TorchCallback` 回传——这样根本就没有共享状态需要维护。

## 关于亮度

Android 上真正的「闪光灯亮度」并不是软件设置，而是硬件能力，需要内核驱动主动暴露出来。应用在启动时会进行探测：

```kotlin
val maxStrength = characteristics
    .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
    ?.coerceAtLeast(1) ?: 1
```

只有同时满足以下两个条件，亮度滑块才会出现：

1. 系统版本是 **Android 13（API 33）** 或更高，并且
2. `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL` 大于 `1`。

任一条件不满足，滑块会被替换成一张写着「固定亮度」的小卡片，并附带一个「为什么不行？」按钮，点开会有诚实的说明。绝不放一个假装能用的滑块。

## 安全监测逻辑

闪光灯开启期间，应用会同时关注两个系统信号：

| 信号 | 来源 | 触发条件 | 提示文案 |
|---|---|---|---|
| 电池 | `ACTION_BATTERY_CHANGED` 广播 | 电量 ≤ 15% 且未充电 | *「电量较低，闪光灯可能会迅速耗电。」* |
| 温度 | `PowerManager.OnThermalStatusChangedListener`（API 29+） | `THERMAL_STATUS_SEVERE` 或更高 | *「设备温度较高，请先关闭闪光灯让它降温。」* |

警告条带动画显隐。两个监听器都在 `onStart` 中注册、在 `onStop` 中注销，因此应用在后台时几乎没有开销。

## 一切都画出来的界面

主界面没有任何位图资源。`BeamBackdrop` 在每一帧都会把整个画面重新绘制一遍：

- 一层从板岩蓝过渡到淡绿色调的垂直渐变作为背景。
- 顶部加一道径向高光，营造光线打在墙上的感觉。
- 一个像截顶圆锥的 `Path`——这就是光束本体——填充一组叠加的垂直渐变，其透明度会跟随当前亮度实时变化。
- 两次 `drawRoundRect` 画出手电筒的灯头和握把，每段都有自己的渐变，侧面还点缀着细细的高光条。

电源按钮是一个被裁成圆形的 `Surface`，开启时缩放到 `1.08x`，按钮内部由 `Canvas` 手绘出手电筒图标（灯头、灯身，以及三道呈放射状的「光芒」）。亮度滑块只是一个普通的 Material 3 `Slider`，通过 `graphicsLayer` 旋转 `-90°` 实现垂直方向——是「能用就行」式做法里最省力的一种。

## 技术栈

| 层级 | 详情 |
|---|---|
| 语言 | Kotlin |
| 界面 | Jetpack Compose、Material 3、`androidx.activity:compose` |
| 相机 | Camera2（`CameraManager`、`CameraCharacteristics`、`TorchCallback`） |
| 系统集成 | `TileService` 提供快捷设置磁贴；`Service` 配 `FOREGROUND_SERVICE_TYPE_CAMERA` 用于后台模式 |
| 传感器 | `BatteryManager` 广播、`PowerManager` 热度监听 |
| 持久化 | `SharedPreferences`（六个键，不使用数据库） |
| 构建 | Gradle Kotlin DSL、AGP，`minSdk` 24，`targetSdk` 36 |

## 项目结构

```
net/dkly/myflashlight/
├── MainActivity.kt                ─ Compose 界面、权限流程、生命周期、传感器接入
│   ├─ FlashlightScreen()          · 根布局
│   ├─ BeamBackdrop()              · Canvas 绘制（光束 + 手电筒外形）
│   ├─ FlashlightPowerButton()     · 动画电源按钮
│   ├─ VerticalBrightnessSlider()  · 旋转过的 Material 3 滑块
│   ├─ SettingsPanel()             · 四个开关
│   └─ BrightnessHelpDialog()      · 解释「为什么没有亮度滑块」
│
├── FlashlightController.kt        ─ 相机查找 + 闪光灯开关 + 强度
├── FlashlightSettings.kt          ─ SharedPreferences 封装（六个键）
├── FlashlightTileService.kt       ─ 快捷设置磁贴（无需打开应用即可切换）
├── FlashlightForegroundService.kt ─ 后台闪光灯服务（FOREGROUND_SERVICE_TYPE_CAMERA）
└── ui/theme/                      ─ Material 3 颜色、字体、主题
```

## 环境要求

| | |
|---|---|
| 最低 SDK | 24（Android 7.0 Nougat） |
| 目标 SDK | 36 |
| JDK | 11 及以上 |
| 推荐设备 | 带闪光灯的实体 Android 手机；模拟器通常无法模拟真实闪光灯。 |

## 构建与运行

```powershell
# 构建调试版 APK
.\gradlew.bat assembleDebug

# 如果未设置 JAVA_HOME，可以指向 Android Studio 自带的 JBR：
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`。也可以在 Android Studio 中打开项目，连接设备，点击 Run 直接运行。

## 权限说明

| 权限 | 在本应用中的实际用途 |
|---|---|
| `CAMERA` | `CameraManager.setTorchMode()` 必需——闪光灯属于相机子系统。 |
| `POST_NOTIFICATIONS` | Android 13+ 要求显式申请，才能弹出后台模式所需的常驻通知；只在你启用后台模式时才会请求。 |
| `FOREGROUND_SERVICE` | 允许应用启动长生命周期的前台服务，以承载后台模式。 |
| `FOREGROUND_SERVICE_CAMERA` | 声明前台服务的类型为「相机」——清单中 `foregroundServiceType="camera"` 对应的运行时权限。 |

`android.hardware.camera.flash` 声明为 `required="false"`，因此即便设备没有闪光灯，应用也能正常安装。

## 未来计划

- 一个真正的应用图标（目前还是 Android 默认图标——欢迎投稿设计）
- 更广的设备测试，尤其是 Android 14/15 的各家 OEM 系统
- 进一步的无障碍打磨（TalkBack 提示语优化、更大的触控目标）

欢迎提交小而专注的 Pull Request。

## 许可证

MIT 协议 — Copyright © 2026 [dkly](https://www.dkly.net)

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
