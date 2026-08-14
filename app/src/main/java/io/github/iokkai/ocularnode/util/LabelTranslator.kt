package io.github.iokkai.ocularnode.util

import android.content.Context
import io.github.iokkai.ocularnode.data.NotificationCategory
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * ML Kit Label Translator
 */
object LabelTranslator {

    private const val TAG = "LabelTranslator"
    private val labelDictionaries = ConcurrentHashMap<String, Map<String, String>>()
    private val loadedLanguages = ConcurrentHashMap.newKeySet<String>()

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
            AppLogger.d(TAG, "Loaded $langKey dict with ${map.size} labels")
        } catch (e: Exception) {
            loadedLanguages.add(langKey)
            AppLogger.w(TAG, "Failed to load labels_$langKey.json: ${e.message}")
        }
    }

    fun translate(label: String, locale: Locale = Locale.getDefault()): String {
        val lower = label.lowercase().trim()
        val langKey = getLanguageKey(locale)
        return labelDictionaries[langKey]?.get(lower) ?: label
    }

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
