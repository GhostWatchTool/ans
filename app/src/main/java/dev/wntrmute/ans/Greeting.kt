package dev.wntrmute.ans

import java.util.Calendar

/**
 * Time-of-day greeting played as the lead-in voice on the first pass of a
 * broadcast. The enum carries both the human phrase (for display / docs) and
 * the audio-resource basename so [NumberStationPlayer] can resolve it against
 * the active [NumberStationPlayer.Voice] prefix.
 *
 * Boundaries: morning 05–11, afternoon 12–16, evening 17–20, night 21–04.
 * Pure so it is JVM-unit-testable; the wall-clock variant lives in
 * [currentGreeting].
 */
internal enum class Greeting(val phrase: String, val rawName: String) {
    Morning("Good morning", "good_morning"),
    Afternoon("Good afternoon", "good_afternoon"),
    Evening("Good evening", "good_evening"),
    Night("Good night", "good_night"),
}

internal fun greetingForHour(hour: Int): Greeting = when (hour) {
    in 5..11 -> Greeting.Morning
    in 12..16 -> Greeting.Afternoon
    in 17..20 -> Greeting.Evening
    else -> Greeting.Night
}

/** Greeting for the current local time. Re-evaluated on every broadcast. */
internal fun currentGreeting(): Greeting =
    greetingForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
