package ru.edventy.visionride

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.gms.vision.detector.Detection
import org.tensorflow.lite.task.gms.vision.detector.ObjectDetector

class NotInitialized(message: String): Exception(message)

/**
 * Object detector.
 */
class TFObjectDetector {
    private lateinit var objectDetector: ObjectDetector

    var initialized = false
        private set

    var cameraBitmap: Bitmap? = null
        private set

    var objects: List<Detection>? = null
        private set

    var imageRotation: Int = 0
        private set

    var listener: ((List<Detection>, Bitmap, Int)->Unit)? = null

    /**
     * Default TFLite ObjectDetector options.
     */
    val options: ObjectDetector.ObjectDetectorOptions
        get() = ObjectDetector.ObjectDetectorOptions.builder()
            .setScoreThreshold(0.5f)
            .setMaxResults(4)
            .setBaseOptions(
                BaseOptions.builder()
                    .useNnapi()
                    .build()
            ).build()

    private fun throwErrorIfNotInitialized(){
        if(!initialized){
            throw NotInitialized("You must call initialize() method before using any NN operations!")
        }
    }

    /**
     * Initialize TFLite ObjectDetector. Must be called before any NN operations.
     */
    fun initialize(
        context: Context,
        options: ObjectDetector.ObjectDetectorOptions,
        modelPath: String = "model1.tflite"
    ){
        // raises IOException
        objectDetector =
            ObjectDetector.createFromFileAndOptions(
                context, modelPath, options)
        initialized = true
    }

    /**
     * Detect object on image.
     */
    fun detectObjects(image: ImageProxy): List<Detection> {
        if (cameraBitmap == null) {
            cameraBitmap = Bitmap.createBitmap(
                image.width, image.height,
                Bitmap.Config.ARGB_8888
            )
        }

        //Copy out RGB bits to the shared bitmap buffer
        image.use {
            cameraBitmap?.copyPixelsFromBuffer(image.planes[0].buffer)
        }

        imageRotation = image.imageInfo.rotationDegrees
        val imageProcessor = ImageProcessor.Builder().add(Rot90Op(-imageRotation / 90)).build()

        return detectObjects(imageProcessor.process(TensorImage.fromBitmap(cameraBitmap)))
    }

    /**
     * Detect object on image.
     */
    fun detectObjects(image: TensorImage): List<Detection> {
        throwErrorIfNotInitialized()
        return objectDetector.detect(image)
    }

    /**
     * Image (from camera or other stream) callback.
     */
    fun onImage(image: ImageProxy){
        if(!initialized){
            Log.w("NN", "You must call initialize() method before using detection. onImage fun will do nothing.")
            return
        }
        val objBuf = detectObjects(image)

        objects = objBuf
        listener?.invoke(objBuf, cameraBitmap!!, imageRotation)
    }
}