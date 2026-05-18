package net.dkly.myflashlight

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class FlashlightForegroundService : Service() {
    private lateinit var flashlightController: FlashlightController
    private lateinit var settings: FlashlightSettings
    private var cameraId: String? = null
    private var maxStrengthLevel: Int = 1

    override fun onCreate() {
        super.onCreate()
        flashlightController = FlashlightController(this)
        settings = FlashlightSettings(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopFlashlight()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val flashlightInfo = flashlightController.loadFlashlight()
        cameraId = flashlightInfo.cameraId
        maxStrengthLevel = flashlightInfo.maxStrengthLevel

        if (cameraId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val enabled = intent?.getBooleanExtra(EXTRA_ENABLED, true) ?: true
        val strength = intent?.getIntExtra(EXTRA_STRENGTH_LEVEL, settings.strengthLevel) ?: settings.strengthLevel
        val notification = buildNotification(enabled, strength)

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)

        if (enabled) {
            flashlightController.setPower(
                cameraId!!,
                true,
                strength,
                maxStrengthLevel
            )
            settings.torchEnabled = true
        } else {
            stopFlashlight()
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopFlashlight()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopFlashlight() {
        val id = cameraId
        if (id != null) {
            runCatching {
                flashlightController.setPower(id, false, settings.strengthLevel, maxStrengthLevel)
            }
        }
        settings.torchEnabled = false
    }

    private fun buildNotification(enabled: Boolean, strengthLevel: Int) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_flashlight_tile)
        .setContentTitle("My Flashlight")
        .setContentText(
            if (enabled) {
                "Flashlight running in background at level $strengthLevel"
            } else {
                "Flashlight background mode"
            }
        )
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Flashlight background mode",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the flashlight on while the app is in the background"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "net.dkly.myflashlight.action.STOP"
        const val EXTRA_ENABLED = "extra_enabled"
        const val EXTRA_STRENGTH_LEVEL = "extra_strength_level"
        const val EXTRA_CAMERA_ID = "extra_camera_id"
        private const val CHANNEL_ID = "flashlight_background"
        private const val NOTIFICATION_ID = 2001
    }
}
