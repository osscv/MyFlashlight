package net.dkly.myflashlight

import android.content.Context

class FlashlightSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "flashlight_settings",
        Context.MODE_PRIVATE
    )

    var hapticsEnabled: Boolean
        get() = preferences.getBoolean(KEY_HAPTICS_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_HAPTICS_ENABLED, value).apply()

    var keepScreenAwake: Boolean
        get() = preferences.getBoolean(KEY_KEEP_SCREEN_AWAKE, false)
        set(value) = preferences.edit().putBoolean(KEY_KEEP_SCREEN_AWAKE, value).apply()

    var startOnLaunch: Boolean
        get() = preferences.getBoolean(KEY_START_ON_LAUNCH, false)
        set(value) = preferences.edit().putBoolean(KEY_START_ON_LAUNCH, value).apply()

    var backgroundFlashlightEnabled: Boolean
        get() = preferences.getBoolean(KEY_BACKGROUND_FLASHLIGHT_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_BACKGROUND_FLASHLIGHT_ENABLED, value).apply()

    var torchEnabled: Boolean
        get() = preferences.getBoolean(KEY_TORCH_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_TORCH_ENABLED, value).apply()

    var strengthLevel: Int
        get() = preferences.getInt(KEY_STRENGTH_LEVEL, 1)
        set(value) = preferences.edit().putInt(KEY_STRENGTH_LEVEL, value).apply()

    var mode: FlashlightMode
        get() = FlashlightMode.fromName(preferences.getString(KEY_MODE, null))
        set(value) = preferences.edit().putString(KEY_MODE, value.name).apply()

    /** Auto-shutoff timer in minutes. 0 = disabled. */
    var autoShutoffMinutes: Int
        get() = preferences.getInt(KEY_AUTO_SHUTOFF_MINUTES, 0)
        set(value) = preferences.edit().putInt(KEY_AUTO_SHUTOFF_MINUTES, value).apply()

    private companion object {
        const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        const val KEY_START_ON_LAUNCH = "start_on_launch"
        const val KEY_BACKGROUND_FLASHLIGHT_ENABLED = "background_flashlight_enabled"
        const val KEY_TORCH_ENABLED = "torch_enabled"
        const val KEY_STRENGTH_LEVEL = "strength_level"
        const val KEY_MODE = "flashlight_mode"
        const val KEY_AUTO_SHUTOFF_MINUTES = "auto_shutoff_minutes"
    }
}
