package com.example.headshaker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.AugmentedFace
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory

class ArGameActivity : AppCompatActivity(), CustomArFragment.OnSceneReadyListener {

    private lateinit var arFragment: CustomArFragment
    private var bolaNode: Node? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_game)

        arFragment = supportFragmentManager
            .findFragmentById(R.id.ar_fragment) as CustomArFragment
    }

    override fun onSceneReady() {
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

        arFragment.arSceneView.scene.addOnUpdateListener {
            val faces = arFragment.arSceneView.session?.getAllTrackables(AugmentedFace::class.java)

            val firstTrackedFace = faces?.firstOrNull { it.trackingState == TrackingState.TRACKING }

            if (firstTrackedFace != null) {
                // Actualizar la posición de la bola a la de la nariz
                val nosePose = firstTrackedFace.getRegionPose(AugmentedFace.RegionType.NOSE_TIP)
                bolaNode?.worldPosition = Vector3(nosePose.tx(), nosePose.ty(), nosePose.tz())
                bolaNode?.isEnabled = true
            } else {
                // Ocultar la bola si no hay caras
                bolaNode?.isEnabled = false
            }
        }
    }
}
