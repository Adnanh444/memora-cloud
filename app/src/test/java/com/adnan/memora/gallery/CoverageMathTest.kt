package com.adnan.memora.gallery

import org.junit.Assert.*
import org.junit.Test

class CoverageMathTest {
    private val media = listOf(CoverageItem("p1", false), CoverageItem("p2", false), CoverageItem("v1", true))
    @Test fun totalsSplitPhotosAndVideos() {
        val c = CoverageMath.calculate(media, emptySet())
        assertEquals(2, c.photos.total); assertEquals(1, c.videos.total)
    }
    @Test fun verifiedCopiesOnlyCountTheCorrespondingLocalItem() {
        val c = CoverageMath.calculate(media, setOf("p1", "cloud:another-device"))
        assertEquals(1, c.photos.verified); assertEquals(0, c.videos.verified)
    }
    @Test fun cloudOnlyCopiesDoNotInflatePhoneCoverage() {
        assertEquals(0f, CoverageMath.calculate(media, setOf("remote-only")).photos.fraction)
    }
    @Test fun repeatedKeysDoNotInflateTotals() { assertEquals(2, CoverageMath.calculate(media + media, setOf("p1")).photos.total) }
    @Test fun emptyLibraryNeverShowsFullBackup() { assertEquals(0f, CoverageMath.calculate(emptyList(), setOf("x")).photos.fraction) }
    @Test fun progressIsBounded() { assertEquals(1f, CoverageCount(2, 9).fraction); assertEquals(0f, CoverageCount(2, -9).fraction) }
    @Test fun fractionUsesPhoneDenominator() { assertEquals(.5f, CoverageMath.calculate(media, setOf("p1")).photos.fraction) }
    @Test fun upwardScrollCollapses() { assertTrue(CollapseRules.next(false, -60f, 48f)) }
    @Test fun downwardScrollExpands() { assertFalse(CollapseRules.next(true, 60f, 48f)) }
    @Test fun smallScrollDoesNotFlicker() { assertTrue(CollapseRules.next(true, 8f, 48f)); assertFalse(CollapseRules.next(false, -8f, 48f)) }
}
