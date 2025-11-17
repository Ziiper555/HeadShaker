package com.example.headshaker

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.pose.PoseLandmark

class PoseController(
    private val context: Context,
    private val onMenuMove: () -> Unit,
    private val onMenuSelect: () -> Unit,
    private val onBigMovement: () -> Unit
) {

    private val detector: PoseDetector

    private var lastLeftY: Float? = null
    private var lastRightY: Float? = null

    private var lastTriggerLeft = 0L
    private var lastTriggerRight = 0L
    private var smallThreshold = 40f   // sensibilidad normal (subir hombro)
    private var bigThreshold = 140f    // sensibilidad movimiento fuerte
    private var  cooldown = 500L

    init {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()

        detector = PoseDetection.getClient(options)
    }

    fun getAnalyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer {
        processImageProxy(it)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        val img = imageProxy.image
        if (img != null) {
            val image = InputImage.fromMediaImage(img, imageProxy.imageInfo.rotationDegrees)

            detector.process(image)
                .addOnSuccessListener { pose ->
                    val left = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
                    val right = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

                    left?.let { detectLeft(it.position.y) }
                    right?.let { detectRight(it.position.y) }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    private fun detectLeft(currentY: Float) {
        val prev = lastLeftY
        lastLeftY = currentY
        if (prev == null) return

        val dy = currentY - prev
        val now = System.currentTimeMillis()

        if (dy < -bigThreshold && now - lastTriggerLeft > cooldown) {
            lastTriggerLeft = now
            onBigMovement()
            return
        }

        if (dy < -smallThreshold && now - lastTriggerLeft > cooldown) {
            lastTriggerLeft = now
            onMenuMove()
        }
    }

    private fun detectRight(currentY: Float) {
        val prev = lastRightY
        lastRightY = currentY
        if (prev == null) return

        val dy = currentY - prev
        val now = System.currentTimeMillis()

        if (dy < -bigThreshold && now - lastTriggerRight > cooldown) {
            lastTriggerRight = now
            onBigMovement()
            return
        }

        if (dy < -smallThreshold && now - lastTriggerRight > cooldown) {
            lastTriggerRight = now
            onMenuSelect()
        }
    }

    fun stop() {
        detector.close()
    }
}
