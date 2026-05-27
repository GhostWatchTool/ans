package dev.wntrmute.ans

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberFormatterTest {

    @Test
    fun groupsIntoBlocksOfFive() {
        assertEquals("12345", NumberFormatter.format("12345"))
        assertEquals("12345 67890", NumberFormatter.format("1234567890"))
        assertEquals("12345 6", NumberFormatter.format("123456"))
        assertEquals("", NumberFormatter.format(""))
    }

    @Test
    fun stripsNonDigits() {
        assertEquals("12345 67", NumberFormatter.format("12 34-5a67"))
    }

    @Test
    fun caretAccountsForInsertedSpaces() {
        assertEquals(0, NumberFormatter.caretAfterDigits(0))
        assertEquals(1, NumberFormatter.caretAfterDigits(1))
        assertEquals(5, NumberFormatter.caretAfterDigits(5))
        assertEquals(7, NumberFormatter.caretAfterDigits(6))
        assertEquals(11, NumberFormatter.caretAfterDigits(10))
        assertEquals(13, NumberFormatter.caretAfterDigits(11))
    }

    @Test
    fun digitsBeforeIndexCountsOnlyDigits() {
        assertEquals(3, NumberFormatter.digitsBefore("12345 678", 3))
        assertEquals(5, NumberFormatter.digitsBefore("12345 678", 6)) // past the space
        assertEquals(6, NumberFormatter.digitsBefore("12345 678", 7))
    }

    @Test
    fun groupsReturnsFiveDigitChunks() {
        assertEquals(listOf("12345", "67"), NumberFormatter.groups("12345 67"))
        assertEquals(emptyList<String>(), NumberFormatter.groups("abc"))
    }
}
