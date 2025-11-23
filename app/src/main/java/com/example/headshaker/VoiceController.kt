package com.example.headshaker

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.result.ActivityResultLauncher
import java.util.Locale

class VoiceController(
    private val context: Context,
    private val speechLauncher: ActivityResultLauncher<Intent>
) : TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("es", "ES")
        }
    }

    fun iniciarReconocimiento() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Di algo...")

        try {
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            hablar("No puedo iniciar el reconocimiento de voz.")
        }
    }

    fun procesarTexto(
        texto: String,
        onMenuChange: (Int) -> Unit,
        onSeleccion: () -> Unit
    ) {
        val t = texto.lowercase()

        when {
            "subir" in t || "arriba" in t || "sube" in t -> {
                onMenuChange(-1)
                hablar("Subiendo opción")
            }

            "baja" in t || "abajo" in t -> {
                onMenuChange(1)
                hablar("Bajando opción")
            }

            "seleccionar" in t || "aceptar" in t -> {
                onSeleccion()
            }

            else -> hablar("No entendí el comando")
        }
    }

    fun hablar(msg: String) {
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun destruir() {
        tts.stop()
        tts.shutdown()
    }
}