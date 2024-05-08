package ru.edventy.visionride

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.widget.ImageView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.Executor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import org.tensorflow.lite.task.gms.vision.TfLiteVision
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun MainScreen(){
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "camera"){
        composable("camera") { CameraPage(navController) }
        composable("settings") { SettingsPage(navController) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPage(nav: NavHostController){
    val context = LocalContext.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val detector = remember { TFObjectDetector() }

    LaunchedEffect(null) {
        context.startDetector(detector)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text("IRIS")
                },
                actions = {
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ){ innerPadding ->
        CameraAI(cameraExecutor, detector, Modifier.padding(innerPadding))
    }
}

@Composable
fun SettingsPage(nav: NavHostController){
    Text("Settings", modifier = Modifier.fillMaxSize())
}

enum class HardwareAccelerator {
    CPU,
    GPU,
    NNAPIDevice,
    UNINITIALIZED
}

suspend fun Context.startDetector(detector: TFObjectDetector): HardwareAccelerator = suspendCoroutine { continuation ->
    val options = TfLiteInitializationOptions.builder()
        .setEnableGpuDelegateSupport(true)
        .build()

    TfLiteVision.initialize(this, options).addOnSuccessListener {
        println("GPU goes brr")
        detector.initialize(this, detector.options)
        continuation.resume(HardwareAccelerator.GPU)
    }.addOnFailureListener {
        // Called if the GPU Delegate is not supported on the device
        TfLiteVision.initialize(this).addOnSuccessListener {
            println("CPU goes brr")
            detector.initialize(this, detector.options)
            continuation.resume(HardwareAccelerator.CPU)
        }.addOnFailureListener{
            continuation.resume(HardwareAccelerator.UNINITIALIZED)
            println("TfLiteVision failed to initialize: "
                    + it.message)
        }
    }
}

suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { cameraProvider ->
        cameraProvider.addListener({
            continuation.resume(cameraProvider.get())
        }, ContextCompat.getMainExecutor(this))
    }
}

@Composable
fun CameraAI(
    cameraExecutor: Executor,
    detector: TFObjectDetector,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }

    var overlayBitmap by remember{ mutableStateOf(Bitmap.createBitmap(
        1, 1,
        Bitmap.Config.ARGB_8888
    ).asImageBitmap()) }

    LaunchedEffect(cameraSelector) {
        val cameraProvider = context.getCameraProvider()

        val imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetRotation(previewView.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) {
                        image -> detector.onImage(image)
                    }
                }

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, imageAnalyzer)

        }
        catch(exc: Exception) {
            Log.e("CameraComposable", "Use case binding failed", exc)
        }

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

            overlayBitmap = mutableBitmap.asImageBitmap()
        }

        preview.setSurfaceProvider(previewView.surfaceProvider)
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
    }

    AndroidView({ previewView }, modifier = Modifier
        .fillMaxSize()
        .then(modifier))
    Image(overlayBitmap, "overlay", modifier = Modifier
        .fillMaxSize()
        .then(modifier))
}