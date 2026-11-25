package com.mkassis37.sehati.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUpdaterTest {
    @Test
    fun onlyAcceptsSehatiGithubReleaseAssetUrls() {
        val official = "https://github.com/mkassis37/Sehati/releases/download/v0.3.0/app-release.apk"
        assertTrue(isTrustedUpdateAsset(official))
        assertFalse(isTrustedUpdateAsset(official.replace("https:", "http:")))
        assertFalse(isTrustedUpdateAsset(official.replace("mkassis37", "other-project")))
        assertFalse(isTrustedUpdateAsset(official.replace("github.com", "github.com.example")))
        assertFalse(isTrustedUpdateAsset(official.replace("github.com", "someone@github.com")))
    }
}
