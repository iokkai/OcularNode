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
        assertEquals(NotificationCategory.HUMAN_AND_ACTIVITY, LabelMapper.getCategory("person"))
        assertEquals(NotificationCategory.HUMAN_AND_ACTIVITY, LabelMapper.getCategory("man"))
        assertEquals(NotificationCategory.HUMAN_AND_ACTIVITY, LabelMapper.getCategory("woman"))
        assertEquals(NotificationCategory.HUMAN_AND_ACTIVITY, LabelMapper.getCategory("child"))
        assertEquals(NotificationCategory.HUMAN_AND_ACTIVITY, LabelMapper.getCategory("running"))
    }

    @Test
    fun `maps pet and animal keywords to PET_AND_ANIMAL`() {
        assertEquals(NotificationCategory.PET_AND_ANIMAL, LabelMapper.getCategory("dog"))
        assertEquals(NotificationCategory.PET_AND_ANIMAL, LabelMapper.getCategory("cat"))
        assertEquals(NotificationCategory.PET_AND_ANIMAL, LabelMapper.getCategory("bird"))
        assertEquals(NotificationCategory.PET_AND_ANIMAL, LabelMapper.getCategory("puppy"))
    }

    @Test
    fun `maps vehicle keywords to VEHICLE_AND_TRANSPORT`() {
        assertEquals(NotificationCategory.VEHICLE_AND_TRANSPORT, LabelMapper.getCategory("car"))
        assertEquals(NotificationCategory.VEHICLE_AND_TRANSPORT, LabelMapper.getCategory("motorcycle"))
        assertEquals(NotificationCategory.VEHICLE_AND_TRANSPORT, LabelMapper.getCategory("bicycle"))
        assertEquals(NotificationCategory.VEHICLE_AND_TRANSPORT, LabelMapper.getCategory("truck"))
    }

    @Test
    fun `maps household items to HOUSEHOLD_ITEM`() {
        assertEquals(NotificationCategory.HOUSEHOLD_ITEM, LabelMapper.getCategory("chair"))
        assertEquals(NotificationCategory.HOUSEHOLD_ITEM, LabelMapper.getCategory("couch"))
        assertEquals(NotificationCategory.HOUSEHOLD_ITEM, LabelMapper.getCategory("laptop"))
        assertEquals(NotificationCategory.HOUSEHOLD_ITEM, LabelMapper.getCategory("bottle"))
    }

    @Test
    fun `unknown labels fallback safely to OTHER`() {
        assertEquals(NotificationCategory.OTHER, LabelMapper.getCategory("completely_unknown_alien_object"))
        assertEquals(NotificationCategory.OTHER, LabelMapper.getCategory(""))
    }

    @Test
    fun `category filter allows enabled categories and blocks disabled categories`() {
        val enabledCategories = setOf(NotificationCategory.HUMAN_AND_ACTIVITY)

        // When "person" is detected -> category is enabled -> pass
        val personCategory = LabelMapper.getCategory("person")
        assertTrue(enabledCategories.contains(personCategory))

        // When "dog" is detected -> category is disabled -> blocked
        val petCategory = LabelMapper.getCategory("dog")
        assertFalse(enabledCategories.contains(petCategory))

        // When "chair" is detected -> category is disabled -> blocked
        val itemCategory = LabelMapper.getCategory("chair")
        assertFalse(enabledCategories.contains(itemCategory))
    }
}
