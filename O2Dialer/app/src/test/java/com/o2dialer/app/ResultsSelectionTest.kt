package com.o2dialer.app

import org.junit.Assert.*
import org.junit.Test

class ResultsSelectionTest {
    private val entries = listOf(
        Entry(name = "Ali", number = "01012345678", stage = Stg.POTENTIAL, status = St.ANSWERED, calledAt = 1),
        Entry(name = "Mona", number = "+201112345678", stage = Stg.POTENTIAL, status = St.ANSWERED, calledAt = 2),
        Entry(name = "Other", number = "01212345678", stage = Stg.NOT_INTERESTED, status = St.ANSWERED),
        Entry(number = "01512345678", stage = Stg.NONE, status = St.NO_ANSWER)
    )

    @Test fun copySelectedCategoryDoesNotIncludeOtherCategoriesOrDependOnSearch() {
        val category = resultCategoryEntries(entries, Stg.POTENTIAL, -1)
        assertEquals(listOf("+201112345678", "01012345678"), resultNumbersForClipboard(category))
        assertEquals(listOf("01012345678"), searchResultEntries(category, "Ali").map { it.number })
        assertEquals(listOf("+201112345678", "01012345678"), resultNumbersForClipboard(category))
    }

    @Test fun searchStaysWithinSelectedStageAndOutcome() {
        val category = resultCategoryEntries(entries, Stg.POTENTIAL, St.ANSWERED)
        assertTrue(searchResultEntries(category, "Other").isEmpty())
        assertEquals("Mona", searchResultEntries(category, "111234").single().name)
        assertTrue(resultCategoryEntries(entries, Stg.POTENTIAL, St.NO_ANSWER).isEmpty())
    }

    @Test fun copyPreservesOriginalNumbersAndOmitsBlankValues() {
        val values = resultNumbersForClipboard(listOf(Entry(number = "01001234567"), Entry(number = " "), Entry(number = "+201001234567")))
        assertEquals("01001234567\n+201001234567", values.joinToString("\n"))
    }
}
