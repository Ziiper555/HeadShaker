package com.example.headshaker

import android.content.Context
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment

class CustomArFragment : ArFragment() {

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Deshabilita tap en planos
        setOnTapArPlaneListener(null)

        // Deshabilita la visualización de la manita
        this.arSceneView.planeRenderer.isVisible = false
    }

    override fun getSessionConfiguration(session: Session): Config {
        val config = super.getSessionConfiguration(session)
        config.planeFindingMode = Config.PlaneFindingMode.DISABLED
        session.configure(config)
        return config
    }
}

