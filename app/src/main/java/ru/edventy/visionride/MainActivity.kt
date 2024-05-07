package ru.edventy.visionride

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import org.tensorflow.lite.task.gms.vision.TfLiteVision
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.FrameLayout.LayoutParams


class MainActivity : ComponentActivity(){
    private lateinit var viewFinder: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var detector = TFObjectDetector()
    private lateinit var overlay: ImageView

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true

            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity_layout)

        viewFinder = findViewById(R.id.viewFinder)
        viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER

        overlay = findViewById(R.id.camera_overlay)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        detector.listener = { objects, image, rotation ->
            // val mutableBitmap: Bitmap = image.copy(Bitmap.Config.ARGB_8888, true)
            val mutableBitmap =
                if (rotation % 180 == 0)
                    Bitmap.createBitmap(
                        image.width, image.height,
                        Bitmap.Config.ARGB_8888
                    )
                else
                    Bitmap.createBitmap(
                        image.height, image.width,
                        Bitmap.Config.ARGB_8888
                    )

            val paintPupil = Paint()
            paintPupil.color = Color.BLUE
            paintPupil.style = Paint.Style.STROKE
            paintPupil.strokeWidth = 5f

            val paintClosedEye = Paint()
            paintClosedEye.color = Color.RED
            paintClosedEye.style = Paint.Style.STROKE
            paintClosedEye.strokeWidth = 5f

            val paintOpenedEye = Paint()
            paintOpenedEye.color = Color.GREEN
            paintOpenedEye.style = Paint.Style.STROKE
            paintOpenedEye.strokeWidth = 5f

            val canvas = Canvas(mutableBitmap)

            objects.map {
                if(it.categories[0].score > 0.5){
                    val x1 = it.boundingBox.left.toInt()
                    val x2 = it.boundingBox.right.toInt()
                    val y1 = it.boundingBox.top.toInt()
                    val y2 = it.boundingBox.bottom.toInt()

                    val rect = Rect(x1, y1, x2, y2)

                    canvas.drawRect(rect, when(it.categories[0].index) {
                        0 -> paintOpenedEye
                        1 -> paintClosedEye
                        else -> paintPupil
                    })
                }
            }

            runOnUiThread {
                overlay.setImageBitmap(mutableBitmap)
                overlay.layoutParams = LayoutParams(overlay.width, viewFinder.height)
            }
        }
    }

    private fun startCamera(){

        val options = TfLiteInitializationOptions.builder()
            .setEnableGpuDelegateSupport(true)
            .build()

        TfLiteVision.initialize(this, options).addOnSuccessListener {
            println("GPU goes brr")
            detector.initialize(this, detector.options)
        }.addOnFailureListener {
            // Called if the GPU Delegate is not supported on the device
            TfLiteVision.initialize(this).addOnSuccessListener {
                println("CPU goes brr")
                detector.initialize(this, detector.options)
            }.addOnFailureListener{
                println("TfLiteVision failed to initialize: "
                        + it.message)
            }
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            val imageAnalyzer =
                ImageAnalysis.Builder()
                    .setTargetRotation(viewFinder.display.rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) {
                            image -> detector.onImage(image)
                        }
                    }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "VisionRide"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()
    }
}