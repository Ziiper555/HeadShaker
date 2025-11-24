package com.example.headshaker

import android.content.Context
import android.os.Bundle
import android.view.View
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment
import java.util.EnumSet

class CustomArFragment : ArFragment() {

    interface OnSceneReadyListener {
        fun onSceneReady()
    }

    private var onSceneReadyListener: OnSceneReadyListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        onSceneReadyListener = context as? OnSceneReadyListener
    }

    override fun onDetach() {
        super.onDetach()
        onSceneReadyListener = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Ocultar la mano de detección y el plano
        planeDiscoveryController.hide()
        planeDiscoveryController.setInstructionView(null)
        arSceneView.planeRenderer.isEnabled = false
        onSceneReadyListener?.onSceneReady()
    }

    override fun getSessionConfiguration(session: Session): Config {
        val config = super.getSessionConfiguration(session)
        config.augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
        return config
    }

    override fun getSessionFeatures(): Set<Session.Feature> {
        return EnumSet.of(Session.Feature.FRONT_CAMERA)
    }
}
