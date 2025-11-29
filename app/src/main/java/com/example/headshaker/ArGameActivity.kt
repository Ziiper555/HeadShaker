package com.example.headshaker

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.AugmentedFace
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Material
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory

class ArGameActivity : AppCompatActivity(), CustomArFragment.OnSceneReadyListener {

    private lateinit var arFragment: CustomArFragment
    private var bolaNode: Node? = null
    private lateinit var scoreTextView: TextView

    // --- PROPIEDADES PARA EL JUEGO ---
    private val bloques = mutableListOf<Node>()
    private var lastBlockSpawnTime = 0L
    private var blockMaterial: Material? = null
    private var fallSpeed = 0.09f // metros por segundo
    private var spawnInterval = 3000L // milisegundos
    private val gameDistance = 0.7f
    private var score = 0

    // --- PROPIEDADES PARA ANIMACIÓN ---
    private val animatingBlocks = mutableMapOf<Node, Long>()
    private val animationDuration = 200L

    // --- PROPIEDADES PARA SONIDO ---
    private lateinit var soundPool: SoundPool
    private var popSoundId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_game)

        arFragment = supportFragmentManager
            .findFragmentById(R.id.ar_fragment) as CustomArFragment
        scoreTextView = findViewById(R.id.score_text)

        arFragment.setOnSceneReadyListener(this)

        // --- INICIALIZAR SOUNDPOOL ---
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_GAME)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(audioAttributes).build()
        popSoundId = soundPool.load(this, R.raw.pop, 1)
        // ---------------------------
    }

    override fun onSceneReady() {
        // Material para la bola
        MaterialFactory.makeOpaqueWithColor(this, com.google.ar.sceneform.rendering.Color(android.graphics.Color.RED))
            .thenAccept { material ->
                val sphereRenderable = ShapeFactory.makeSphere(0.02f, Vector3(0f, 0f, 0f), material)
                val node = Node().apply {
                    renderable = sphereRenderable
                    isEnabled = false
                }
                arFragment.arSceneView.scene.addChild(node)
                bolaNode = node
            }

        // Material para los bloques
        MaterialFactory.makeTransparentWithColor(this, com.google.ar.sceneform.rendering.Color(android.graphics.Color.BLUE))
            .thenAccept { material ->
                blockMaterial = material
            }

        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            if (bolaNode == null || blockMaterial == null) return@addOnUpdateListener

            val faces = arFragment.arSceneView.session?.getAllTrackables(AugmentedFace::class.java)
            val firstTrackedFace = faces?.firstOrNull { it.trackingState == TrackingState.TRACKING }
            val now = System.currentTimeMillis()

            if (firstTrackedFace != null) {
                // ... (código de movimiento de la bola y generación de bloques sin cambios)
                bolaNode!!.isEnabled = true
                bloques.forEach { it.isEnabled = true }

                val camera = arFragment.arSceneView.scene.camera

                // --- POSICIÓN DE LA BOLA CON Z FIJO (CORREGIDO) ---
                val nosePose = firstTrackedFace.getRegionPose(AugmentedFace.RegionType.NOSE_TIP)
                val noseWorldPos = Vector3(nosePose.tx(), nosePose.ty(), nosePose.tz())
                val noseScreenPos = camera.worldToScreenPoint(noseWorldPos)
                val bolaRay = camera.screenPointToRay(noseScreenPos.x, noseScreenPos.y)
                bolaNode!!.worldPosition = bolaRay.getPoint(gameDistance)
                // --- FIN POSICIÓN BOLA ---

                // Escalar la bola para que parezca de tamaño constante
                val distance = Vector3.subtract(camera.worldPosition, bolaNode!!.worldPosition).length()
                bolaNode!!.localScale = Vector3(distance, distance, distance)

                // --- INICIO DE LA LÓGICA DEL JUEGO ---
                val view = arFragment.arSceneView

                // 2. Generar bloques desde el borde superior de la PANTALLA
                if (now - lastBlockSpawnTime > spawnInterval) {
                    lastBlockSpawnTime = now

                    val block = Node()
                    block.renderable = ShapeFactory.makeCube(
                        Vector3(0.06f, 0.06f, 0.06f), // tamaño del cubo
                        Vector3.zero(),
                        blockMaterial
                    )

                    val screenWidth = view.width.toFloat()
                    val randomScreenX = (screenWidth / 2f) + (Math.random().toFloat() * 800f - 400f)

                    val spawnRay = camera.screenPointToRay(randomScreenX, 0f)
                    block.worldPosition = spawnRay.getPoint(gameDistance)

                    arFragment.arSceneView.scene.addChild(block)
                    bloques.add(block)
                }

                // 3. Mover bloques, detectar colisiones y fin del juego
                val viewHeight = view.height.toFloat()
                val iterator = bloques.iterator()
                while(iterator.hasNext()){
                    val block = iterator.next()

                    // Mover el bloque hacia abajo
                    block.worldPosition = Vector3(
                        block.worldPosition.x,
                        block.worldPosition.y - fallSpeed * frameTime.deltaSeconds,
                        block.worldPosition.z
                    )

                    // Detectar colisión con la bola (2D)
                    val dx = block.worldPosition.x - bolaNode!!.worldPosition.x
                    val dy = block.worldPosition.y - bolaNode!!.worldPosition.y
                    val distanceSq2D = dx * dx + dy * dy
                    val threshold = 0.04f
                    if (distanceSq2D < threshold * threshold) {
                        iterator.remove() // Lo quitamos de la lista de bloques que caen

                        soundPool.play(popSoundId, 1f, 1f, 1, 0, 1f) // <-- REPRODUCIR SONIDO

                        // Hacemos una copia del material para animarlo de forma independiente
                        block.renderable?.material = block.renderable?.material?.makeCopy()
                        animatingBlocks[block] = now // Lo añadimos a la lista de bloques que se animan

                        score++
                        scoreTextView.text = "Puntos: $score"

                        // --- SISTEMA DE PROGRESIÓN ---
                        if (score > 0 && score % 10 == 0) {
                            fallSpeed += 0.02f // Aumentar la velocidad de caída
                            if (spawnInterval > 1000L) {
                                spawnInterval -= 500L // Reducir el intervalo de aparición
                            }else if(spawnInterval > 500L){
                                spawnInterval -= 100L
                            }
                        }
                        // --- FIN SISTEMA DE PROGRESIÓN ---

                        continue
                    }

                    val screenPos = camera.worldToScreenPoint(block.worldPosition)

                    if (screenPos.y >= viewHeight) {
                        finish()
                        return@addOnUpdateListener
                    }
                }
            } else {
                bolaNode!!.isEnabled = false
                bloques.forEach { it.isEnabled = false }
            }

            // --- LÓGICA DE ANIMACIÓN DE DESTRUCCIÓN ---
            handleBlockAnimation(now)
        }
    }

    private fun handleBlockAnimation(now: Long) {
        val animIterator = animatingBlocks.iterator()
        while (animIterator.hasNext()) {
            val entry = animIterator.next()
            val block = entry.key
            val startTime = entry.value
            val elapsedTime = now - startTime

            if (elapsedTime >= animationDuration) {
                arFragment.arSceneView.scene.removeChild(block)
                animIterator.remove()
            } else {
                val progress = elapsedTime.toFloat() / animationDuration
                val currentScale = 1.0f + (1.3f - 1.0f) * progress
                block.localScale = Vector3(currentScale, currentScale, currentScale)

                block.renderable?.material?.let { material ->
                    val blue = com.google.ar.sceneform.rendering.Color(android.graphics.Color.BLUE)
                    material.setFloat4("color", blue.r, blue.g, blue.b, 1.0f - progress)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release() // Liberar recursos del SoundPool
    }
}
