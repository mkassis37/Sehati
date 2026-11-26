package com.mkassis37.sehati.drive

import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Test

class DriveBackupIntegrityTest {
    @Test
    fun acceptsMatchingSizeAndChecksum() {
        verifyDriveBackup(123, "abc", 123, "ABC")
    }

    @Test
    fun stillRequiresMatchingSizeWhenDriveOmitsChecksum() {
        verifyDriveBackup(123, "", 123, "abc")
        assertThrows(IOException::class.java) { verifyDriveBackup(122, "", 123, "abc") }
    }

    @Test
    fun rejectsMismatchedChecksum() {
        assertThrows(IOException::class.java) { verifyDriveBackup(123, "def", 123, "abc") }
    }
}
