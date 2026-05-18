package net.dkly.myflashlight

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class FlashlightTileService : TileService() {
    private lateinit var settings: FlashlightSettings
    private lateinit var flashlightController: FlashlightController

    override fun onCreate() {
        super.onCreate()
        settings = FlashlightSettings(this)
        flashlightController = FlashlightController(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile(settings.torchEnabled)
    }

    override fun onClick() {
        super.onClick()
        val flashlightInfo = runCatching { flashlightController.loadFlashlight() }.getOrNull()
        val cameraId = flashlightInfo?.cameraId
        if (cameraId == null) {
            updateTile(enabled = false, unavailable = true)
            return
        }

        val nextEnabled = !settings.torchEnabled
        val maxStrength = flashlightInfo.maxStrengthLevel
        val strength = settings.strengthLevel.coerceIn(1, maxStrength)

        val changed = runCatching {
            flashlightController.setPower(cameraId, nextEnabled, strength, maxStrength)
        }.isSuccess

        if (changed) {
            settings.torchEnabled = nextEnabled
            updateTile(nextEnabled)
        } else {
            updateTile(enabled = false, unavailable = true)
        }
    }

    private fun updateTile(enabled: Boolean, unavailable: Boolean = false) {
        qsTile?.apply {
            label = "Flashlight"
            subtitle = when {
                unavailable -> "Unavailable"
                enabled -> "On"
                else -> "Off"
            }
            state = when {
                unavailable -> Tile.STATE_UNAVAILABLE
                enabled -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }
}
