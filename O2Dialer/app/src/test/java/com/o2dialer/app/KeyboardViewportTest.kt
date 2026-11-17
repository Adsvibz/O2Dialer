package com.o2dialer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardViewportTest {
    @Test fun keyboardAlreadyResizedWindowNeedsNoAdditionalPadding() {
        assertEquals(0, residualKeyboardOverlap(540, 540, true, 80))
    }
    @Test fun oldOemWithMissingImeInsetsUsesVisibleFrame() {
        assertEquals(300, residualKeyboardOverlap(840, 540, false, 80))
    }
    @Test fun partialResizePadsOnlyRemainingOverlap() {
        assertEquals(80, residualKeyboardOverlap(620, 540, false, 80))
        assertEquals(40, residualKeyboardOverlap(580, 540, true, 80))
    }
    @Test fun ordinarySystemBarIsNotKeyboardWhenInsetsMissing() {
        assertEquals(0, residualKeyboardOverlap(840, 816, false, 80))
        assertEquals(0, residualKeyboardOverlap(520, 540, false, 80))
    }
}
