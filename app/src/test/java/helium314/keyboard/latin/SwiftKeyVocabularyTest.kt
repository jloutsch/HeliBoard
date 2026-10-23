// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.utils.SwiftKeyVocabulary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwiftKeyVocabularyTest {
    private val header = """
        # Your SwiftKey vocabulary
        #
        # This is a list of words that SwiftKey has learned from your typing.
        #
        # Learn more: https://go.microsoft.com/fwlink/?linkid=2330143
    """.trimIndent()

    private fun filter(vararg lines: String, known: Set<String> = emptySet()) =
        SwiftKeyVocabulary.filterWords(header + "\n" + lines.joinToString("\n")) { it in known }

    @Test fun detectsHeader() {
        assertTrue(SwiftKeyVocabulary.isSwiftKeyVocabulary(header))
        assertTrue(SwiftKeyVocabulary.isSwiftKeyVocabulary("\uFEFF$header"))
        assertFalse(SwiftKeyVocabulary.isSwiftKeyVocabulary("hello\nworld"))
    }

    @Test fun readsFilesUpToTheSizeLimit() {
        assertEquals("$header\nMünster", SwiftKeyVocabulary.readText("$header\nMünster".byteInputStream()))
        assertEquals("x".repeat(20_000), SwiftKeyVocabulary.readText("x".repeat(20_000).byteInputStream(), 20_000))
        assertEquals(null, SwiftKeyVocabulary.readText("x".repeat(20_001).byteInputStream(), 20_000))
    }

    @Test fun keepsUnknownWords() {
        val result = filter("Contoso", "chatgpt", "airpods", "iPad", "2fa", "n95", "Chick-fil-A", "Münster")
        assertEquals(listOf("Contoso", "chatgpt", "airpods", "iPad", "2fa", "n95", "Chick-fil-A", "Münster"), result.words)
        assertEquals(0, result.skipped)
    }

    @Test fun dropsNonWords() {
        val result = filter(
            "!", "$", "1,000", "1 555-555-0100", "192.0.2.1", "07Xq3pZK", "2k9vq1z", "10th", "80s", "100k",
            "@someone", "someone@example.com", "www.example.com", "index.html", "readme.md", "a",
            "see you at the park", "some-very-long-url-slug-here", "QWErtyUIop", "andThe",
            "Tok_EN12AB", "ref.x&utm_source", "💋", "-foo",
        )
        assertEquals(emptyList(), result.words)
        assertEquals(24, result.skipped)
    }

    @Test fun normalizesApostrophesAndTrailingDots() {
        assertEquals(listOf("Fabrikam's", "Anyway"), filter("Fabrikam’s", "Fabrikam's", "Anyway.").words)
    }

    @Test fun dropsKnownWordsInAnyCapitalization() {
        val known = setOf("anyway", "springfield", "drm", "keyboard", "youtube") // lowercase, like the importer provides
        val result = filter("keyboard", "Anyway", "ANYWAY", "springfield", "drm", "youtube", "Contoso", known = known)
        assertEquals(listOf("Contoso"), result.words)
        assertEquals(6, result.skipped)
    }

    @Test fun prefersLowercaseWhenBothWereLearned() {
        assertEquals(listOf("yay", "Fabrikam"), filter("Yay", "yay", "YAY", "FABRIKAM", "Fabrikam").words)
    }
}
