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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
    detector: TFObjectDetector
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

    AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
    Image(overlayBitmap, "overlay", modifier = Modifier.fillMaxSize())
}