package com.example.headshaker

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlin.math.atan2
import kotlin.math.abs
import java.lang.Math.toDegrees
import java.util.ArrayDeque
import androidx.compose.ui.geometry.Offset

class PoseController() {
}