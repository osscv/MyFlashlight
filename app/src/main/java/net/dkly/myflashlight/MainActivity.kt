package net.dkly.myflashlight

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import net.dkly.myflashlight.ui.theme.MyFlashlightTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var flashlightController: FlashlightController
    private lateinit var settings: FlashlightSettings
    private lateinit var cameraManager: CameraManager
    private lateinit var powerManager: PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingBackgroundPermission = mutableStateOf(false)

    private var torchCameraId: String? by mutableStateOf(null)
    private var torchEnabled by mutableStateOf(false)
    private var torchAvailable by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Looking for a flashlight...")
    private var warningMessage by mutableStateOf<String?>(null)
    private var strengthLevel by mutableIntStateOf(1)
    private var maxStrengthLevel by mutableIntStateOf(1)
    private var hapticsEnabled by mutableStateOf(true)
    private var keepScreenAwake by mutableStateOf(false)
    private var startOnLaunch by mutableStateOf(false)
    private var backgroundFlashlightEnabled by mutableStateOf(false)
    private var batteryLevel by mutableIntStateOf(100)
    private var isCharging by mutableStateOf(false)
    private var thermalStatus by mutableIntStateOf(PowerManager.THERMAL_STATUS_NONE)
    private var showBrightnessHelp by mutableStateOf(false)

    private val requestBackgroundPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (cameraGranted && notificationsGranted) {
            backgroundFlashlightEnabled = true
            settings.backgroundFlashlightEnabled = true
            maybeStartBackgroundFlashlight()
        } else {
            pendingBackgroundPermission.value = false
            backgroundFlashlightEnabled = false
            settings.backgroundFlashlightEnabled = false
            statusMessage = "Background flashlight needs camera and notification permission"
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryState(intent)
        }
    }

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        thermalStatus = status
        refreshSafetyWarnings()
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                torchEnabled = enabled
                settings.torchEnabled = enabled
                updateScreenAwakeFlag()
                torchAvailable = true
                statusMessage = if (enabled) "Flashlight is on" else "Flashlight is off"
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) {
                torchEnabled = false
                settings.torchEnabled = false
                updateScreenAwakeFlag()
                torchAvailable = false
                statusMessage = "Flashlight is temporarily unavailable"
            }
        }

        override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
            if (cameraId == torchCameraId && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                strengthLevel = newStrengthLevel.coerceIn(1, maxStrengthLevel)
                settings.strengthLevel = strengthLevel
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = FlashlightSettings(this)
        flashlightController = FlashlightController(this)
        cameraManager = flashlightController.cameraManagerInstance
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        loadSettings()
        loadFlashlight()
        cameraManager.registerTorchCallback(torchCallback, mainHandler)
        updateBatteryState(registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        refreshSafetyWarnings()
        if (startOnLaunch && torchAvailable) {
            updateTorchPower(true)
        }

        enableEdgeToEdge()
        setContent {
            MyFlashlightTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FlashlightScreen(
                        enabled = torchEnabled,
                        available = torchAvailable,
                        statusMessage = statusMessage,
                        strengthLevel = strengthLevel,
                        maxStrengthLevel = maxStrengthLevel,
                        hapticsEnabled = hapticsEnabled,
                        keepScreenAwake = keepScreenAwake,
                        startOnLaunch = startOnLaunch,
                        backgroundFlashlightEnabled = backgroundFlashlightEnabled,
                        brightnessHelpVisible = showBrightnessHelp,
                        warningMessage = warningMessage,
                        onToggle = ::updateTorchPower,
                        onStrengthChange = ::updateStrength,
                        onHapticsChange = ::updateHapticsEnabled,
                        onKeepScreenAwakeChange = ::updateKeepScreenAwake,
                        onStartOnLaunchChange = ::updateStartOnLaunch,
                        onBackgroundFlashlightChange = ::updateBackgroundFlashlight,
                        onBrightnessHelpClick = { showBrightnessHelp = true },
                        onBrightnessHelpDismiss = { showBrightnessHelp = false },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.addThermalStatusListener(thermalListener)
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(batteryReceiver) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { powerManager.removeThermalStatusListener(thermalListener) }
        }
        super.onStop()
    }

    override fun onDestroy() {
        runCatching {
            torchCameraId?.let { cameraManager.setTorchMode(it, false) }
            settings.torchEnabled = false
            cameraManager.unregisterTorchCallback(torchCallback)
        }
        super.onDestroy()
    }

    private fun loadSettings() {
        hapticsEnabled = settings.hapticsEnabled
        keepScreenAwake = settings.keepScreenAwake
        startOnLaunch = settings.startOnLaunch
        backgroundFlashlightEnabled = settings.backgroundFlashlightEnabled
        strengthLevel = settings.strengthLevel
    }

    private fun loadFlashlight() {
        val flashlightInfo = flashlightController.loadFlashlight()
        val cameraId = flashlightInfo.cameraId

        torchCameraId = cameraId
        torchAvailable = cameraId != null

        if (cameraId == null) {
            statusMessage = "This device does not report a camera flashlight"
            return
        }

        maxStrengthLevel = flashlightInfo.maxStrengthLevel
        strengthLevel = settings.strengthLevel
            .coerceIn(1, maxStrengthLevel)
            .takeIf { settings.strengthLevel > 1 }
            ?: flashlightInfo.defaultStrengthLevel

        statusMessage = "Flashlight is ready"
    }

    private fun updateTorchPower(enabled: Boolean) {
        val cameraId = torchCameraId ?: return

        runCameraAction {
            flashlightController.setPower(cameraId, enabled, strengthLevel, maxStrengthLevel)

            torchEnabled = enabled
            settings.torchEnabled = enabled
            updateScreenAwakeFlag()
            maybeStartBackgroundFlashlight()
            if (!enabled) {
                stopBackgroundFlashlightService()
            }
            statusMessage = if (enabled) "Flashlight is on" else "Flashlight is off"
            refreshSafetyWarnings()
        }
    }

    private fun updateStrength(level: Int) {
        val nextLevel = level.coerceIn(1, maxStrengthLevel)
        strengthLevel = nextLevel
        settings.strengthLevel = nextLevel

        val cameraId = torchCameraId
        if (torchEnabled && cameraId != null && supportsStrengthControl()) {
            runCameraAction {
                flashlightController.setStrength(cameraId, nextLevel, maxStrengthLevel)
                statusMessage = "Brightness set to $nextLevel of $maxStrengthLevel"
                maybeStartBackgroundFlashlight()
            }
        }
        refreshSafetyWarnings()
    }

    private fun supportsStrengthControl(): Boolean {
        return flashlightController.supportsStrengthControl(maxStrengthLevel)
    }

    private fun updateHapticsEnabled(enabled: Boolean) {
        hapticsEnabled = enabled
        settings.hapticsEnabled = enabled
    }

    private fun updateKeepScreenAwake(enabled: Boolean) {
        keepScreenAwake = enabled
        settings.keepScreenAwake = enabled
        updateScreenAwakeFlag()
    }

    private fun updateStartOnLaunch(enabled: Boolean) {
        startOnLaunch = enabled
        settings.startOnLaunch = enabled
    }

    private fun updateBackgroundFlashlight(enabled: Boolean) {
        if (enabled == backgroundFlashlightEnabled) return

        if (enabled) {
            val permissions = requiredBackgroundPermissions()
            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                pendingBackgroundPermission.value = true
                requestBackgroundPermissions.launch(permissions.toTypedArray())
                return
            }
        }

        backgroundFlashlightEnabled = enabled
        settings.backgroundFlashlightEnabled = enabled
        if (!enabled) {
            stopBackgroundFlashlightService()
        } else {
            maybeStartBackgroundFlashlight()
        }
    }

    private fun updateScreenAwakeFlag() {
        if (keepScreenAwake && torchEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun maybeStartBackgroundFlashlight() {
        if (!backgroundFlashlightEnabled || !torchEnabled || !torchAvailable) return
        val cameraId = torchCameraId ?: return
        startForegroundFlashlightService(cameraId)
    }

    private fun stopBackgroundFlashlightService() {
        runCatching {
            startService(
                Intent(this, FlashlightForegroundService::class.java).apply {
                    action = FlashlightForegroundService.ACTION_STOP
                }
            )
        }
    }

    private fun startForegroundFlashlightService(cameraId: String) {
        val intent = Intent(this, FlashlightForegroundService::class.java).apply {
            putExtra(FlashlightForegroundService.EXTRA_ENABLED, true)
            putExtra(FlashlightForegroundService.EXTRA_STRENGTH_LEVEL, strengthLevel)
            putExtra(FlashlightForegroundService.EXTRA_CAMERA_ID, cameraId)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requiredBackgroundPermissions(): List<String> {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions
    }

    private fun refreshSafetyWarnings() {
        warningMessage = when {
            torchEnabled && !isCharging && batteryLevel <= 15 -> "Battery is low. The flashlight may drain it quickly."
            torchEnabled && thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> "The device is hot. Turn the flashlight off for a moment."
            else -> null
        }
    }

    private fun updateBatteryState(intent: Intent?) {
        if (intent == null) return
        batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, batteryLevel)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        isCharging = plugged != 0
        refreshSafetyWarnings()
    }

    private fun requestBackgroundModePermissionIfNeeded() {
        val permissions = requiredBackgroundPermissions()
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestBackgroundPermissions.launch(permissions.toTypedArray())
        }
    }

    private fun runCameraAction(action: () -> Unit) {
        try {
            action()
            torchAvailable = true
        } catch (exception: CameraAccessException) {
            torchAvailable = false
            statusMessage = exception.message ?: "Could not access the camera flashlight"
        } catch (exception: SecurityException) {
            torchAvailable = false
            statusMessage = "Camera access is blocked for this app"
        } catch (exception: IllegalArgumentException) {
            torchAvailable = false
            statusMessage = "This flashlight is not available"
        }
    }
}

@Composable
private fun FlashlightScreen(
    enabled: Boolean,
    available: Boolean,
    statusMessage: String,
    warningMessage: String?,
    strengthLevel: Int,
    maxStrengthLevel: Int,
    hapticsEnabled: Boolean,
    keepScreenAwake: Boolean,
    startOnLaunch: Boolean,
    backgroundFlashlightEnabled: Boolean,
    brightnessHelpVisible: Boolean,
    onToggle: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onStartOnLaunchChange: (Boolean) -> Unit,
    onBackgroundFlashlightChange: (Boolean) -> Unit,
    onBrightnessHelpClick: () -> Unit,
    onBrightnessHelpDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strengthSupported = maxStrengthLevel > 1
    val brightnessPercent = ((strengthLevel.toFloat() / maxStrengthLevel.coerceAtLeast(1)) * 100)
        .roundToInt()
    val haptic = LocalHapticFeedback.current
    var settingsVisible by remember { mutableStateOf(false) }
    val animatedBrightness by animateFloatAsState(
        targetValue = if (enabled) {
            if (strengthSupported) brightnessPercent / 100f else 1f
        } else {
            0.12f
        },
        animationSpec = tween(durationMillis = 420),
        label = "beamBrightness"
    )

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1D2530),
                        Color(0xFF111820),
                        Color(0xFF162414)
                    )
                )
            )
    ) {
        BeamBackdrop(
            enabled = enabled,
            brightnessProgress = animatedBrightness,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = if (enabled) "Flashlight on" else "Flashlight off",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = statusMessage,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .width(230.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                }
                Surface(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            settingsVisible = !settingsVisible
                        },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.30f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "SET",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }

            AnimatedVisibility(visible = warningMessage != null) {
                warningMessage?.let {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFB75F00).copy(alpha = 0.30f)
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(visible = settingsVisible) {
                    SettingsPanel(
                        hapticsEnabled = hapticsEnabled,
                        keepScreenAwake = keepScreenAwake,
                        startOnLaunch = startOnLaunch,
                        backgroundFlashlightEnabled = backgroundFlashlightEnabled,
                        onHapticsChange = onHapticsChange,
                        onKeepScreenAwakeChange = onKeepScreenAwakeChange,
                        onStartOnLaunchChange = onStartOnLaunchChange,
                        onBackgroundFlashlightChange = onBackgroundFlashlightChange
                    )
                }

                Spacer(modifier = Modifier.height(if (settingsVisible) 18.dp else 0.dp))

                if (strengthSupported) {
                    VerticalBrightnessSlider(
                        strengthLevel = strengthLevel,
                        maxStrengthLevel = maxStrengthLevel,
                        available = available,
                        hapticsEnabled = hapticsEnabled,
                        onStrengthChange = onStrengthChange
                    )
                    Text(
                        text = "$brightnessPercent%",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                } else {
                    UnsupportedBrightnessNotice(onBrightnessHelpClick)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FlashlightPowerButton(
                    enabled = enabled,
                    available = available,
                    onClick = {
                        if (hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onToggle(!enabled)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (available) "Tap to ${if (enabled) "turn off" else "turn on"}" else "Flashlight unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                    textAlign = TextAlign.Center
                )
            }
        }

        if (brightnessHelpVisible) {
            BrightnessHelpDialog(onDismiss = onBrightnessHelpDismiss)
        }
    }
}

@Composable
private fun BeamBackdrop(
    enabled: Boolean,
    brightnessProgress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val originY = height * 0.52f
        val intensity = brightnessProgress.coerceIn(0.08f, 1f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.18f * intensity),
                    Color.Transparent
                ),
                center = androidx.compose.ui.geometry.Offset(width / 2f, originY - height * 0.25f),
                radius = width * 0.78f
            )
        )

        val beam = Path().apply {
            moveTo(width * 0.29f, originY)
            lineTo(width * 0.08f, height * 0.10f)
            quadraticTo(width / 2f, height * 0.00f, width * 0.92f, height * 0.10f)
            lineTo(width * 0.71f, originY)
            close()
        }
        drawPath(
            path = beam,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.08f * intensity),
                    Color.White.copy(alpha = 0.34f * intensity),
                    Color.White.copy(alpha = 0.07f * intensity)
                ),
                startY = 0f,
                endY = originY
            )
        )

        drawLine(
            color = Color.White.copy(alpha = 0.38f * intensity),
            start = androidx.compose.ui.geometry.Offset(width * 0.30f, originY),
            end = androidx.compose.ui.geometry.Offset(width * 0.70f, originY),
            strokeWidth = 2.5f
        )

        val headWidth = width * 0.46f
        val headHeight = height * 0.045f
        val headLeft = (width - headWidth) / 2f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF3A4248).copy(alpha = 0.88f),
                    Color(0xFF151A1E).copy(alpha = 0.95f)
                ),
                startY = originY,
                endY = originY + headHeight
            ),
            topLeft = androidx.compose.ui.geometry.Offset(headLeft, originY - headHeight * 0.18f),
            size = androidx.compose.ui.geometry.Size(headWidth, headHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
        )

        val handleWidth = width * 0.25f
        val handleHeight = height * 0.24f
        val handleLeft = (width - handleWidth) / 2f
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF101518).copy(alpha = 0.84f),
                    Color(0xFF2B3338).copy(alpha = 0.74f),
                    Color(0xFF0D1114).copy(alpha = 0.86f)
                )
            ),
            topLeft = androidx.compose.ui.geometry.Offset(handleLeft, originY + headHeight * 0.55f),
            size = androidx.compose.ui.geometry.Size(handleWidth, handleHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
        )

        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            topLeft = androidx.compose.ui.geometry.Offset(handleLeft + handleWidth * 0.12f, originY + headHeight),
            size = androidx.compose.ui.geometry.Size(handleWidth * 0.18f, handleHeight * 0.78f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )
    }
}

@Composable
private fun VerticalBrightnessSlider(
    strengthLevel: Int,
    maxStrengthLevel: Int,
    available: Boolean,
    hapticsEnabled: Boolean,
    onStrengthChange: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .height(196.dp)
            .width(52.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(154.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.20f))
        )
        Slider(
            value = strengthLevel.toFloat(),
            onValueChange = {
                if (hapticsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onStrengthChange(it.roundToInt())
            },
            valueRange = 1f..maxStrengthLevel.toFloat(),
            steps = (maxStrengthLevel - 2).coerceAtLeast(0),
            enabled = available,
            modifier = Modifier
                .width(176.dp)
                .graphicsLayer(rotationZ = -90f)
        )
    }
}

@Composable
private fun FlashlightPowerButton(
    enabled: Boolean,
    available: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1.08f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "powerButtonScale"
    )
    Surface(
        modifier = Modifier
            .size(78.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CircleShape)
            .clickable(enabled = available, onClick = onClick),
        shape = CircleShape,
        color = if (enabled) Color.White else Color.White.copy(alpha = 0.88f),
        shadowElevation = if (enabled) 14.dp else 6.dp
    ) {
        Canvas(modifier = Modifier.padding(22.dp)) {
            val iconColor = if (available) Color(0xFF1A1A1A) else Color(0xFF7D7D7D)
            val centerX = size.width / 2f
            drawLine(
                color = iconColor,
                start = androidx.compose.ui.geometry.Offset(centerX - 7f, 2f),
                end = androidx.compose.ui.geometry.Offset(centerX + 7f, 2f),
                strokeWidth = 5f
            )
            drawLine(
                color = iconColor,
                start = androidx.compose.ui.geometry.Offset(centerX - 10f, 9f),
                end = androidx.compose.ui.geometry.Offset(centerX + 10f, 9f),
                strokeWidth = 4f
            )
            drawRoundRect(
                color = iconColor,
                topLeft = androidx.compose.ui.geometry.Offset(centerX - 8f, 13f),
                size = androidx.compose.ui.geometry.Size(16f, 22f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )
            drawLine(
                color = iconColor,
                start = androidx.compose.ui.geometry.Offset(centerX - 12f, -8f),
                end = androidx.compose.ui.geometry.Offset(centerX - 15f, -16f),
                strokeWidth = 3f
            )
            drawLine(
                color = iconColor,
                start = androidx.compose.ui.geometry.Offset(centerX, -8f),
                end = androidx.compose.ui.geometry.Offset(centerX, -18f),
                strokeWidth = 3f
            )
            drawLine(
                color = iconColor,
                start = androidx.compose.ui.geometry.Offset(centerX + 12f, -8f),
                end = androidx.compose.ui.geometry.Offset(centerX + 15f, -16f),
                strokeWidth = 3f
            )
        }
    }
}

@Composable
private fun UnsupportedBrightnessNotice(onLearnMore: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.28f)
    ) {
        Column(
            modifier = Modifier
                .width(244.dp)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Fixed brightness",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Real torch brightness needs Android 13+ and compatible camera hardware.",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.68f),
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onLearnMore) {
                Text(text = "Why not here?")
            }
        }
    }
}

@Composable
private fun BrightnessHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Brightness support") },
        text = {
            Text(
                text = "Android only exposes real flashlight brightness control on Android 13 and newer, and only when the device camera hardware advertises multiple torch strength levels. Older versions can still switch the flashlight on and off, but the system does not offer a public brightness API."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "OK")
            }
        }
    )
}

@Composable
private fun SettingsPanel(
    hapticsEnabled: Boolean,
    keepScreenAwake: Boolean,
    startOnLaunch: Boolean,
    backgroundFlashlightEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onStartOnLaunchChange: (Boolean) -> Unit,
    onBackgroundFlashlightChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.width(300.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.36f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingSwitchRow(
                title = "Haptic feedback",
                subtitle = "Vibrate on controls",
                checked = hapticsEnabled,
                onCheckedChange = onHapticsChange
            )
            SettingSwitchRow(
                title = "Keep screen awake",
                subtitle = "Only while flashlight is on",
                checked = keepScreenAwake,
                onCheckedChange = onKeepScreenAwakeChange
            )
            SettingSwitchRow(
                title = "Start on launch",
                subtitle = "Turn on when app opens",
                checked = startOnLaunch,
                onCheckedChange = onStartOnLaunchChange
            )
            SettingSwitchRow(
                title = "Background flashlight",
                subtitle = "Keep a foreground service active",
                checked = backgroundFlashlightEnabled,
                onCheckedChange = onBackgroundFlashlightChange
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FlashlightScreenPreview() {
    MyFlashlightTheme {
        FlashlightScreen(
            enabled = true,
            available = true,
            statusMessage = "Flashlight is on",
            warningMessage = null,
            strengthLevel = 3,
            maxStrengthLevel = 5,
            hapticsEnabled = true,
            keepScreenAwake = true,
            startOnLaunch = false,
            backgroundFlashlightEnabled = false,
            brightnessHelpVisible = false,
            onToggle = {},
            onStrengthChange = {},
            onHapticsChange = {},
            onKeepScreenAwakeChange = {},
            onStartOnLaunchChange = {},
            onBackgroundFlashlightChange = {},
            onBrightnessHelpClick = {},
            onBrightnessHelpDismiss = {}
        )
    }
}
