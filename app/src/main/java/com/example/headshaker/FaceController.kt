package com.example.headshaker

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetector
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.google.mlkit.vision.facemesh.FaceMeshPoint

class FaceController(
    private val onListen: () -> Unit
) {
    private val detector: FaceMeshDetector

    // Tiempo mínimo manteniendo cejas levantadas (ms)
    private val holdTime = 2000L

    // Umbral de distancia vertical entre ceja y ojo (ajustable)
    private val eyebrowThreshold = 40f

    private var eyebrowRaiseStart: Long? = null

    init {
        val options = FaceMeshDetectorOptions.Builder()
            .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
            .build()
        detector = FaceMeshDetection.getClient(options)
    }

    fun getAnalyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer(::processImageProxy)

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { faceMeshes ->
                if (faceMeshes.isNotEmpty()) {
                    val mesh = faceMeshes[0]  // Tomamos la primera cara
                    detectEyebrowRaise(mesh.allPoints)
                } else {
                    eyebrowRaiseStart = null
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun detectEyebrowRaise(points: List<FaceMeshPoint>) {
        val now = System.currentTimeMillis()

        // Ejemplo de indices (MediaPipe Face Mesh)
        val leftEyebrowUpper = points[105]   // parte superior de ceja izquierda
        val leftEyeUpper = points[159]       // parte superior del ojo izquierdo
        val rightEyebrowUpper = points[334]  // parte superior de ceja derecha
        val rightEyeUpper = points[386]      // parte superior del ojo derecho

        // Calculamos distancia vertical ceja-ojo (Y más pequeño arriba)
        val leftDistance = leftEyeUpper.position.y - leftEyebrowUpper.position.y
        val rightDistance = rightEyeUpper.position.y - rightEyebrowUpper.position.y

        val avgDistance = (leftDistance + rightDistance) / 2f

        if (avgDistance > eyebrowThreshold) {
            // Cejas levantadas
            if (eyebrowRaiseStart == null) eyebrowRaiseStart = now

            val elapsed = now - eyebrowRaiseStart!!
            if (elapsed >= holdTime) {
                onListen()
                eyebrowRaiseStart = now  // reiniciamos contador
            }
        } else {
            // Cejas bajadas → reiniciamos
            eyebrowRaiseStart = null
        }
    }

    fun stop() {
        detector.close()
    }
}