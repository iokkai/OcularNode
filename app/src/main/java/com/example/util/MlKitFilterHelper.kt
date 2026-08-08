package com.example.util

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MlKitAnalysisResult(
    val hasPerson: Boolean,
    val hasPet: Boolean,
    val detectedLabels: List<String>,
    val shouldSuppressNotification: Boolean, // true if Person only (主人在家 -> 攔截推播)
    val summaryText: String
)

object MlKitFilterHelper {
    private const val TAG = "MlKitFilterHelper"

    private val objectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        ObjectDetection.getClient(options)
    }

    private val imageLabeler by lazy {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.40f)
            .build()
        ImageLabeling.getClient(options)
    }

    suspend fun analyzeFrame(bitmap: Bitmap): MlKitAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val detectedObjects = try {
                Tasks.await(objectDetector.process(inputImage))
            } catch (e: Exception) {
                emptyList()
            }

            val imageLabels = try {
                Tasks.await(imageLabeler.process(inputImage))
            } catch (e: Exception) {
                emptyList()
            }

            val allLabelTexts = mutableListOf<String>()

            for (obj in detectedObjects) {
                for (label in obj.labels) {
                    allLabelTexts.add(label.text.lowercase())
                }
            }

            for (lbl in imageLabels) {
                allLabelTexts.add(lbl.text.lowercase())
            }

            val personKeywords = listOf("person", "human", "man", "woman", "boy", "girl", "child", "people")
            val petKeywords = listOf("dog", "cat", "pet", "animal", "canine", "feline", "puppy", "kitten", "mammal", "bird", "carnivore", "fauna")

            val hasPerson = allLabelTexts.any { label -> personKeywords.any { label.contains(it) } }
            val hasPet = allLabelTexts.any { label -> petKeywords.any { label.contains(it) } }

            // 二階段判斷邏輯：
            // - 若識別只有人類 (hasPerson && !hasPet)：視為主人在家，攔截推播通知 (進入冷卻，不警報)
            // - 若有寵物 (hasPet) 或未辨識出人類 (!hasPerson)：正常發送事件推播
            val shouldSuppress = hasPerson && !hasPet

            val summaryText = when {
                hasPerson && hasPet -> "人類 + 寵物 🐶 (發送警報)"
                hasPerson -> "主人在家 (純人類已攔截推播)"
                hasPet -> "偵測到寵物 🐶🐱 (發送警報)"
                allLabelTexts.isNotEmpty() -> "畫面異動 [${allLabelTexts.distinct().take(3).joinToString()}]"
                else -> "動態異動 (未特別分類)"
            }

            Log.d(TAG, "ML Kit Result: hasPerson=$hasPerson, hasPet=$hasPet, suppress=$shouldSuppress, labels=$allLabelTexts")

            MlKitAnalysisResult(
                hasPerson = hasPerson,
                hasPet = hasPet,
                detectedLabels = allLabelTexts.distinct(),
                shouldSuppressNotification = shouldSuppress,
                summaryText = summaryText
            )
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit analysis failed", e)
            MlKitAnalysisResult(
                hasPerson = false,
                hasPet = false,
                detectedLabels = emptyList(),
                shouldSuppressNotification = false,
                summaryText = "畫面異動"
            )
        }
    }
}
