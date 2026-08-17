package io.github.iokkai.ocularnode.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun parseVersionCodeFromTag_correctlyExtractsDigits() {
        assertEquals(120L, UpdateManager.parseVersionCodeFromTag("v1.2.0"))
        assertEquals(201L, UpdateManager.parseVersionCodeFromTag("v2.0.1"))
        assertEquals(100L, UpdateManager.parseVersionCodeFromTag("1.0.0"))
        assertEquals(10L, UpdateManager.parseVersionCodeFromTag("v1.0"))
        assertEquals(0L, UpdateManager.parseVersionCodeFromTag("release-alpha"))
        assertEquals(0L, UpdateManager.parseVersionCodeFromTag(""))
    }

    @Test
    fun isRemoteNewer_withHigherRemoteVersionCode_returnsTrue() {
        val result = UpdateManager.isRemoteNewer(
            remoteTagName = "v1.3.0",
            remoteVersionCode = 130L,
            currentVersionName = "1.2.0",
            currentVersionCode = 120L
        )
        assertTrue(result)
    }

    @Test
    fun isRemoteNewer_withSameOrOlderVersionCode_returnsFalse() {
        // Same version code
        val sameResult = UpdateManager.isRemoteNewer(
            remoteTagName = "v1.2.0",
            remoteVersionCode = 120L,
            currentVersionName = "1.2.0",
            currentVersionCode = 120L
        )
        assertFalse(sameResult)

        // Older version code
        val olderResult = UpdateManager.isRemoteNewer(
            remoteTagName = "v1.1.0",
            remoteVersionCode = 110L,
            currentVersionName = "1.2.0",
            currentVersionCode = 120L
        )
        assertFalse(olderResult)
    }

    @Test
    fun isRemoteNewer_withZeroVersionCode_fallsBackToStringComparison() {
        // Different tag name when codes are 0
        val resultDiff = UpdateManager.isRemoteNewer(
            remoteTagName = "beta-2",
            remoteVersionCode = 0L,
            currentVersionName = "beta-1",
            currentVersionCode = 0L
        )
        assertTrue(resultDiff)

        // Same tag name when codes are 0
        val resultSame = UpdateManager.isRemoteNewer(
            remoteTagName = "v1.0.0",
            remoteVersionCode = 0L,
            currentVersionName = "v1.0.0",
            currentVersionCode = 0L
        )
        assertFalse(resultSame)
    }

    @Test
    fun updateInstallStage_hasExpectedStages() {
        val stages = UpdateInstallStage.entries
        assertTrue(stages.contains(UpdateInstallStage.IDLE))
        assertTrue(stages.contains(UpdateInstallStage.DOWNLOADING))
        assertTrue(stages.contains(UpdateInstallStage.VERIFYING))
        assertTrue(stages.contains(UpdateInstallStage.INSTALLING_SILENT))
        assertTrue(stages.contains(UpdateInstallStage.PROMPTING_SYSTEM_INSTALL))
        assertTrue(stages.contains(UpdateInstallStage.COMPLETED))
        assertTrue(stages.contains(UpdateInstallStage.FAILED))
    }
}
