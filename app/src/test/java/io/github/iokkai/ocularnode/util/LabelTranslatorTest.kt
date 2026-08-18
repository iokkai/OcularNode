package io.github.iokkai.ocularnode.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * 測試 AI 標籤多國語言翻譯與 Fallback 機制 (Label Translator & Locale Mapping)。
 */
class LabelTranslatorTest {

    @Test
    fun `translate falls back cleanly to raw label when dictionary is unloaded or label missing`() {
        // When no dictionary is loaded, translate should return original label safely without throwing Exception
        val result = LabelTranslator.translate("Person", Locale.TRADITIONAL_CHINESE)
        assertEquals("Person", result)

        val emptyResult = LabelTranslator.translate("", Locale.ENGLISH)
        assertEquals("", emptyResult)
    }
}
