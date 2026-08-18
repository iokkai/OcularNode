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
    fun compareSemVer_correctlyComparesVersions() {
        // Higher minor/patch
        assertTrue(UpdateManager.compareSemVer("v1.3.0", "1.2.0") > 0)
        assertTrue(UpdateManager.compareSemVer("1.10.0", "1.2.0") > 0)
        assertTrue(UpdateManager.compareSemVer("v1.2.1", "v1.2.0") > 0)
        assertTrue(UpdateManager.compareSemVer("2.0.0", "1.99.99") > 0)
        assertTrue(UpdateManager.compareSemVer("0.2", "0.1") > 0)

        // Equal
        assertEquals(0, UpdateManager.compareSemVer("v1.2.0", "1.2.0"))
        assertEquals(0, UpdateManager.compareSemVer("1.0", "v1.0.0"))
        assertEquals(0, UpdateManager.compareSemVer("2.1.0-beta", "2.1.0"))

        // Lower
        assertTrue(UpdateManager.compareSemVer("1.1.10", "1.2.0") < 0)
        assertTrue(UpdateManager.compareSemVer("v1.2.0", "v1.2.1") < 0)
        assertTrue(UpdateManager.compareSemVer("0.1", "0.2") < 0)
    }

    @Test
    fun isRemoteNewer_withSemanticVersionComparison_handlesMultiDigitCorrectly() {
        // v1.1.10 is OLDER than current 1.2.0 (previously failed when stripping dots)
        assertFalse(
            UpdateManager.isRemoteNewer(
                remoteTagName = "v1.1.10",
                currentVersionName = "1.2.0",
                currentVersionCode = 120L
            )
        )

        // v1.10.0 is NEWER than current 1.2.0
        assertTrue(
            UpdateManager.isRemoteNewer(
                remoteTagName = "v1.10.0",
                currentVersionName = "1.2.0",
                currentVersionCode = 120L
            )
        )

        // v0.2 is NEWER than current 0.1
        assertTrue(
            UpdateManager.isRemoteNewer(
                remoteTagName = "v0.2",
                currentVersionName = "0.1",
                currentVersionCode = 1L
            )
        )

        // v0.0.7 is NEWER than local debug version 0.0.1-debug
        assertTrue(
            UpdateManager.isRemoteNewer(
                remoteTagName = "v0.0.7",
                currentVersionName = "0.0.1-debug",
                currentVersionCode = 1L
            )
        )

        // v0.0.7 is NEWER than local release version 0.0.1-local
        assertTrue(
            UpdateManager.isRemoteNewer(
                remoteTagName = "v0.0.7",
                currentVersionName = "0.0.1-local",
                currentVersionCode = 1L
            )
        )
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
