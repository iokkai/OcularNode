package io.github.iokkai.ocularnode.util

import android.content.Context
import io.github.iokkai.ocularnode.data.NotificationCategory
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * ML Kit 物件標籤與分類多國語言翻譯器 (LabelTranslator)
 * 完全透過 Assets (assets/labels/*.json) 與 Android 官方字串資源 (R.string) 進行在地化，無任何寫死字串。
 */
object LabelTranslator {

    private const val TAG = "LabelTranslator"
    private val labelDictionaries = ConcurrentHashMap<String, Map<String, String>>()
    private val loadedLanguages = ConcurrentHashMap.newKeySet<String>()

    /**
     * 依據語言動態載入對應 Assets JSON 字典
     */
    fun loadDictionaryForLocale(context: Context, locale: Locale = Locale.getDefault()) {
        val langKey = getLanguageKey(locale)
        if (loadedLanguages.contains(langKey)) return

        try {
            val fileName = "labels/labels_$langKey.json"
            val assetManager = context.assets
            val jsonStr = assetManager.open(fileName).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k.lowercase()] = jsonObject.getString(k)
            }
            labelDictionaries[langKey] = map
            loadedLanguages.add(langKey)
            AppLogger.d(TAG, "已成功載入 $langKey 標籤字典，共 ${map.size} 個詞彙")
        } catch (e: Exception) {
            // 若無特定語系資產檔，仍標記已載入避免反覆開啟失敗
            loadedLanguages.add(langKey)
            AppLogger.w(TAG, "未找到 labels_$langKey.json 資產檔或載入失敗: ${e.message}")
        }
    }

    /**
     * 翻譯 ML Kit 標籤 (若字典無對應詞彙則回傳原始英文標籤)
     */
    fun translate(label: String, locale: Locale = Locale.getDefault()): String {
        val lower = label.lowercase().trim()
        val langKey = getLanguageKey(locale)
        return labelDictionaries[langKey]?.get(lower) ?: label
    }

    /**
     * 取得分類的在地化顯示名稱 (從 Android 官方資源檔 strings.xml 讀取)
     */
    fun getCategoryDisplayName(context: Context, category: NotificationCategory): String {
        return category.getLocalizedTitle(context)
    }

    private fun getLanguageKey(locale: Locale): String {
        val lang = locale.language.lowercase()
        return when (lang) {
            "zh" -> "zh_TW"
            else -> "en"
        }
    }
}
