package dev.wntrmute.ans

import java.util.Calendar

/**
 * Time-of-day greeting spoken as the lead-in voice behind the tone.
 *
 * Boundaries: morning 05–11, afternoon 12–16, evening 17–20, night 21–04.
 * Pure so it is JVM-unit-testable; the wall-clock variant lives in
 * [currentGreeting].
 */
internal fun greetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..20 -> "Good evening"
    else -> "Good night"
}

/** Greeting for the current local time. Re-evaluated on every pass. */
internal fun currentGreeting(): String =
    greetingForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
