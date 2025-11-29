package com.example.headshaker

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

    // --- NUEVAS PROPIEDADES PARA EL JUEGO ---
    private val bloques = mutableListOf<Node>()
    private var lastBlockSpawnTime = 0L
    private var blockMaterial: Material? = null
    private var fallSpeed = 0.09f // metros por segundo
    private var spawnInterval = 3000L // milisegundos para crear un bloque
    private val gameDistance = 0.7f // Distancia (profundidad) FIJA a la que transcurre el juego
    private var score = 0

    // --- PROPIEDADES PARA ANIMACIÓN ---
    private val animatingBlocks = mutableMapOf<Node, Long>()
    private val animationDuration = 200L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_game)

        arFragment = supportFragmentManager
            .findFragmentById(R.id.ar_fragment) as CustomArFragment
        scoreTextView = findViewById(R.id.score_text)

        arFragment.setOnSceneReadyListener(this)
    }

    override fun onSceneReady() {
        // Material para la bola (la dejamos para referencia visual)
        MaterialFactory.makeOpaqueWithColor(this, com.google.ar.sceneform.rendering.Color(android.graphics.Color.RED))
            .thenAccept { material ->
                val sphereRenderable = ShapeFactory.makeSphere(
                    0.02f,                // radio en metros
                    Vector3(0f, 0f, 0f),  // centro
                    material
                )

                val node = Node()
                node.renderable = sphereRenderable
                node.isEnabled = false

                arFragment.arSceneView.scene.addChild(node)
                bolaNode = node
            }

        // Material para los bloques que caen (TRANSPARENTE para poder animar el alpha)
        MaterialFactory.makeTransparentWithColor(this, com.google.ar.sceneform.rendering.Color(android.graphics.Color.BLUE))
            .thenAccept { material ->
                blockMaterial = material
            }

        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            if (bolaNode == null || blockMaterial == null) {
                return@addOnUpdateListener
            }

            val faces = arFragment.arSceneView.session?.getAllTrackables(AugmentedFace::class.java)
            val firstTrackedFace = faces?.firstOrNull { it.trackingState == TrackingState.TRACKING }
            val now = System.currentTimeMillis()

            if (firstTrackedFace != null) {
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

                        // Hacemos una copia del material para animarlo de forma independiente
                        block.renderable?.material = block.renderable?.material?.makeCopy()
                        animatingBlocks[block] = now // Lo añadimos a la lista de bloques que se animan

                        score++
                        scoreTextView.text = "Puntos: $score"

                        // --- SISTEMA DE PROGRESIÓN ---
                        if (score > 0 && score % 10 == 0) {
                            fallSpeed += 0.02f // Aumentar la velocidad de caída
                            if (spawnInterval > 500L) {
                                spawnInterval -= 500L // Reducir el intervalo de aparición
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
                // --- FIN DE LA LÓGICA DEL JUEGO ---

            } else {
                // Si no hay cara, ocultamos todo.
                bolaNode!!.isEnabled = false
                bloques.forEach { it.isEnabled = false }
            }

            // --- LÓGICA DE ANIMACIÓN DE DESTRUCCIÓN ---
            val animIterator = animatingBlocks.iterator()
            while (animIterator.hasNext()) {
                val entry = animIterator.next()
                val block = entry.key
                val startTime = entry.value
                val elapsedTime = now - startTime

                if (elapsedTime >= animationDuration) {
                    // La animación ha terminado, eliminamos el nodo
                    arFragment.arSceneView.scene.removeChild(block)
                    animIterator.remove()
                } else {
                    // La animación está en curso
                    val progress = elapsedTime.toFloat() / animationDuration

                    // 1. Aumentar el tamaño
                    val initialScale = 1.0f
                    val finalScale = 1.3f
                    val currentScale = initialScale + (finalScale - initialScale) * progress
                    block.localScale = Vector3(currentScale, currentScale, currentScale)

                    // 2. Hacerlo transparente
                    block.renderable?.material?.let { material ->
                        val blue = com.google.ar.sceneform.rendering.Color(android.graphics.Color.BLUE)
                        val newAlpha = 1.0f - progress
                        material.setFloat4("color", blue.r, blue.g, blue.b, newAlpha)
                    }
                }
            }
            // --- FIN LÓGICA DE ANIMACIÓN ---
        }
    }
}
