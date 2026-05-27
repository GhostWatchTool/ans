package dev.wntrmute.ans

/**
 * Tracks how many full readings (passes) of a message remain.
 *
 * - repeat off: exactly one pass.
 * - repeat on, loop forever: unbounded.
 * - repeat on, fixed: [plays] passes (clamped to at least one).
 *
 * Pure (no Android dependencies) so the repeat accounting is unit-testable.
 */
class RepeatPlan(repeat: Boolean, loopForever: Boolean, plays: Int) {

    /** Remaining passes; null means loop forever. */
    var remaining: Int? = when {
        !repeat -> 1
        loopForever -> null
        else -> plays.coerceAtLeast(1)
    }
        private set

    val isInfinite: Boolean get() = remaining == null

    /**
     * Record that a pass just completed. Returns true if another pass should
     * play. Infinite plans always continue.
     */
    fun completePass(): Boolean {
        val current = remaining ?: return true
        val next = current - 1
        remaining = next
        return next > 0
    }
}
