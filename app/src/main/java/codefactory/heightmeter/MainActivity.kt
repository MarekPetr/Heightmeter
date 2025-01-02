package codefactory.heightmeter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.fonts.FontStyle
import android.hardware.Sensor
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import codefactory.heightmeter.ui.theme.Orange
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.acos
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.math.tan


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        setContent {
            Surface {
                val context = LocalContext.current
                val isPermissionGranted = remember {
                    mutableStateOf<Boolean?>(
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val launcher =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                        isPermissionGranted.value = isGranted
                    }
                val sensorAvailable = isSensorAvailable(Sensor.TYPE_ACCELEROMETER)
                when (isPermissionGranted.value) {
                    true ->
                        if (!sensorAvailable) {
                            Box(modifier = Modifier.fillMaxSize().background(color = Color.DarkGray), contentAlignment = Alignment.Center) {
                                Text(text="Accelerometer not found!", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 27.sp)
                            }
                        }
                        else {
                            Box {
                                CameraPreview(cameraProviderFuture)
                                ControlsLayout()
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Crosshair(color = Orange)
                                }
                            }
                        }
                        else -> {
                            LaunchedEffect(Unit) {
                                launcher.launch(Manifest.permission.CAMERA)
                            }
                        }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(cameraProviderFuture: ListenableFuture<ProcessCameraProvider>) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FILL_START
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                post {
                    cameraProviderFuture.addListener(Runnable {
                        val cameraProvider = cameraProviderFuture.get()
                        bindPreview(
                            cameraProvider,
                            lifecycleOwner,
                            this,
                        )
                    }, ContextCompat.getMainExecutor(context))
                }
            }
        }
    )
}
fun bindPreview(cameraProvider: ProcessCameraProvider,
                lifecycleOwner: LifecycleOwner,
                previewView: PreviewView
) {
    val preview = Preview.Builder().build()

    // Connect the preview use case to the previewView
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    cameraProvider.unbindAll() // Unbind use cases before rebinding
    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
    preview.setSurfaceProvider(previewView.surfaceProvider)
}

@Composable
fun CustomButton(
    onClick: () -> Unit,
    label: String
) {
    Button (
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Gray.copy(alpha = 0.6f),
            contentColor = Color.Black,
            disabledContentColor = Color.Black,
            disabledContainerColor = Color.Gray.copy(alpha = 0.4f)
        ),
        modifier = Modifier.width(135.dp),
        border = BorderStroke(width = 1.dp, color = Color.Black)
    ) {
        Text(label)
    }
}


@Composable
fun InputField(
    onValueChange: (String) -> Unit,
    value: String,
    label: String,
) {
    val onChange : (String) -> Unit = { it ->
        val formatted = DecimalFormatter().cleanup(it)
        onValueChange(formatted)
    }
    val focusManager = LocalFocusManager.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(135.dp)
    ) {
        TextField(
            value = value,
            onValueChange = onChange,
            maxLines = 1,
            label = { Text(text = label, color = Orange)},
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions (
                onDone = {
                    focusManager.clearFocus()
                },
            ),
            colors = TextFieldDefaults.colors(
                disabledTextColor = Color.Transparent,
                focusedTextColor = Orange,
                unfocusedTextColor = Orange,
                unfocusedLabelColor = Orange,
                cursorColor = Orange,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedIndicatorColor = Orange,
                unfocusedIndicatorColor = Orange,
                disabledIndicatorColor = Orange,
            ),
            modifier = Modifier.width(125.dp)
        )
    }
}

@Composable
fun Measurement(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(20.dp).width(100.dp)
    ) {
        Text(text = label, color = Orange, fontSize = 25.sp)
        Text(text = value, color = Orange, fontSize = 25.sp)
    }
}

@Composable
fun Crosshair(modifier: Modifier = Modifier, color: Color = Color.Black, size: Dp = 100.dp, strokeWidth: Float = 4f) {
    Canvas(modifier = modifier.size(size)) {
        val canvasWidth = size.toPx()
        val canvasHeight = size.toPx()
        val centerX = canvasWidth / 2
        val centerY = canvasHeight / 2

        // Draw horizontal line
        drawLine(
            color = color,
            start = Offset(x = 0f, y = centerY),
            end = Offset(x = canvasWidth, y = centerY),
            strokeWidth = strokeWidth
        )

        // Draw vertical line
        drawLine(
            color = color,
            start = Offset(x = centerX, y = 0f),
            end = Offset(x = centerX, y = canvasHeight),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
fun ControlsLayout(
) {
    val lensHeight = rememberSaveable { mutableStateOf("") }
    val distance = rememberSaveable { mutableStateOf("") }
    val gravity by rememberSensorValueAsState(
        type = Sensor.TYPE_ACCELEROMETER,
        transformSensorEvent = { event -> event?.values ?: FloatArray(0) },
    )

    fun getHeight(): String {
        if (gravity.isEmpty()) {
            return ""
        }
        val lensHeightValue = try {
            lensHeight.value.toDouble()
        }
        catch (e: Throwable) {
            return ""
        }

        val distanceValue = try {
            distance.value.toDouble()
        }
        catch (e: Throwable) {
            return ""
        }

        try {
            val angle = computeAngle(gravity)
            val height = computeHeight(angle, lensHeightValue, distanceValue)
            return formatHeight(height)
        }
        catch (e: Throwable) {
            return ""
        }
    }
    fun calculateDistance() {
        val angle = computeAngle(gravity)
        val lensHeightValue = try {
            lensHeight.value.toDouble()
        }
        catch (e: Throwable) {
            return
        }
        val computedDistance = computeDistance(angle, lensHeightValue)
        val rounded = roundToDecimals(computedDistance)
        distance.value = rounded.toString()
    }

    val height by remember(gravity, lensHeight, distance) { derivedStateOf { getHeight() }}
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                onClick = {
                    focusManager.clearFocus()
                },
                interactionSource = interactionSource,
                indication = null
            )
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
        InputField(
            label = "Enter your height",
            onValueChange = { lensHeight.value = it},
            value = lensHeight.value,
        )
        Measurement(label="Height", value = height)
        Column {
            InputField(
                label = "Enter Distance",
                onValueChange = { distance.value = it },
                value = distance.value,
            )
            CustomButton(label = "Click base", onClick = {
                focusManager.clearFocus()
                calculateDistance()
            })
        }
    }
}

fun formatHeight(height: Double): String {
    val rounded = roundToDecimals(height)
    val limit = 10000
    if (rounded <= -limit) {
        return "min"
    }
    if (rounded >= limit) {
        return "max"
    }
    return rounded.toString()
}

private fun roundToDecimals(number: Double): Double {
    return round(number * 100) / 100
}

private fun computeHeight(angle: Double, lensHeight: Double, distance: Double): Double {
    return (tan(angle) * distance) + lensHeight
}

private fun computeDistance(angle: Double, lensHeight: Double): Double {
    return lensHeight / (-(tan(angle)))
}

private fun computeAngle(gravity: FloatArray): Double {
    val normOfG = sqrt(
        gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]
    )

    val arcCos = acos(gravity[2] / normOfG).toDouble()
    val angle = Math.toDegrees(arcCos) - 90.0f
    return Math.toRadians(angle)
}