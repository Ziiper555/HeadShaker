package com.example.headshaker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.example.headshaker.ui.theme.HeadShakerTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.app.Activity
import android.content.Intent

class MainActivity : ComponentActivity() {

    private lateinit var voice: VoiceController
    private val opciones = listOf("Jugar", "Configuración", "Información", "Salir")
    private var opcionSeleccionada by mutableStateOf(0)
    private lateinit var poseController: PoseController

    // CameraX helpers
    private lateinit var cameraExecutor: ExecutorService

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                val texto =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""

                voice.procesarTexto(
                    texto,
                    onMenuChange = { movimiento ->
                        opcionSeleccionada =
                            (opcionSeleccionada + movimiento + opciones.size) % opciones.size
                    },
                    onSeleccion = {
                        onOpcionSeleccionada(opcionSeleccionada)
                    }
                )
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                voice.hablar("Necesito permiso de cámara para detectar movimientos.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializamos el controlador de voz
        voice = VoiceController(this, speechLauncher)

        // Executor para ImageAnalysis
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            HeadShakerTheme {
                PantallaMenu(
                    opciones = opciones,
                    opcionSeleccionada = opcionSeleccionada,
                    onEscucharClick = { voice.iniciarReconocimiento() } // Solo escucha cuando pulses
                )
            }
        }
    }

    // Inicia CameraX y configura ImageAnalysis con el PoseController
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // ImageAnalysis: solo analizamos frames (no mostramos preview necesario)
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Inicializamos PoseController PASÁNDOLE el analyzer
            poseController = PoseController(
                onMenuMove = {
                    opcionSeleccionada =
                        (opcionSeleccionada + 1 + opciones.size) % opciones.size
                },
                onMenuSelect = {
                    onOpcionSeleccionada(opcionSeleccionada)
                },
                onListen = {
                    voice.iniciarReconocimiento()
                }
            )

            // Asignamos el analyzer de PoseController
            imageAnalysis.setAnalyzer(cameraExecutor, poseController.getAnalyzer())

            // Seleccionamos cámara frontal (ajusta si quieres trasera)
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind antes de bindear, para evitar excepciones al reconfigurar
                cameraProvider.unbindAll()
                // Bind solo ImageAnalysis (no es obligatorio añadir Preview)
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageAnalysis
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
                voice.hablar("Error al iniciar la cámara: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        poseController.stop()
        cameraExecutor.shutdown()
        voice.destruir()
        super.onDestroy()
    }

    private fun onOpcionSeleccionada(index: Int) {
        val opcion = opciones[index]

        // Decir por voz la opción seleccionada
        voice.hablar("Has seleccionado $opcion")

        // EJECUTAR ACCIONES SEGÚN LA OPCIÓN
        when (opcion) {
            "Jugar" -> {
                // TODO: Iniciar juego
            }
            "Configuración" -> {
                // TODO: Abrir settings
            }
            "Información" -> {
                // TODO: Mostrar info
            }
            "Salir" -> {
                // TODO: Cerrar app
            }
        }
    }

    @Composable
    fun PantallaMenu(
        opciones: List<String>,
        opcionSeleccionada: Int,
        onEscucharClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Menú
            opciones.forEachIndexed { index, opcion ->
                val isSelected = index == opcionSeleccionada

                Text(
                    text = if (isSelected) "> $opcion" else "  $opcion",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Botón para escuchar
            Button(onClick = onEscucharClick) {
                Text("Escuchar")
            }
        }
    }
}