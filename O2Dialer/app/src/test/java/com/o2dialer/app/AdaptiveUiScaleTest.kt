package com.o2dialer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveUiScaleTest {
    @Test fun regularAndLargeScreensRetainOriginalScale() {
        assertEquals(1f, adaptiveUiScale(390), .0001f)
        assertEquals(1f, adaptiveUiScale(480), .0001f)
    }
    @Test fun smallerScreensUseProportionalScale() {
        assertEquals(360f / 390, adaptiveUiScale(360), .0001f)
        assertTrue(adaptiveUiScale(320) < adaptiveUiScale(360))
    }
    @Test fun narrowScreensKeepReadableMinimum() {
        assertEquals(.86f, adaptiveUiScale(240), .0001f)
        assertEquals(1f, adaptiveUiScale(0), .0001f)
    }
}
