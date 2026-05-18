package net.dkly.myflashlight

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

class FlashlightController(context: Context) {
    private val cameraManager = context.applicationContext
        .getSystemService(Context.CAMERA_SERVICE) as CameraManager

    val cameraManagerInstance: CameraManager
        get() = cameraManager

    fun loadFlashlight(): FlashlightInfo {
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val isBackFacing = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK

            hasFlash && isBackFacing
        } ?: cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

        if (cameraId == null) {
            return FlashlightInfo(cameraId = null, maxStrengthLevel = 1, defaultStrengthLevel = 1)
        }

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val maxStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            characteristics
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                ?.coerceAtLeast(1) ?: 1
        } else {
            1
        }
        val defaultStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            characteristics
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL)
                ?.coerceIn(1, maxStrength) ?: 1
        } else {
            1
        }

        return FlashlightInfo(
            cameraId = cameraId,
            maxStrengthLevel = maxStrength,
            defaultStrengthLevel = defaultStrength
        )
    }

    fun setPower(cameraId: String, enabled: Boolean, strengthLevel: Int, maxStrengthLevel: Int) {
        if (enabled && supportsStrengthControl(maxStrengthLevel)) {
            cameraManager.turnOnTorchWithStrengthLevel(
                cameraId,
                strengthLevel.coerceIn(1, maxStrengthLevel)
            )
        } else {
            cameraManager.setTorchMode(cameraId, enabled)
        }
    }

    fun setStrength(cameraId: String, strengthLevel: Int, maxStrengthLevel: Int) {
        if (supportsStrengthControl(maxStrengthLevel)) {
            cameraManager.turnOnTorchWithStrengthLevel(
                cameraId,
                strengthLevel.coerceIn(1, maxStrengthLevel)
            )
        }
    }

    fun supportsStrengthControl(maxStrengthLevel: Int): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxStrengthLevel > 1
    }
}

data class FlashlightInfo(
    val cameraId: String?,
    val maxStrengthLevel: Int,
    val defaultStrengthLevel: Int
)
