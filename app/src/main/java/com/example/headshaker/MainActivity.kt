package com.example.headshaker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.example.headshaker.ui.theme.HeadShakerTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var voice: VoiceController
    private val opciones = listOf("Jugar", "Configuración", "Información", "Salir")
    private var opcionSeleccionada by mutableStateOf(0)

    // Declaramos ambos controladores
    private lateinit var poseController: PoseController
    private lateinit var faceController: FaceController

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

        voice = VoiceController(this, speechLauncher)
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
                    onEscucharClick = { voice.iniciarReconocimiento() }
                )
            }
        }
    }

    // Inicia CameraX y asigna los controladores
    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Inicialización de los controladores (esto no cambia)
            poseController = PoseController(
                onMenuMove = {
                    opcionSeleccionada =
                        (opcionSeleccionada + 1 + opciones.size) % opciones.size
                },
                onMenuSelect = {
                    onOpcionSeleccionada(opcionSeleccionada)
                }
            )

            faceController = FaceController(
                onListen = { voice.iniciarReconocimiento() }
            )

            val faceAnalyzer = faceController.getAnalyzer()

            // --- ESTE BLOQUE ES EL QUE CAMBIA ---
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                // 1. Iniciamos el análisis de pose y obtenemos el Task
                val poseTask = poseController.processImageProxy(imageProxy)

                // 2. Cuando el análisis de pose termine (con éxito o no)...
                poseTask?.addOnCompleteListener {
                    // 3. ...iniciamos el análisis facial.
                    // FaceController se encargará de cerrar el imageProxy.
                    faceAnalyzer.analyze(imageProxy)
                }
            }
            // --- FIN DEL BLOQUE MODIFICADO ---

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
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
        // Detenemos ambos controladores
        if (::poseController.isInitialized) {
            poseController.stop()
        }
        if (::faceController.isInitialized) {
            faceController.stop()
        }
        cameraExecutor.shutdown()
        voice.destruir()
        super.onDestroy()
    }

    private fun onOpcionSeleccionada(index: Int) {
        val opcion = opciones[index]

        voice.hablar("Has seleccionado $opcion")

        when (opcion) {
            "Jugar" -> {
                val intent = Intent(this, ArGameActivity::class.java)
                startActivity(intent)
            }
            "Configuración" -> { /* TODO */ }
            "Información" -> { /* TODO */ }
            "Salir" -> { finish() }
        }
    }

    @Composable
    fun PantallaMenu(
        opciones: List<String>,
        opcionSeleccionada: Int,
        onEscucharClick: () -> Unit
    ) {
        val mensajes = remember {
            listOf(
                "Inclina la cabeza a la derecha para moverte por el menu",
                "Inclina la cabeza a la izquierda para seleccionar una opción",
                "Eleva las cejas durante unos segundos para activar la escucha",
                "Di bajar, subir o seleccionar para moverte por el menu"
            )
        }
        var mensajeActual by remember { mutableStateOf(mensajes[0]) }

        LaunchedEffect(key1 = true) {
            var index = 0
            while (true) {
                delay(5000)
                index = (index + 1) % mensajes.size
                mensajeActual = mensajes[index]
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

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

            Button(onClick = onEscucharClick) {
                Text("Escuchar")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = mensajeActual,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }
}
