package com.example.heightmeter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp

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

                when (isPermissionGranted.value) {
                    true -> Box {
                        CameraPreview(cameraProviderFuture)
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

class Sensors: SensorEventListener {
    private var gravity = FloatArray(0)
    private var geomagnetic = FloatArray(0)

    override fun onSensorChanged(event: SensorEvent?) {
        if (event!!.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values
            getHeight()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        TODO("Not yet implemented")
    }

    fun getHeight() {
    }

}