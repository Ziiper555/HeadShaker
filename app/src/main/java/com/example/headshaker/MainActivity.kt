package com.example.headshaker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var voice: VoiceController
    private val opciones = listOf("Jugar", "Configuración", "Información", "Salir")
    private var opcionSeleccionada by mutableStateOf(0)

    // --- PUNTUACIÓN MÁXIMA ---
    private var highScore by mutableStateOf(0)

    private lateinit var poseController: PoseController
    private lateinit var faceController: FaceController

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView

    private var menuMediaPlayer: MediaPlayer? = null

    // --- LAUNCHERS PARA ACTIVIDADES ---
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val texto = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            voice.procesarTexto(texto, opciones,
                onMenuChange = { movimiento -> opcionSeleccionada = (opcionSeleccionada + movimiento + opciones.size) % opciones.size },
                onSeleccion = { onOpcionSeleccionada(opcionSeleccionada) },
                onSeleccionDirecta = { index ->
                    opcionSeleccionada = index
                    onOpcionSeleccionada(index)
                }
            )
        }
    }

    private val gameLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val score = result.data?.getIntExtra("score", 0) ?: 0
            if (score > highScore) {
                highScore = score
                saveHighScore(highScore)
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else voice.hablar("Necesito permiso de cámara para detectar movimientos.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this)
        voice = VoiceController(this, speechLauncher)
        cameraExecutor = Executors.newSingleThreadExecutor()

        highScore = loadHighScore()

        menuMediaPlayer = MediaPlayer.create(this, R.raw.menumusic)
        menuMediaPlayer?.isLooping = true

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            HeadShakerTheme {
                PantallaMenu(
                    opciones = opciones,
                    opcionSeleccionada = opcionSeleccionada,
                    highScore = highScore,
                    onEscucharClick = { voice.iniciarReconocimiento() },
                    previewView = previewView
                )
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

            poseController = PoseController(
                onMenuMove = { opcionSeleccionada = (opcionSeleccionada + 1 + opciones.size) % opciones.size },
                onMenuSelect = { onOpcionSeleccionada(opcionSeleccionada) }
            )
            faceController = FaceController(onListen = { voice.iniciarReconocimiento() })

            val faceAnalyzer = faceController.getAnalyzer()
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                poseController.processImageProxy(imageProxy)?.addOnCompleteListener { faceAnalyzer.analyze(imageProxy) }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis)
            } catch (exc: Exception) {
                exc.printStackTrace()
                voice.hablar("Error al iniciar la cámara: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onOpcionSeleccionada(index: Int) {
        val opcion = opciones[index]
        voice.hablar("Has seleccionado $opcion")
        when (opcion) {
            "Jugar" -> gameLauncher.launch(Intent(this, ArGameActivity::class.java))
            "Salir" -> finish()
        }
    }

    private fun saveHighScore(score: Int) {
        try {
            DataOutputStream(openFileOutput("highscore.dat", MODE_PRIVATE)).use { it.writeInt(score) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadHighScore(): Int {
        return try {
            DataInputStream(openFileInput("highscore.dat")).use { it.readInt() }
        } catch (e: FileNotFoundException) {
            0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    override fun onResume() {
        super.onResume()
        menuMediaPlayer?.takeIf { !it.isPlaying }?.start()
    }

    override fun onPause() {
        super.onPause()
        menuMediaPlayer?.takeIf { it.isPlaying }?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::poseController.isInitialized) poseController.stop()
        if (::faceController.isInitialized) faceController.stop()
        cameraExecutor.shutdown()
        voice.destruir()
        menuMediaPlayer?.release()
        menuMediaPlayer = null
    }
}

@Composable
fun PantallaMenu(
    opciones: List<String>,
    opcionSeleccionada: Int,
    highScore: Int,
    onEscucharClick: () -> Unit,
    previewView: PreviewView
) {
    val mensajes = remember { listOf("Inclina la cabeza para navegar", "Levanta las cejas para hablar", "Di el nombre de una opción") }
    var mensajeActual by remember { mutableStateOf(mensajes[0]) }

    LaunchedEffect(key1 = true) {
        var index = 0
        while (true) {
            delay(5000)
            index = (index + 1) % mensajes.size
            mensajeActual = mensajes[index]
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
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
            Button(onClick = onEscucharClick) { Text("Escuchar") }
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = mensajeActual, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }

        AndroidView(
            factory = { previewView },
            modifier = Modifier.size(width = 120.dp, height = 160.dp).padding(16.dp).align(Alignment.TopEnd)
        )

        Text(
            text = "Récord: $highScore",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        )
    }
}
