package com.adnan.memora.ui.screens

import org.junit.Assert.*
import org.junit.Test

class GridDensityTest {
    @Test fun defaultIsTwoColumns() { assertEquals(2, GridDensity.DEFAULT) }
    @Test fun pinchTogetherAddsColumns() { assertEquals(3, GridDensity.step(2, 0.7f)) }
    @Test fun spreadFingersRemovesColumns() { assertEquals(2, GridDensity.step(3, 1.4f)) }
    @Test fun smallMotionDoesNotChangeDensity() { assertEquals(3, GridDensity.step(3, 0.95f)) }
    @Test fun columnBoundsAreEnforced() { assertEquals(1, GridDensity.saved(-10)); assertEquals(8, GridDensity.saved(20)) }
    @Test fun canZoomToOneColumn() { assertEquals(1, GridDensity.step(1, 1.6f)) }
    @Test fun cannotExceedEightColumns() { assertEquals(8, GridDensity.step(8, 0.5f)) }
    @Test fun narrowWindowClampsWithoutChangingSavedValue() {
        val preference = 8
        assertEquals(5, GridDensity.effective(preference, 360f))
        assertEquals(8, preference)
        assertEquals(8, GridDensity.effective(preference, 900f))
    }
    @Test fun tinyWindowHasAtLeastOneColumn() { assertEquals(1, GridDensity.effective(6, 30f)) }
    @Test fun localFavoriteRuleRejectsCloudUri() {
        assertTrue(PhoneFavoriteRules.isLocal("content")); assertFalse(PhoneFavoriteRules.isLocal("memora")); assertFalse(PhoneFavoriteRules.isLocal(null))
    }
}
