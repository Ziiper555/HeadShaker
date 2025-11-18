package com.example.headshaker

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

class PoseController(
    private val onMenuMove: () -> Unit,       // cabeza izquierda
    private val onMenuSelect: () -> Unit,     // cabeza derecha
    private val onListen: () -> Unit          // mano arriba
) {

    private val detector: PoseDetector

    // Tiempo mínimo en ms para mantener postura
    private val holdTime = 1000L

    // Sensibilidad de inclinación: diferencia en Y entre ojos
    private val tiltThreshold = 18f

    // Sensibilidad de mano levantada
    private val handRaiseThreshold = -50f

    private var leftTiltStart: Long? = null
    private var rightTiltStart: Long? = null
    private var handUpStart: Long? = null

    init {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()

        detector = PoseDetection.getClient(options)
    }

    fun getAnalyzer(): ImageAnalysis.Analyzer =
        ImageAnalysis.Analyzer { imageProxy ->
            processImageProxy(imageProxy)
        }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { pose ->

                val leftEye  = pose.getPoseLandmark(PoseLandmark.LEFT_EYE)
                val rightEye = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE)
                val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
                val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

                if (leftEye != null && rightEye != null)
                    detectHeadTilt(leftEye.position.y, rightEye.position.y)

                if (rightWrist != null && rightShoulder != null)
                    detectHandRaise(
                        wristY = rightWrist.position.y,
                        shoulderY = rightShoulder.position.y
                    )
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun detectHeadTilt(leftEyeY: Float, rightEyeY: Float) {
        val now = System.currentTimeMillis()

        val diff = rightEyeY - leftEyeY

        // CABEZA INCLINADA A LA IZQUIERDA
        if (diff > tiltThreshold) {

            if (leftTiltStart == null)
                leftTiltStart = now

            val elapsed = now - leftTiltStart!!
            if (elapsed >= holdTime) {
                onMenuMove()     // avanzar opción
                leftTiltStart = now // permitir repetición
            }

            rightTiltStart = null
            return
        }

        // CABEZA INCLINADA A LA DERECHA
        if (diff < -tiltThreshold) {

            if (rightTiltStart == null)
                rightTiltStart = now

            val elapsed = now - rightTiltStart!!
            if (elapsed >= holdTime) {
                onMenuSelect()  // seleccionar opción
                rightTiltStart = now
            }

            leftTiltStart = null
            return
        }

        // Si la cabeza está recta, reiniciar
        leftTiltStart = null
        rightTiltStart = null
    }

    private fun detectHandRaise(wristY: Float, shoulderY: Float) {
        val now = System.currentTimeMillis()

        // Mano arriba = muñeca mucho más arriba que el hombro
        val diff = wristY - shoulderY

        if (diff < handRaiseThreshold) {

            if (handUpStart == null)
                handUpStart = now

            val elapsed = now - handUpStart!!
            if (elapsed > holdTime) {
                onListen()
                handUpStart = now
            }

        } else {
            handUpStart = null
        }
    }

    fun stop() {
        detector.close()
    }
}
