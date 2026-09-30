package com.omnimind.domain.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class StorageUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFormatBytes() {
        assertEquals("500 KB", StorageUtils.formatBytes(500 * 1024L))
        assertEquals("25.0 MB", StorageUtils.formatBytes(25 * 1024 * 1024L))
        assertEquals("2.45 GB", StorageUtils.formatBytes((2.45 * 1024 * 1024 * 1024L).toLong()))
    }

    @Test
    fun testComputeSha256_knownContent() = runBlocking {
        val testFile = tempFolder.newFile("test_sha256.txt")
        testFile.writeText("OmniMind local LLM inference on ARM64")

        val hash = StorageUtils.computeSha256(testFile)
        assertNotNull(hash)
        assertEquals(64, hash.length)

        // Same content must give exact same hash
        val hash2 = StorageUtils.computeSha256(testFile)
        assertEquals(hash, hash2)
    }

    @Test
    fun testComputeSha256_cancellation() {
        val largeFile = tempFolder.newFile("large_test.bin")
        // Write 1 MB
        largeFile.writeBytes(ByteArray(1024 * 1024))

        val cancelFlag = AtomicBoolean(true)
        try {
            runBlocking {
                StorageUtils.computeSha256(largeFile, cancelFlag)
            }
            fail("Expected InterruptedException upon cancellation")
        } catch (e: InterruptedException) {
            assertTrue(e.message?.contains("cancelled") == true)
        }
    }
}
