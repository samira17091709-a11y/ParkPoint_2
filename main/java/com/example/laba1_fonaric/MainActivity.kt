package com.example.laba1_fonaric

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.laba1_fonaric.ui.theme.Laba1fonaricTheme
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var vibrator: Vibrator
    private var isFlashOn = false
    private var cameraId: String? = null

    private var lastShakeTime = 0L
    private var shakeCount = 0
    private var lastAccel = 0f
    private var currentAccel = 0f

    private var onFlashStateChanged: ((Boolean) -> Unit)? = null

    companion object {
        private const val SHAKE_THRESHOLD = 15
        private const val SHAKE_TIMEOUT = 1000L
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        checkCameraPermission()

        setContent {
            Laba1fonaricTheme {
                var flashState by remember { mutableStateOf(isFlashOn) }

                LaunchedEffect(Unit) {
                    onFlashStateChanged = { newState ->
                        flashState = newState
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (flashState) Color(0xFFFFF0B5) else Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (flashState) "🔦 ФОНАРИК ГОРИТ" else "📱\nДважды тряхните",
                        color = if (flashState) Color.Black else Color.White,
                        fontSize = if (flashState) 24.sp else 36.sp,
                        lineHeight = if (flashState) 32.sp else 48.sp
                    )
                }
            }
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                initCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun initCamera() {
        try {
            // Получаем ID камеры со вспышкой
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if (flashAvailable == true) {
                    cameraId = id
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        // Инициализация датчиков
        val sensorManager = getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager
        val accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)

        accelerometer?.let {
            lastAccel = 0f
            currentAccel = 0f
            sensorManager.registerListener(sensorListener, it, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        val sensorManager = getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager
        sensorManager.unregisterListener(sensorListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Выключаем фонарик при закрытии
        turnOffFlash()
    }

    private val sensorListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
            event?.let {
                if (it.sensor.type == android.hardware.Sensor.TYPE_ACCELEROMETER) {
                    val x = it.values[0]
                    val y = it.values[1]
                    val z = it.values[2]

                    lastAccel = currentAccel
                    currentAccel = sqrt(x * x + y * y + z * z).toFloat()
                    val delta = currentAccel - lastAccel
                    val force = abs(delta)

                    val currentTime = System.currentTimeMillis()

                    if (force > SHAKE_THRESHOLD) {
                        if (currentTime - lastShakeTime > SHAKE_TIMEOUT) {
                            shakeCount = 1
                        } else {
                            shakeCount++
                        }

                        lastShakeTime = currentTime

                        if (shakeCount >= 2) {
                            if (vibrator.hasVibrator()) {
                                vibrator.vibrate(200)
                            }
                            toggleFlashlight()
                            shakeCount = 0
                            lastShakeTime = currentTime + 500
                        }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {
            // Не требуется
        }
    }

    private fun toggleFlashlight() {
        if (cameraId == null) {
            initCamera()
            if (cameraId == null) return
        }

        try {
            if (isFlashOn) {
                // Выключаем
                cameraManager.setTorchMode(cameraId!!, false)
                isFlashOn = false
            } else {
                // Включаем
                cameraManager.setTorchMode(cameraId!!, true)
                isFlashOn = true
            }
            onFlashStateChanged?.invoke(isFlashOn)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun turnOffFlash() {
        try {
            if (isFlashOn && cameraId != null) {
                cameraManager.setTorchMode(cameraId!!, false)
                isFlashOn = false
                onFlashStateChanged?.invoke(false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}