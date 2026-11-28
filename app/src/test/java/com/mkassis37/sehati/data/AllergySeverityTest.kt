package com.mkassis37.sehati.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllergySeverityTest {
    @Test
    fun recognizesArabicLifeThreateningSeverityAndCommonVariants() {
        assertTrue("مهددة للحياة".isLifeThreateningAllergySeverity())
        assertTrue("مهدده للحياه".isLifeThreateningAllergySeverity())
        assertTrue("  مهددة للحياة  ".isLifeThreateningAllergySeverity())
        assertTrue("حساسية مهددة للحياة".isLifeThreateningAllergySeverity())
    }

    @Test
    fun recognizesEnglishLifeThreateningSeverityAndAnaphylaxis() {
        assertTrue("Life-threatening".isLifeThreateningAllergySeverity())
        assertTrue("life threatening".isLifeThreateningAllergySeverity())
        assertTrue("Anaphylaxis".isLifeThreateningAllergySeverity())
    }

    @Test
    fun lifeThreateningSeverityIsAlsoSevere() {
        assertTrue("مهددة للحياة".isSevereAllergySeverity())
        assertTrue("شديدة".isSevereAllergySeverity())
        assertTrue("severe".isSevereAllergySeverity())
        assertTrue("critical".isSevereAllergySeverity())
    }

    @Test
    fun doesNotPromoteModerateMildOrUnknownSeverity() {
        assertFalse("متوسطة".isLifeThreateningAllergySeverity())
        assertFalse("خفيفة".isLifeThreateningAllergySeverity())
        assertFalse("غير معروفة".isLifeThreateningAllergySeverity())
        assertFalse("متوسطة".isSevereAllergySeverity())
        assertFalse("غير معروفة".isSevereAllergySeverity())
    }
}
