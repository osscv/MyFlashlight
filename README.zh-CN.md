# My Flashlight

> 一款用心打造的 Android 手电筒应用。
> 原生摄像头闪光灯控制、亮度调节、动画效果，一样都不少。

[English](README.md) | [中文](README.zh-CN.md)

---

## 它能做什么

轻触一下，手机闪光灯秒变手电筒。滑动条调节亮度。通知栏快捷开关。后台持续运行。就是好用。

## 功能特性

| 功能 | 说明 |
|---|---|
| **一键开关** | 大号电源按钮，即时开关 |
| **亮度调节** | 垂直滑块，支持 Android 13+ 多级亮度硬件 |
| **快捷设置磁贴** | 无需打开应用即可切换 |
| **动画界面** | 自定义光束、手电筒外形和流畅亮度过渡 —— 全部用 Compose Canvas 绘制 |
| **后台模式** | 前台服务确保离开应用后闪光灯继续工作 |
| **启动即亮** | 可选：打开应用时自动开启闪光灯 |
| **屏幕常亮** | 闪光灯开启时防止屏幕休眠 |
| **触觉反馈** | 每次操作都有振动反馈 |
| **智能警告** | 闪光灯开启时，电量低或设备过热会自动提醒 |
| **自动关闭** | 应用销毁时自动关闭闪光灯 |
| **无闪光灯检测** | 对没有摄像头闪光灯的设备友好提示 |

## 工作原理

```
  用户点击电源按钮
        |
        v
  FlashlightController
        |
        +---> CameraManager.getCameraCharacteristics()
        |         |
        |         +---> 查找带闪光灯的后置摄像头
        |         +---> 读取 FLASH_INFO_STRENGTH_MAXIMUM_LEVEL
        |
        +---> setTorchMode()                          -- 基本开关
        +---> turnOnTorchWithStrengthLevel()           -- 亮度控制 (API 33+)
        |
        v
  CameraManager.TorchCallback  <-- 实时获取闪光灯状态
        |
        v
  Compose 重新组合 UI  （光束亮度、按钮状态、状态文字）
```

`TileService` 负责快捷设置磁贴。`ForegroundService` 确保后台闪光灯持续运行。`SharedPreferences` 在会话之间保存你的设置。

## 亮度支持

真正的闪光灯亮度控制需要：

1. **Android 13（API 33）** 或更高版本
2. 摄像头硬件报告 `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL > 1`

在旧版 Android 上，应用提供可靠的开关控制。UI 会自动适配 —— 当滑块不可用时显示固定亮度说明。

## 界面设计

界面是一个深色渐变背景，搭配用 Compose `Canvas` 自定义绘制的手电筒和光束。当闪光灯开启时：

- 光束动画匹配当前亮度等级
- 电源按钮略微放大，提供触觉反馈
- 状态文字实时更新

当闪光灯关闭时，光束淡出为微弱辉光，让你仍能看到布局。

安全警告会自动弹出：

- **电量低**（低于 15%，未在充电）—— "电量较低，手电筒可能很快消耗电量。"
- **设备过热**（热状态严重或更高）—— "设备较热，请暂时关闭手电筒。"

## 技术栈

| 层级 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 相机 | Camera2 API（`CameraManager`、`CameraCharacteristics`） |
| 持久化 | SharedPreferences |
| 系统集成 | `TileService`（快捷设置）、`ForegroundService`（后台闪光灯） |
| 构建系统 | Gradle Kotlin DSL |

## 项目结构

```
net/dkly/myflashlight/
|
+-- MainActivity.kt               主界面、状态管理、相机回调
|   +-- FlashlightScreen()        根组合函数 —— 布局、动画、设置面板
|   +-- BeamBackdrop()            自定义 Canvas 绘制：光束、手电筒外形
|   +-- FlashlightPowerButton()   带手电筒图标的动画电源按钮
|   +-- VerticalBrightnessSlider() 旋转滑块，控制闪光灯亮度
|   +-- SettingsPanel()           设置开关面板
|   +-- BrightnessHelpDialog()    解释为什么亮度功能不可用
|
+-- FlashlightController.kt       摄像头闪光灯逻辑 —— 发现、开关、亮度
+-- FlashlightSettings.kt         所有设置的 SharedPreferences 封装
+-- FlashlightTileService.kt      快捷设置磁贴提供者
+-- FlashlightForegroundService.kt 后台服务，保持闪光灯运行
+-- ui/theme/                     Material 3 颜色、字体、主题
```

## 环境要求

| | |
|---|---|
| 最低 SDK | **24**（Android 7.0 Nougat） |
| 目标 SDK | **36** |
| JDK | 11+ |
| 设备 | 带摄像头闪光灯的实体 Android 手机 |

## 构建与运行

```powershell
# 构建调试 APK
.\gradlew.bat assembleDebug

# 如果 JAVA_HOME 未设置：
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

APK 输出到 `app/build/outputs/apk/debug/`。

直接运行：在 Android Studio 中打开项目，连接设备，选择 `app` 配置，点击运行。模拟器通常没有真实闪光灯 —— 请使用实体设备。

## 权限说明

| 权限 | 用途 |
|---|---|
| `CAMERA` | 访问摄像头闪光灯硬件 |
| `POST_NOTIFICATIONS` | 显示前台服务通知（Android 13+） |
| `FOREGROUND_SERVICE` | 后台保持闪光灯运行 |
| `FOREGROUND_SERVICE_CAMERA` | 声明前台服务类型 |

`camera.flash` 硬件特性声明为 `required="false"` —— 应用可以在没有闪光灯的设备上正常安装，并显示友好的"不可用"提示。

## 未来可能的改进

- 自定义应用图标（目前是 Android 默认图标）
- 锁屏小部件
- 自动关闭定时器
- SOS / 频闪模式
- 更多设备兼容性测试
- 无障碍功能改进

## 许可证

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
