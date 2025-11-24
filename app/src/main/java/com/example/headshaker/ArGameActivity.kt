package com.example.headshaker

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory

class ArGameActivity : AppCompatActivity(), CustomArFragment.OnSceneReadyListener {

    private lateinit var arFragment: CustomArFragment
    private var bolaNode: Node? = null
    private lateinit var faceController: FaceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_game)

        arFragment = supportFragmentManager
            .findFragmentById(R.id.ar_fragment) as CustomArFragment

        // 3️⃣ Inicializar FaceController
        faceController = FaceController {
            // Esto se dispara cuando el usuario levanta cejas tiempo suficiente
            // Aquí puedes añadir eventos especiales, por ejemplo "activar power-up"
            Log.d("AR", "Cejas levantadas!")
        }

        // 4️⃣ Integrar FaceController con CameraX o ARCore frame
        // Nota: Si usas ARCore trasera, captura imagen con arFragment.arSceneView.arFrame
        startFaceAnalysis()
    }

    override fun onSceneReady() {
        // 2️⃣ Cargar modelo GLB desde res/raw
        MaterialFactory.makeOpaqueWithColor(this, com.google.ar.sceneform.rendering.Color(android.graphics.Color.RED))
            .thenAccept { material ->
                val sphereRenderable = ShapeFactory.makeSphere(
                    0.05f,                // radio en metros
                    Vector3(0f, 0f, 0f),  // centro
                    material
                )

                bolaNode = Node().apply {
                    this.renderable = sphereRenderable
                    setParent(arFragment.arSceneView.scene)
                }
            }

        // 5️⃣ Actualizar posición de la bola cada frame
        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            updateBola()
        }
    }

    private fun startFaceAnalysis() {
        // Aquí tienes que configurar un ImageAnalysis usando CameraX y añadir faceController.getAnalyzer()
        // Ejemplo:
        // val analysisUseCase = ImageAnalysis.Builder().build()
        // analysisUseCase.setAnalyzer(executor, faceController.getAnalyzer())
        // cameraProvider.bindToLifecycle(this, cameraSelector, analysisUseCase)
    }

    private fun updateBola() {
        // Placeholder: si quieres usar nariz de FaceMesh
        val nosePos = faceController.getNosePosition3D()  // función que tú añades para devolver Vector3
        if (nosePos != null) {
            bolaNode?.worldPosition = nosePos
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        faceController.stop()
    }
}
