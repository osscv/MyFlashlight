package net.dkly.myflashlight

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class FlashlightWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val settings = FlashlightSettings(context)
        val views = buildRemoteViews(context, settings.torchEnabled)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            toggleTorch(context)
        }
    }

    private fun toggleTorch(context: Context) {
        val controller = FlashlightController(context)
        val settings = FlashlightSettings(context)
        val info = runCatching { controller.loadFlashlight() }.getOrNull()
        val cameraId = info?.cameraId
        if (cameraId == null) {
            refresh(context)
            controller.dispose()
            return
        }

        val nextEnabled = !settings.torchEnabled
        val strength = settings.strengthLevel.coerceIn(1, info.maxStrengthLevel)

        val ok = runCatching {
            controller.setPower(
                cameraId = cameraId,
                enabled = nextEnabled,
                mode = FlashlightMode.STEADY,
                strengthLevel = strength,
                maxStrengthLevel = info.maxStrengthLevel
            )
        }.isSuccess

        if (ok) {
            settings.torchEnabled = nextEnabled
            if (nextEnabled) settings.mode = FlashlightMode.STEADY
        }
        controller.dispose()
        refresh(context)
    }

    companion object {
        const val ACTION_TOGGLE = "net.dkly.myflashlight.widget.TOGGLE"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FlashlightWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val views = buildRemoteViews(context, FlashlightSettings(context).torchEnabled)
            manager.updateAppWidget(ids, views)
        }

        private fun buildRemoteViews(context: Context, enabled: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_flashlight)

            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (enabled) R.drawable.widget_background_on else R.drawable.widget_background_off
            )
            views.setContentDescription(
                R.id.widget_root,
                context.getString(
                    if (enabled) R.string.widget_toggle_off_description
                    else R.string.widget_toggle_on_description
                )
            )
            views.setTextViewText(
                R.id.widget_label,
                context.getString(if (enabled) R.string.widget_label_on else R.string.widget_label_off)
            )

            val intent = Intent(context, FlashlightWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            return views
        }
    }
}
