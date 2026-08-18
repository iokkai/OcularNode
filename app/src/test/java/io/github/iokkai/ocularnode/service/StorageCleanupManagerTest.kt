package io.github.iokkai.ocularnode.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 測試儲存空間 FIFO 配額自動清理演算法 (Storage Quota FIFO 20% Cleanup Algorithm)。
 */
class StorageCleanupManagerTest {

    data class MockVideoRecord(
        val id: Long,
        val fileName: String,
        val timestamp: Long,
        val sizeBytes: Long
    )

    class StorageCleanupSimulator(
        val minFreeSpaceMB: Long = 1000L,
        val maxRecordsQuota: Int = 200
    ) {
        val records = mutableListOf<MockVideoRecord>()

        fun evaluateAndCleanup(currentFreeMB: Long): Pair<Int, Long> {
            val isStorageLow = currentFreeMB < minFreeSpaceMB
            val isQuotaExceeded = records.size > maxRecordsQuota

            if (!isStorageLow && !isQuotaExceeded) {
                return 0 to 0L // No cleanup needed
            }

            // Sort by oldest first
            records.sortBy { it.timestamp }

            // Delete oldest 20%
            val purgeCount = (records.size * 0.2).toInt().coerceAtLeast(1)
            var reclaimedBytes = 0L

            val toDelete = records.take(purgeCount)
            for (record in toDelete) {
                reclaimedBytes += record.sizeBytes
            }
            records.removeAll(toDelete)

            return purgeCount to reclaimedBytes
        }
    }

    @Test
    fun `no cleanup performed when storage and quota are healthy`() {
        val simulator = StorageCleanupSimulator(minFreeSpaceMB = 1000, maxRecordsQuota = 200)
        for (i in 1..50) {
            simulator.records.add(MockVideoRecord(i.toLong(), "video_$i.mp4", i * 1000L, 5_000_000L))
        }

        // 5000 MB free (> 1000 MB) and 50 records (< 200)
        val (deletedCount, reclaimedBytes) = simulator.evaluateAndCleanup(currentFreeMB = 5000)
        assertEquals(0, deletedCount)
        assertEquals(0L, reclaimedBytes)
        assertEquals(50, simulator.records.size)
    }

    @Test
    fun `purges exactly oldest 20 percent when storage space is low`() {
        val simulator = StorageCleanupSimulator(minFreeSpaceMB = 1000, maxRecordsQuota = 200)
        for (i in 1..100) {
            simulator.records.add(MockVideoRecord(i.toLong(), "video_$i.mp4", i * 1000L, 10_000_000L))
        }

        // Storage drops to 500 MB (< 1000 MB threshold)
        val (deletedCount, reclaimedBytes) = simulator.evaluateAndCleanup(currentFreeMB = 500)

        // 20% of 100 = 20 records
        assertEquals(20, deletedCount)
        assertEquals(200_000_000L, reclaimedBytes) // 20 * 10 MB = 200 MB
        assertEquals(80, simulator.records.size)

        // Verify remaining records are the newer ones (id 21 to 100)
        assertEquals(21L, simulator.records.first().id)
        assertEquals(100L, simulator.records.last().id)
    }

    @Test
    fun `purges oldest records when count exceeds max quota limit`() {
        val simulator = StorageCleanupSimulator(minFreeSpaceMB = 1000, maxRecordsQuota = 50)
        for (i in 1..60) {
            simulator.records.add(MockVideoRecord(i.toLong(), "video_$i.mp4", i * 1000L, 5_000_000L))
        }

        // 60 records > 50 quota -> triggers cleanup
        val (deletedCount, _) = simulator.evaluateAndCleanup(currentFreeMB = 5000)

        // 20% of 60 = 12 records
        assertEquals(12, deletedCount)
        assertEquals(48, simulator.records.size) // 60 - 12 = 48 (< 50 quota)
    }
}
