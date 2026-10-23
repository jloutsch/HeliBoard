// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class PointerTrackerTest {
    @Test fun `holding delete deletes a word on the first repeat and then once per long press delay`() {
        // 400 ms long press / 50 ms repeat interval -> one word every 8 repeats
        val codes = (1..17).map { PointerTracker.getRepeatCode(KeyCode.DELETE, it, true, 400, 50) }
        val wordRepeats = codes.withIndex().filter { it.value == KeyCode.DELETE_WORD }.map { it.index + 1 }
        assertEquals(listOf(1, 9, 17), wordRepeats)
        assert(codes.all { it == KeyCode.DELETE_WORD || it == Constants.NOT_A_CODE })
    }

    @Test fun `key repeat is unchanged when the option is off or for other keys`() {
        assertEquals(KeyCode.DELETE, PointerTracker.getRepeatCode(KeyCode.DELETE, 2, false, 400, 50))
        assertEquals(KeyCode.ARROW_LEFT, PointerTracker.getRepeatCode(KeyCode.ARROW_LEFT, 2, true, 400, 50))
    }

    @Test fun `long press shorter than the repeat interval deletes a word on every repeat`() {
        assertEquals(KeyCode.DELETE_WORD, PointerTracker.getRepeatCode(KeyCode.DELETE, 2, true, 30, 50))
    }
}
