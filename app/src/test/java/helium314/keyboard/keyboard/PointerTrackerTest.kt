// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class PointerTrackerTest {
    @Test fun `deleting words gets faster the longer delete is held`() {
        val delays = (1..20).map { PointerTracker.getRepeatDelay(it, true, 400, 50) }
        assertEquals(listOf(400, 500, 250, 236), delays.take(4))
        assert(delays.drop(2).zipWithNext().all { (a, b) -> b <= a })
        assertEquals(120, delays.last())
    }

    @Test fun `key repeat timing is unchanged when not deleting words`() {
        assertEquals(listOf(400, 50, 50), (1..3).map { PointerTracker.getRepeatDelay(it, false, 400, 50) })
    }
}
