package dev.wntrmute.ans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatPlanTest {

    @Test
    fun playsOnceWhenRepeatOff() {
        val plan = RepeatPlan(repeat = false, loopForever = false, plays = 5)
        assertFalse(plan.isInfinite)
        // After the single pass completes, do not continue.
        assertFalse(plan.completePass())
    }

    @Test
    fun loopsForeverWhenSet() {
        val plan = RepeatPlan(repeat = true, loopForever = true, plays = 3)
        assertTrue(plan.isInfinite)
        repeat(20) { assertTrue(plan.completePass()) }
        assertTrue(plan.isInfinite)
    }

    @Test
    fun fixedCountPlaysExactlyNPasses() {
        val plan = RepeatPlan(repeat = true, loopForever = false, plays = 3)
        assertTrue(plan.completePass()) // 3 -> 2
        assertTrue(plan.completePass()) // 2 -> 1
        assertFalse(plan.completePass()) // 1 -> 0, stop
        assertEquals(0, plan.remaining)
    }

    @Test
    fun fixedCountClampedToAtLeastOne() {
        val plan = RepeatPlan(repeat = true, loopForever = false, plays = 0)
        assertEquals(1, plan.remaining)
        assertFalse(plan.completePass())
    }
}
