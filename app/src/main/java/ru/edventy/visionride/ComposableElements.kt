package ru.edventy.visionride

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.Executor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.firebase.ml.modeldownloader.CustomModel
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.DownloadType
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import com.jamal.composeprefs3.ui.PrefsScreen
import com.jamal.composeprefs3.ui.prefs.CheckBoxPref
import com.jamal.composeprefs3.ui.prefs.EditTextPref
import org.tensorflow.lite.task.gms.vision.TfLiteVision
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

val BLOCK_CLOUD_MODEL_KEY = booleanPreferencesKey("blockCloudModel")

@Composable
fun MainScreen(dataStore: DataStore<Preferences>){
    val navController = rememberNavController()
    var modelFile by remember { mutableStateOf<File?>(null) }

    val conditions = CustomModelDownloadConditions.Builder()
//        .requireWifi()  // Also possible: .requireCharging() and .requireDeviceIdle()
        .build()
    FirebaseModelDownloader.getInstance()
        .getModel("Eye-Gaze-Detector", DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND,
            conditions)
        .addOnSuccessListener { model: CustomModel? ->
            // Download complete. Depending on your app, you could enable the ML
            // feature, or switch from the local model to the remote model, etc.

            // The CustomModel object contains the local path of the model file,
            // which you can use to instantiate a TensorFlow Lite interpreter.

            if(model != null){
                modelFile = model.file

                Log.i("Firebase NN", "Model downloaded: " + modelFile?.name)
            }
        }
        .addOnFailureListener{
            Log.e("Firebase NN", "Failed to download model.")
        }

    NavHost(navController = navController, startDestination = "loading"){
        composable("loading") { LoadingPage(navController) }
        composable("error") { ErrorPage() }
        composable("camera") { CameraPage(navController, dataStore, modelFile) }
        composable("settings") { SettingsPage(navController, dataStore) }
    }
}

@Composable
fun LoadingPage(nav: NavHostController) {
    val context = LocalContext.current

    val options = TfLiteInitializationOptions.builder()
        .setEnableGpuDelegateSupport(true)
        .build()

    LaunchedEffect(null){
        TfLiteVision.initialize(context, options).addOnSuccessListener {
            println("GPU goes brr")
            nav.navigate("camera")
        }.addOnFailureListener {
            // Called if the GPU Delegate is not supported on the device
            TfLiteVision.initialize(context).addOnSuccessListener {
                println("CPU goes brr")
                nav.navigate("camera")
            }.addOnFailureListener{
                println("TfLiteVision failed to initialize: "
                        + it.message)
                nav.navigate("error")
            }
        }
    }

    Scaffold { innerPadding ->
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.logogram),
            contentDescription = "Rozen logo",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(50.dp)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun ErrorPage(){
    Column (modifier = Modifier.padding(10.dp)) {
        Text("Oops", fontSize = 30.sp)
        Text("Your device does not support Tensorflow Lite.")
        Text("Application is unavailable.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPage(nav: NavHostController, dataStore: DataStore<Preferences>, modelFile: File? = null){
    val context = LocalContext.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val detector = remember { TFObjectDetector() }
    var cameraSelector by remember {
        mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA)
    }

    LaunchedEffect(modelFile) {
        dataStore.data.collect { data ->
            val blockCloudModel = data[BLOCK_CLOUD_MODEL_KEY]

            if(modelFile == null || blockCloudModel == true){
                Log.i("Detector", "Using local model.")
                detector.initialize(context, detector.options)
            }
            else{
                Log.i("Detector", "Using cloud model.")
                detector.initialize(detector.options, modelFile)
            }
        }

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
                    IconButton(onClick = {
                        cameraSelector =
                            if(cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            else
                                CameraSelector.DEFAULT_BACK_CAMERA
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Change camera"
                        )
                    }
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
        CameraAI(cameraExecutor, detector, modifier = Modifier.padding(innerPadding), cameraSelector = cameraSelector)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsPage(nav: NavHostController, dataStore: DataStore<Preferences>){
    Scaffold (
        topBar = {
            TopAppBar(
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text("Settings")
                },
                actions = {
                    IconButton(onClick = { nav.navigate("camera") }) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Home"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .padding(innerPadding)
            .fillMaxWidth()){
            PrefsScreen(dataStore) {
                prefsGroup("Googles"){
                    prefsItem { EditTextPref(
                        key = "googlesIP",
                        title = "Googles IP",
                        summary = "Camera feed HTTP server",
                        dialogTitle = "Googles IP",
                        dialogMessage = "Put VisionRide Googles IP here:"
                    ) }
                }
                prefsGroup("Wheelchair"){
                    prefsItem { EditTextPref(
                        key = "wheelchairIP",
                        title = "Wheelchair IP",
                        summary = "ROS server address",
                        dialogTitle = "Wheelchair IP",
                        dialogMessage = "Put Wheelchair server IP here:"
                    ) }
                }
                prefsGroup("AI & Models"){
                    prefsItem { CheckBoxPref(
                        key = BLOCK_CLOUD_MODEL_KEY.name,
                        title = "Use local AI model",
                        summary = "Downgrade to builtin model and prevent cloud download",
                    ) }
                }
            }
        }
    }
}

@Composable
fun CameraAI(
    cameraExecutor: Executor,
    detector: TFObjectDetector,
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }

    var overlayBitmap by remember{ mutableStateOf(Bitmap.createBitmap(
        1, 1,
        Bitmap.Config.ARGB_8888
    ).asImageBitmap()) }

    LaunchedEffect(cameraSelector) {
        preview.setSurfaceProvider(previewView.surfaceProvider)
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

        ProcessCameraProvider.getInstance(context).also { cameraProviderFuture ->
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                detector.flip = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA

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
            }, ContextCompat.getMainExecutor(context))
        }
    }

    AndroidView({ previewView }, modifier = Modifier
        .fillMaxSize()
        .then(modifier))
    Image(overlayBitmap, "overlay", modifier = Modifier
        .fillMaxSize()
        .then(modifier))
}