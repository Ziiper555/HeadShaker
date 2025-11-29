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
    private val opciones = listOf("Jugar", "Musica", "Información", "Salir")
    private var opcionSeleccionada by mutableStateOf(0)

    private var highScore by mutableStateOf(0)
    private var isMusicMuted by mutableStateOf(false)

    private lateinit var poseController: PoseController
    private lateinit var faceController: FaceController

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView

    private var menuMediaPlayer: MediaPlayer? = null

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)?.let {
            voice.procesarTexto(it, opciones,
                onMenuChange = { mov -> opcionSeleccionada = (opcionSeleccionada + mov + opciones.size) % opciones.size },
                onSeleccion = { onOpcionSeleccionada(opcionSeleccionada) },
                onSeleccionDirecta = { index -> onOpcionSeleccionada(index).also { opcionSeleccionada = index } }
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
        if (granted) startCamera() else voice.hablar("Necesito permiso de cámara.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this)
        voice = VoiceController(this, speechLauncher)
        cameraExecutor = Executors.newSingleThreadExecutor()
        highScore = loadHighScore()
        isMusicMuted = loadMusicMuted()

        if (!isMusicMuted) {
            menuMediaPlayer = MediaPlayer.create(this, R.raw.menumusic)?.apply {
                isLooping = true
                setVolume(0.2f, 0.2f)
            }
        }

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
            faceController = FaceController { voice.iniciarReconocimiento() }

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                poseController.processImageProxy(imageProxy)?.addOnCompleteListener { faceController.getAnalyzer().analyze(imageProxy) }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) { e.printStackTrace() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onOpcionSeleccionada(index: Int) {
        val opcion = opciones[index]
        when (opcion) {
            "Jugar" -> {
                voice.hablar("Has seleccionado Jugar")
                val intent = Intent(this, ArGameActivity::class.java)
                intent.putExtra("isMusicMuted", isMusicMuted)
                gameLauncher.launch(intent)
            }
            "Musica" -> {
                isMusicMuted = !isMusicMuted
                saveMusicMuted(isMusicMuted)
                if (isMusicMuted) {
                    menuMediaPlayer?.pause()
                    voice.hablar("Música desactivada")
                } else {
                    if (menuMediaPlayer == null) {
                        menuMediaPlayer = MediaPlayer.create(this, R.raw.menumusic)?.apply {
                            isLooping = true
                            setVolume(0.4f, 0.4f)
                        }
                    }
                    menuMediaPlayer?.start()
                    voice.hablar("Música activada")
                }
            }
            "Información" -> voice.hablar("Rompe los bloques azules, no toques los bloques rosas")
            "Salir" -> finish()
        }
    }

    private fun saveHighScore(score: Int) {
        try {
            DataOutputStream(openFileOutput("highscore.dat", MODE_PRIVATE)).use { it.writeInt(score) }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun loadHighScore(): Int {
        return try {
            DataInputStream(openFileInput("highscore.dat")).use { it.readInt() }
        } catch (e: Exception) { 0 }
    }

    private fun saveMusicMuted(muted: Boolean) {
        try {
            DataOutputStream(openFileOutput("music_settings.dat", MODE_PRIVATE)).use { it.writeBoolean(muted) }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun loadMusicMuted(): Boolean {
        return try {
            DataInputStream(openFileInput("music_settings.dat")).use { it.readBoolean() }
        } catch (e: Exception) { false }
    }

    override fun onResume() {
        super.onResume()
        if (!isMusicMuted) menuMediaPlayer?.start()
    }

    override fun onPause() {
        super.onPause()
        menuMediaPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::poseController.isInitialized) poseController.stop()
        if (::faceController.isInitialized) faceController.stop()
        cameraExecutor.shutdown()
        voice.destruir()
        menuMediaPlayer?.release()
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
    val mensajes = remember { listOf("Inclina la cabeza para navegar", "Levanta las cejas para hablar", "Di una opción") }
    var mensajeActual by remember { mutableStateOf(mensajes[0]) }

    LaunchedEffect(Unit) {
        var index = 0
        while (true) {
            delay(5000)
            index = (index + 1) % mensajes.size
            mensajeActual = mensajes[index]
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            opciones.forEachIndexed { index, opcion ->
                Text(
                    text = if (index == opcionSeleccionada) "> $opcion" else "  $opcion",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (index == opcionSeleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            Spacer(Modifier.height(40.dp))
            Button(onClick = onEscucharClick) { Text("Escuchar") }
            Spacer(Modifier.height(20.dp))
            Text(mensajeActual, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }

        AndroidView(
            factory = { previewView },
            modifier = Modifier.size(120.dp, 160.dp).padding(16.dp).align(Alignment.TopEnd)
        )

        Text(
            text = "Récord: $highScore",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        )
    }
}
