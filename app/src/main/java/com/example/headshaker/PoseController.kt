package com.example.headshaker

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task // <-- Importa la clase Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

class PoseController(
    private val onMenuMove: () -> Unit,
    private val onMenuSelect: () -> Unit
) {

    private val detector: PoseDetector
    private val holdTime = 1000L
    private val tiltThreshold = 18f
    private var leftTiltStart: Long? = null
    private var rightTiltStart: Long? = null
    private var canSelect = true  // control de cooldown para selección

    init {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        detector = PoseDetection.getClient(options)
    }

    // Esta función no necesita cambios
    fun getAnalyzer(): ImageAnalysis.Analyzer =
        ImageAnalysis.Analyzer { proxy ->
            processImageProxy(proxy)
        }

    // --- AQUÍ ESTÁ LA CORRECCIÓN ---
    @SuppressLint("UnsafeOptInUsageError")
    fun processImageProxy(imageProxy: ImageProxy): Task<Pose>? { // 1. Cambia el tipo de retorno a Task<Pose>?
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return null // Devuelve null si no hay imagen
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        // 2. Devuelve el Task directamente
        return detector.process(image)
            .addOnSuccessListener { pose ->
                val leftEye = pose.getPoseLandmark(PoseLandmark.LEFT_EYE)
                val rightEye = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE)

                if (leftEye != null && rightEye != null) {
                    detectHeadTilt(
                        leftEyeY = leftEye.position.y,
                        rightEyeY = rightEye.position.y
                    )
                }
            }
        // Eliminamos el .addOnCompleteListener de aquí
    }

    private fun detectHeadTilt(leftEyeY: Float, rightEyeY: Float) {
        val now = System.currentTimeMillis()
        val diff = rightEyeY - leftEyeY

        // CABEZA IZQUIERDA → mover opción
        if (diff > tiltThreshold) {
            if (leftTiltStart == null) leftTiltStart = now

            val elapsed = now - leftTiltStart!!
            if (elapsed >= holdTime) {
                onMenuMove()
                leftTiltStart = now
            }

            rightTiltStart = null
            return
        }

        // CABEZA DERECHA → seleccionar opción
        if (diff < -tiltThreshold) {
            if (!canSelect) return // todavía en cooldown

            if (rightTiltStart == null) rightTiltStart = now

            val elapsed = now - rightTiltStart!!
            if (elapsed >= holdTime) {
                onMenuSelect()  // ejecutar selección
                rightTiltStart = now

                // deshabilitamos temporalmente la selección
                canSelect = false
                android.os.Handler().postDelayed({
                    canSelect = true
                }, 3000) // 3 segundos
            }

            leftTiltStart = null
            return
        }

        // Cabeza recta → reiniciar
        leftTiltStart = null
        rightTiltStart = null
    }

    fun stop() {
        detector.close()
    }
}
