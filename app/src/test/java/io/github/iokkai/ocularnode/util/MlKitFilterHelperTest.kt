package io.github.iokkai.ocularnode.util

import io.github.iokkai.ocularnode.data.LabelMapper
import io.github.iokkai.ocularnode.data.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 測試 ML Kit AI 標籤分類與類別過濾器 (LabelMapper & Notification Category Filtering)。
 */
class MlKitFilterHelperTest {

    @Test
    fun `maps person and human related keywords to HUMAN_AND_ACTIVITY`() {
        assertEquals(NotificationCategory.HUMAN_AND_ACTIVITY, LabelMapper.getCategoryForLabel("person"))
        assertEquals(NotificationCategory.HUMAN_AND_ACTIVITY, LabelMapper.getCategoryForLabel("man"))
        assertEquals(NotificationCategory.HUMAN_AND_ACTIVITY, LabelMapper.getCategoryForLabel("woman"))
        assertEquals(NotificationCategory.HUMAN_AND_ACTIVITY, LabelMapper.getCategoryForLabel("child"))
        assertEquals(NotificationCategory.HUMAN_AND_ACTIVITY, LabelMapper.getCategoryForLabel("running"))
    }

    @Test
    fun `maps pet and animal keywords to PET_AND_ANIMAL`() {
        assertEquals(NotificationCategory.PET_AND_ANIMAL, LabelMapper.getCategoryForLabel("dog"))
        assertEquals(NotificationCategory.PET_AND_ANIMAL, LabelMapper.getCategoryForLabel("cat"))
        assertEquals(NotificationCategory.PET_AND_ANIMAL, LabelMapper.getCategoryForLabel("bird"))
        assertEquals(NotificationCategory.PET_AND_ANIMAL, LabelMapper.getCategoryForLabel("puppy"))
    }

    @Test
    fun `maps vehicle keywords to VEHICLE_AND_TRANSPORT`() {
        assertEquals(NotificationCategory.VEHICLE_AND_TRANSPORT, LabelMapper.getCategoryForLabel("car"))
        assertEquals(NotificationCategory.VEHICLE_AND_TRANSPORT, LabelMapper.getCategoryForLabel("motorcycle"))
        assertEquals(NotificationCategory.VEHICLE_AND_TRANSPORT, LabelMapper.getCategoryForLabel("bicycle"))
        assertEquals(NotificationCategory.VEHICLE_AND_TRANSPORT, LabelMapper.getCategoryForLabel("truck"))
    }

    @Test
    fun `maps household items to HOUSEHOLD_ITEM`() {
        assertEquals(NotificationCategory.HOUSEHOLD_ITEM, LabelMapper.getCategoryForLabel("chair"))
        assertEquals(NotificationCategory.HOUSEHOLD_ITEM, LabelMapper.getCategoryForLabel("couch"))
        assertEquals(NotificationCategory.HOUSEHOLD_ITEM, LabelMapper.getCategoryForLabel("laptop"))
        assertEquals(NotificationCategory.HOUSEHOLD_ITEM, LabelMapper.getCategoryForLabel("bottle"))
    }

    @Test
    fun `unknown labels fallback safely to OTHER`() {
        assertEquals(NotificationCategory.OTHER, LabelMapper.getCategoryForLabel("completely_unknown_alien_object"))
        assertEquals(NotificationCategory.OTHER, LabelMapper.getCategoryForLabel(""))
    }

    @Test
    fun `category filter allows enabled categories and blocks disabled categories`() {
        val enabledCategories = setOf(NotificationCategory.HUMAN_AND_ACTIVITY)

        // When "person" is detected -> category is enabled -> pass
        val personCategory = LabelMapper.getCategoryForLabel("person")
        assertTrue(enabledCategories.contains(personCategory))

        // When "dog" is detected -> category is disabled -> blocked
        val petCategory = LabelMapper.getCategoryForLabel("dog")
        assertFalse(enabledCategories.contains(petCategory))

        // When "chair" is detected -> category is disabled -> blocked
        val itemCategory = LabelMapper.getCategoryForLabel("chair")
        assertFalse(enabledCategories.contains(itemCategory))
    }
}
