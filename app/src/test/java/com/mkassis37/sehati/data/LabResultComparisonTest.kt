package com.mkassis37.sehati.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabResultComparisonTest {
    @Test
    fun matchesReferenceRangesIncludingOpenEndedRanges() {
        val first = LabResult(memberId = 1, testName = "Synthetic", value = 7.0,
            performedAt = "2026-11-01", referenceHigh = 10.0)
        assertTrue(first.hasSameReferenceRange(first.copy(value = 8.0, performedAt = "2026-11-03")))
        assertFalse(first.hasSameReferenceRange(first.copy(referenceHigh = 11.0)))
        assertFalse(first.hasSameReferenceRange(first.copy(referenceLow = 4.0)))
    }
}
