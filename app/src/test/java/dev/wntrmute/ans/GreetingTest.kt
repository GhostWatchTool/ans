package dev.wntrmute.ans

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {

    @Test
    fun morningCovers5To11() {
        assertEquals("Good morning", greetingForHour(5))
        assertEquals("Good morning", greetingForHour(8))
        assertEquals("Good morning", greetingForHour(11))
    }

    @Test
    fun afternoonCovers12To16() {
        assertEquals("Good afternoon", greetingForHour(12))
        assertEquals("Good afternoon", greetingForHour(16))
    }

    @Test
    fun eveningCovers17To20() {
        assertEquals("Good evening", greetingForHour(17))
        assertEquals("Good evening", greetingForHour(20))
    }

    @Test
    fun nightCoversTheLateAndEarlyHours() {
        assertEquals("Good night", greetingForHour(21))
        assertEquals("Good night", greetingForHour(0))
        assertEquals("Good night", greetingForHour(4))
    }
}
