package com.mkassis37.sehati.drive

import java.io.IOException

internal fun verifyDriveBackup(remoteSize: Long, remoteSha256: String, localSize: Long, localSha256: String) {
    if (remoteSize != localSize || (remoteSha256.isNotBlank() && !remoteSha256.equals(localSha256, ignoreCase = true))) {
        throw IOException("Drive backup metadata does not match the encrypted file")
    }
}
