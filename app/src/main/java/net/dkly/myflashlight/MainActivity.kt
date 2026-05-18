package net.dkly.myflashlight

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.dkly.myflashlight.ui.theme.MyFlashlightTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var cameraManager: CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var torchCameraId: String? by mutableStateOf(null)
    private var torchEnabled by mutableStateOf(false)
    private var torchAvailable by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Looking for a flashlight...")
    private var strengthLevel by mutableIntStateOf(1)
    private var maxStrengthLevel by mutableIntStateOf(1)

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                torchEnabled = enabled
                torchAvailable = true
                statusMessage = if (enabled) "Flashlight is on" else "Flashlight is off"
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) {
                torchEnabled = false
                torchAvailable = false
                statusMessage = "Flashlight is temporarily unavailable"
            }
        }

        override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
            if (cameraId == torchCameraId && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                strengthLevel = newStrengthLevel.coerceIn(1, maxStrengthLevel)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        loadFlashlight()
        cameraManager.registerTorchCallback(torchCallback, mainHandler)

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
                        onToggle = ::updateTorchPower,
                        onStrengthChange = ::updateStrength,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching {
            torchCameraId?.let { cameraManager.setTorchMode(it, false) }
            cameraManager.unregisterTorchCallback(torchCallback)
        }
        super.onDestroy()
    }

    private fun loadFlashlight() {
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

        torchCameraId = cameraId
        torchAvailable = cameraId != null

        if (cameraId == null) {
            statusMessage = "This device does not report a camera flashlight"
            return
        }

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            maxStrengthLevel = characteristics
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                ?.coerceAtLeast(1) ?: 1
            strengthLevel = characteristics
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL)
                ?.coerceIn(1, maxStrengthLevel) ?: 1
        }

        statusMessage = "Flashlight is ready"
    }

    private fun updateTorchPower(enabled: Boolean) {
        val cameraId = torchCameraId ?: return

        runCameraAction {
            if (enabled && supportsStrengthControl()) {
                cameraManager.turnOnTorchWithStrengthLevel(cameraId, strengthLevel)
            } else {
                cameraManager.setTorchMode(cameraId, enabled)
            }

            torchEnabled = enabled
            statusMessage = if (enabled) "Flashlight is on" else "Flashlight is off"
        }
    }

    private fun updateStrength(level: Int) {
        val nextLevel = level.coerceIn(1, maxStrengthLevel)
        strengthLevel = nextLevel

        val cameraId = torchCameraId
        if (torchEnabled && cameraId != null && supportsStrengthControl()) {
            runCameraAction {
                cameraManager.turnOnTorchWithStrengthLevel(cameraId, nextLevel)
                statusMessage = "Brightness set to $nextLevel of $maxStrengthLevel"
            }
        }
    }

    private fun supportsStrengthControl(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxStrengthLevel > 1
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
    strengthLevel: Int,
    maxStrengthLevel: Int,
    onToggle: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val strengthSupported = maxStrengthLevel > 1
    val brightnessPercent = ((strengthLevel.toFloat() / maxStrengthLevel.coerceAtLeast(1)) * 100)
        .roundToInt()

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
            brightnessPercent = if (strengthSupported) brightnessPercent else 100,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (enabled) "Flashlight on" else "Flashlight off",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = statusMessage,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.68f),
                    textAlign = TextAlign.Center
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (strengthSupported) {
                    VerticalBrightnessSlider(
                        strengthLevel = strengthLevel,
                        maxStrengthLevel = maxStrengthLevel,
                        available = available,
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
                    UnsupportedBrightnessNotice()
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FlashlightPowerButton(
                    enabled = enabled,
                    available = available,
                    onClick = { onToggle(!enabled) }
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
    }
}

@Composable
private fun BeamBackdrop(
    enabled: Boolean,
    brightnessPercent: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val originY = height * 0.52f
        val intensity = if (enabled) brightnessPercent.coerceIn(18, 100) / 100f else 0.12f

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
    onStrengthChange: (Int) -> Unit
) {
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
            onValueChange = { onStrengthChange(it.roundToInt()) },
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
    Surface(
        modifier = Modifier
            .size(78.dp)
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
private fun UnsupportedBrightnessNotice() {
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
        }
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
            strengthLevel = 3,
            maxStrengthLevel = 5,
            onToggle = {},
            onStrengthChange = {}
        )
    }
}
