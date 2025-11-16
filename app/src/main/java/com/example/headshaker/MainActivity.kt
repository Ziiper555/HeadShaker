package com.example.headshaker

import android.app.Activity
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

class MainActivity : ComponentActivity() {

    private lateinit var voice: VoiceController
    private val opciones = listOf("Jugar", "Configuración", "Información", "Salir")
    private var opcionSeleccionada by mutableStateOf(0)

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {

                val texto = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializamos el controlador externo
        voice = VoiceController(this, speechLauncher)

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

    override fun onDestroy() {
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