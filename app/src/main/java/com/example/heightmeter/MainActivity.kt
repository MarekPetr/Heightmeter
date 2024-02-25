package com.example.heightmeter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.widget.Button
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import android.widget.Toast

class MainActivity : ComponentActivity() {
    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        activityResultLauncher.launch(Manifest.permission.CAMERA)
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission())
        { isGranted ->
            // Handle Permission granted/rejected
            if (isGranted) {
                initializeCamera()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG)
                    .show()
            }
        }

    private fun initializeCamera() {
        // Permission already granted, proceed with camera operations
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(cameraProvider : ProcessCameraProvider) {
        val previewView: PreviewView = findViewById(R.id.previewView)
        val preview = Preview.Builder().build()

        // Connect the preview use case to the previewView
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider.unbindAll() // Unbind use cases before rebinding
        val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview)
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }
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