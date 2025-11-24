package com.example.headshaker

import android.content.Context
import android.os.Bundle
import android.view.View
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment

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
        onSceneReadyListener?.onSceneReady()
    }

    override fun getSessionConfiguration(session: Session?): Config {
        val config = Config(session)
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

        session?.configure(config)

        // Ocultar la mano de detección
        planeDiscoveryController.hide()
        planeDiscoveryController.setInstructionView(null)

        // Desactivar detección de planos
        arSceneView.planeRenderer.isEnabled = false

        return config
    }

    override fun getAdditionalPermissions(): Array<String> {
        return emptyArray()
    }

    override fun isArRequired(): Boolean {
        return true
    }
}
