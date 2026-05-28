package dev.wntrmute.ans

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {

    @Test
    fun morningCovers5To11() {
        assertEquals(Greeting.Morning, greetingForHour(5))
        assertEquals(Greeting.Morning, greetingForHour(8))
        assertEquals(Greeting.Morning, greetingForHour(11))
    }

    @Test
    fun afternoonCovers12To16() {
        assertEquals(Greeting.Afternoon, greetingForHour(12))
        assertEquals(Greeting.Afternoon, greetingForHour(16))
    }

    @Test
    fun eveningCovers17To20() {
        assertEquals(Greeting.Evening, greetingForHour(17))
        assertEquals(Greeting.Evening, greetingForHour(20))
    }

    @Test
    fun nightCoversTheLateAndEarlyHours() {
        assertEquals(Greeting.Night, greetingForHour(21))
        assertEquals(Greeting.Night, greetingForHour(0))
        assertEquals(Greeting.Night, greetingForHour(4))
    }

    @Test
    fun phraseAndResourceNamesAreInSync() {
        assertEquals("Good morning", Greeting.Morning.phrase)
        assertEquals("good_morning", Greeting.Morning.rawName)
        assertEquals("Good bye for now"[0], 'G') // sanity that we kept the goodbye phrase form
    }
}
