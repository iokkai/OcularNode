package io.github.iokkai.ocularnode.util

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.data.LabelMapper
import io.github.iokkai.ocularnode.data.NotificationCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class MlKitAnalysisResult(
    val hasPerson: Boolean,
    val hasPet: Boolean,
    val detectedLabels: List<String>,
    val shouldSuppressNotification: Boolean, // true if Person only (主人在家 -> 攔截推播)
    val shouldTriggerRecording: Boolean,
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

    /**
     * @param enabledCategories: 請從 ViewModel 或外部傳入當前使用者開啟的類別，切勿在此函式內讀取 DataStore！
     */
    suspend fun analyzeFrame(
        context: Context, 
        bitmap: Bitmap, 
        enabledCategories: Set<NotificationCategory>,
        enabledRecordingCategories: Set<NotificationCategory>
    ): MlKitAnalysisResult = withContext(Dispatchers.IO) {
        var scaledBitmap: Bitmap? = null
        try {
            // 初始化多國語言資產字典 (若尚未載入)
            LabelTranslator.loadDictionaryForLocale(context)

            // 適度降採樣至長邊最大 640px，降低 ML Kit 推論耗時與記憶體佔用達 50% 以上
            val maxDimension = 640f
            val srcWidth = bitmap.width.toFloat()
            val srcHeight = bitmap.height.toFloat()
            val scaleFactor = if (Math.max(srcWidth, srcHeight) > maxDimension) {
                maxDimension / Math.max(srcWidth, srcHeight)
            } else {
                1.0f
            }
            val targetBitmap = if (scaleFactor < 1.0f) {
                val tw = (srcWidth * scaleFactor).toInt().coerceAtLeast(1)
                val th = (srcHeight * scaleFactor).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, tw, th, true).also { scaledBitmap = it }
            } else {
                bitmap
            }

            val inputImage = InputImage.fromBitmap(targetBitmap, 0)

            // 使用 kotlinx.coroutines.tasks.await() 來處理 Play Services Task
            val detectedObjects = try {
                objectDetector.process(inputImage).await()
            } catch (e: Exception) {
                emptyList()
            }

            val imageLabels = try {
                imageLabeler.process(inputImage).await()
            } catch (e: Exception) {
                emptyList()
            }

            // 統合所有的 Label，統一轉小寫並去除重複
            val allLabelTexts = (detectedObjects.flatMap { it.labels }.map { it.text } + imageLabels.map { it.text })
                                .map { it.lowercase() }
                                .distinct()

            val personKeywords = listOf("person", "human", "man", "woman", "boy", "girl", "child", "people", "hand", "dude", "clown", "face", "head", "portrait", "hair", "skin", "nose", "eye", "mouth", "smile", "skateboarder", "deejay", "grandparent", "crowd", "musician", "singer", "superhero", "model", "groom", "baby", "bride", "joker", "supervillain")
            val petKeywords = listOf(
                "dog", "cat", "pet", "animal", "canine", "feline", "puppy", "kitten", "mammal", 
                "bird", "carnivore", "fauna", "shetland sheepdog", "gerbil", "bear", "dalmatian", 
                "ragdoll", "cairn terrier", "pixie-bob", "horse", "penguin", "shikoku", "duck", 
                "turtle", "crocodile", "bull", "butterfly", "larva", "sphynx", "basset hound", "seal"
            )

            val hasPerson = allLabelTexts.any { label -> personKeywords.any { label.contains(it) } }
            val hasPet = allLabelTexts.any { label -> petKeywords.any { label.contains(it) } }

            // 各分類獨立開關判斷：
            // 將偵測到的標籤映射至分類，並透過 LabelTranslator 進行多國語言在地化翻譯
            val detectedCategories = mutableSetOf<NotificationCategory>()
            val translatedLabels = allLabelTexts.map { LabelTranslator.translate(it) }

            for (label in allLabelTexts) {
                val cat = LabelMapper.getCategory(label)
                detectedCategories.add(cat)
            }

            val enabledMatchedNotifCats = detectedCategories.filter { enabledCategories.contains(it) }
            val disabledMatchedNotifCats = detectedCategories.filter { !enabledCategories.contains(it) }

            val enabledMatchedRecCats = detectedCategories.filter { enabledRecordingCategories.contains(it) }
            val disabledMatchedRecCats = detectedCategories.filter { !enabledRecordingCategories.contains(it) }

            // 1. 推播決策：只要命中任何一個開啟的分類即發出通知；若所有命中的分類皆被使用者關閉則攔截
            val shouldSuppressNotification = if (detectedCategories.isNotEmpty()) {
                enabledMatchedNotifCats.isEmpty()
            } else {
                !enabledCategories.contains(NotificationCategory.OTHER)
            }

            // 2. 錄影決策：只要命中任何一個開啟錄影的分類即啟動錄影
            val shouldTriggerRecording = if (detectedCategories.isNotEmpty()) {
                enabledMatchedRecCats.isNotEmpty()
            } else {
                enabledRecordingCategories.contains(NotificationCategory.OTHER)
            }

            // 3. 組合 summaryText 顯示在地化分類與標籤內容
            val labelsPreview = translatedLabels.take(5).joinToString(", ")
            val summaryText = if (!shouldSuppressNotification) {
                val enabledCatNames = enabledMatchedNotifCats.map { LabelTranslator.getCategoryDisplayName(context, it) }.distinct().joinToString("/")
                if (enabledCatNames.isNotBlank() && labelsPreview.isNotBlank()) {
                    "[$enabledCatNames] $labelsPreview"
                } else if (labelsPreview.isNotBlank()) {
                    context.getString(R.string.ml_detected_prefix, labelsPreview)
                } else {
                    context.getString(R.string.ml_unclassified)
                }
            } else {
                val disabledCatNames = disabledMatchedNotifCats.map { LabelTranslator.getCategoryDisplayName(context, it) }.distinct().joinToString("/")
                if (disabledCatNames.isNotBlank()) {
                    context.getString(R.string.ml_filtered_with_cat, disabledCatNames, labelsPreview)
                } else {
                    context.getString(R.string.ml_filtered_generic)
                }
            }

            AppLogger.d(TAG, "原始物件標籤: $allLabelTexts")
            AppLogger.d(TAG, "翻譯標籤: $translatedLabels")
            AppLogger.d(TAG, "偵測分類: $detectedCategories, 命中開啟推播分類: $enabledMatchedNotifCats, 關閉分類: $disabledMatchedNotifCats")
            AppLogger.d(TAG, "最終決定: suppressNotif=$shouldSuppressNotification, triggerRec=$shouldTriggerRecording (摘要: $summaryText)")

            MlKitAnalysisResult(
                hasPerson = hasPerson,
                hasPet = hasPet,
                detectedLabels = translatedLabels,
                shouldSuppressNotification = shouldSuppressNotification,
                shouldTriggerRecording = shouldTriggerRecording,
                summaryText = summaryText
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "ML Kit 分析失敗", e)
            MlKitAnalysisResult(
                hasPerson = false,
                hasPet = false,
                detectedLabels = emptyList(),
                shouldSuppressNotification = false,
                shouldTriggerRecording = true, // 發生錯誤時預設允許錄影
                summaryText = context.getString(R.string.ml_unclassified)
            )
        } finally {
            scaledBitmap?.recycle()
        }
    }
}
