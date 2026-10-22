// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import kotlin.test.Test
import kotlin.test.assertEquals

class SwipedWordInCenterTest {
    private fun positions(count: Int, stripSize: Int) =
        (0 until count).map { SuggestionStripLayoutHelper.getPositionWithSwipedWordInCenter(it, stripSize / 2) }

    @Test fun swipedWordIsInCenter() {
        assertEquals(listOf(1, 0, 2), positions(3, 3))
        assertEquals(listOf(2, 1, 3, 0, 4), positions(5, 5))
    }

    @Test fun fillsEveryPositionOnce() {
        for (stripSize in 1..9) {
            assertEquals((0 until stripSize).toList(), positions(stripSize, stripSize).sorted(), "strip size $stripSize")
        }
    }
}
