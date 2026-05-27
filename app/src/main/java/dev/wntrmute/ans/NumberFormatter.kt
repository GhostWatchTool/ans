package dev.wntrmute.ans

/**
 * Pure text helpers for the message field. A number-station message is a stream
 * of digits displayed in groups of five, e.g. "12345 67890 123".
 *
 * Keeping this logic free of Android types makes it unit-testable on the JVM.
 */
object NumberFormatter {
    const val GROUP_SIZE = 5

    /** Strip everything that is not a digit. */
    fun digitsOnly(input: String): String = input.filter(Char::isDigit)

    /** Group a run of digits into space-separated blocks of [GROUP_SIZE]. */
    fun group(digits: String): String =
        digits.chunked(GROUP_SIZE).joinToString(separator = " ")

    /** Strip non-digits, then group. */
    fun format(input: String): String = group(digitsOnly(input))

    /** Split a raw or formatted message into its five-digit groups. */
    fun groups(input: String): List<String> =
        digitsOnly(input).chunked(GROUP_SIZE)

    /**
     * The caret offset, in the *formatted* string, that sits just after
     * [digitCount] digits. Accounts for the spaces inserted between groups so
     * the caret does not drift while the user types.
     */
    fun caretAfterDigits(digitCount: Int): Int {
        if (digitCount <= 0) return 0
        return digitCount + (digitCount - 1) / GROUP_SIZE
    }

    /** Count the digits appearing before [index] in [text]. */
    fun digitsBefore(text: String, index: Int): Int {
        val end = index.coerceIn(0, text.length)
        var count = 0
        for (i in 0 until end) {
            if (text[i].isDigit()) count++
        }
        return count
    }
}
