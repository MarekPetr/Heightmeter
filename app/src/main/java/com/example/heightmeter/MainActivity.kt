package com.example.heightmeter

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.heightmeter.ui.theme.Orange
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.math.tan


class MainActivity : ComponentActivity(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gravity = FloatArray(0)
    private var geomagnetic = FloatArray(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSensors();
        initListeners();
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

                when (isPermissionGranted.value) {
                    true -> Box {
                        CameraPreview(cameraProviderFuture)
                        ControlsLayout()
                    }
                    else -> TextButton(
                        onClick = { launcher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = "Tap to grant camera permission",
                            color = androidx.compose.ui.graphics.Color.Black,
                            fontSize = 20.sp
                        )
                    }
                }
            }
        }
    }

    private fun setSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    private fun initListeners() {
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager?.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event!!.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }

    private fun computedAngle(): Double {
        if (gravity.isEmpty() || geomagnetic.isEmpty()) {
            throw Error("gravity or geomagnetic not initialized")
        }

        val R = FloatArray(9)
        val I = FloatArray(9)

        val gotRotationMatrix = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)
        if (!gotRotationMatrix) {
            throw Error("No rotation Matrix")
        }

        val orientation = FloatArray(3)
        SensorManager.getOrientation(R, orientation)

        val inclineGravity = gravity.clone()
        val normOfG = sqrt(
            inclineGravity[0] * inclineGravity[0] + inclineGravity[1] * inclineGravity[1] + inclineGravity[2] * inclineGravity[2]
        ) as Float

        // Normalize the accelerometer vector
        inclineGravity[2] = (inclineGravity[2] / normOfG)

        val arcCos = acos(inclineGravity[2]).toDouble()
        val angle = Math.toDegrees(arcCos) - 90.0f
        return Math.toRadians(angle)
    }

    private fun computeHeight(angle: Double, lensHeight: Double, distance: Double): Double {
        val height = tan(angle * distance) + lensHeight
        return Math.round(height * 10.0) * 0.1
    }

    private fun getHeightString(height: Double): String {
        if (height < -99999.9f) {
            return "min"
        }
        if (height > 999999.9f) {
            return "max"
        }
        return height.toString()
    }

    private fun computeDistance(lensHeight: Double, distance: Double): Double {
        val newDistance = lensHeight / (-(tan(computedAngle())))
        return Math.round(newDistance * 10.0) * 0.1
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
    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
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
    label: String
) {
    val textField = FocusRequester()
    val text = remember { mutableStateOf("") }
    val onChange : (String) -> Unit = { it ->
        text.value = it
        onValueChange(it)
    }
    val onConfirm: () -> Unit = {
        textField.freeFocus()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(135.dp)
    ) {
        TextField(
            value = text.value,
            onValueChange = onChange,
            maxLines = 1,
            label = { Text(text = label, color = Orange)},
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
            modifier = Modifier.width(125.dp).focusRequester(textField)
        )
        CustomButton(onClick = onConfirm, label = "OK")
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
fun ControlsLayout(
) {
    val height = remember { mutableStateOf("") }
    val onChange : (String) -> Unit = { it ->
        height.value = it
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
        InputField(label = "Enter your height", onValueChange = onChange)
        Measurement(label="Height", value = height.value)
        Column {
            InputField(label = "Enter Distance", onValueChange = {})
            CustomButton(onClick = {}, label = "Measure")
        }

    }
}