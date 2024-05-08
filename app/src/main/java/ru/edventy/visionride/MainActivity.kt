package ru.edventy.visionride

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import org.tensorflow.lite.task.gms.vision.TfLiteVision
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import ru.edventy.visionride.ui.theme.VisionRideTheme


class MainActivity : ComponentActivity(){
    private lateinit var cameraExecutor: ExecutorService
    private var detector = TFObjectDetector()

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
                initrializeTFLite()
            }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VisionRideTheme {
                MainScreen()
            }
        }

        // Request camera permissions
        if (!allPermissionsGranted()) {
            requestPermissions()
        }
    }

    private fun initrializeTFLite(){

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