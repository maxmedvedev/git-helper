package com.max.medvedev.githelper.commitLinks

import org.junit.Assert.assertEquals
import org.junit.Test

class CommitHashShapeTest {
    private fun hashes(line: String): List<String> = findCommitHashes(line).map { line.substring(it) }

    @Test
    fun `finds short and full hashes`() {
        val full = "5b0248fa65f2f" + "0".repeat(27)
        assertEquals(listOf("5b0248fa65f2f", full), hashes("5b0248fa65f2f IJPL-261090 make it public, see $full"))
    }

    @Test
    fun `finds hashes in git output forms`() {
        assertEquals(listOf("ee0fded"), hashes("commit ee0fded (HEAD -> master)"))
        assertEquals(listOf("ee0fded"), hashes("[master ee0fded] subject"))
        assertEquals(listOf("ee0fded"), hashes("see `ee0fded`."))
        assertEquals(listOf("ee0fded", "d298f22"), hashes("ee0fded..d298f22"))
    }

    @Test
    fun `rejects digit-only words and wrong lengths`() {
        assertEquals(emptyList<String>(), hashes("20261119 1234567"))
        assertEquals(emptyList<String>(), hashes("abc123"))
        assertEquals(emptyList<String>(), hashes("a".repeat(41)))
    }

    @Test
    fun `rejects hex inside a longer token`() {
        assertEquals(emptyList<String>(), hashes("0x1f2e3d4c"))
        assertEquals(emptyList<String>(), hashes("/tmp/abcdef12/file"))
        assertEquals(emptyList<String>(), hashes("https://example.com/commit/abcdef12"))
        assertEquals(emptyList<String>(), hashes("123e4567-e89b-12d3-a456-426614174000"))
        assertEquals(emptyList<String>(), hashes("#deadbeef"))
        assertEquals(emptyList<String>(), hashes("abcdef12.txt"))
        assertEquals(emptyList<String>(), hashes("cafebabe_1"))
    }
}
