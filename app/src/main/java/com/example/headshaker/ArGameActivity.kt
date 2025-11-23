package com.example.headshaker

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class ArGameActivity : AppCompatActivity() {

    private lateinit var arFragment: CustomArFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_game)

        arFragment = supportFragmentManager
            .findFragmentById(R.id.ar_fragment) as CustomArFragment

        // Aquí puedes empezar a configurar la escena AR
        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            val frame = arFragment.arSceneView.arFrame
            // actualizar bola y bloques
        }
    }
}
