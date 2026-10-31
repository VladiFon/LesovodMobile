package com.lesovod.kubaturnik.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test
    fun compares() {
        assertTrue(isNewerVersion("1.1.0", "1.0.0"))
        assertTrue(isNewerVersion("1.10.0", "1.9.3"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "1.1.0"))
        assertTrue(isNewerVersion("2", "1.9.9"))
    }
}
