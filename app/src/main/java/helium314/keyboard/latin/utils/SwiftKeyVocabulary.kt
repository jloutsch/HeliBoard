// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.Normalizer

/**
 * Reads the vocabulary.txt that SwiftKey stores in OneDrive (Apps > SwiftKey) or exports from its data portal.
 * The file is a list of learned words, one per line, with '#' comment lines at the top and no frequencies.
 *
 * SwiftKey learns pretty much every token typed, so the list is filtered: numbers, links, mail addresses,
 * handles, ids, multi-word entries, and capitalization variants of known words (e.g. sentence-start "Anyway")
 * are dropped, as well as words that are already known in any capitalization (e.g. from the main dictionary).
 */
object SwiftKeyVocabulary {
    private const val HEADER = "# Your SwiftKey vocabulary"
    // a word list is far smaller; a bigger file was picked by mistake and could run the keyboard's process out of memory
    const val MAX_FILE_BYTES = 10_000_000

    fun isSwiftKeyVocabulary(text: String) = text.trimStart('\uFEFF').startsWith(HEADER)

    /** Text of [stream] as UTF-8, or null if it's larger than [maxBytes] */
    fun readText(stream: InputStream, maxBytes: Int = MAX_FILE_BYTES): String? {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) return bytes.toString("UTF-8")
            bytes.write(buffer, 0, read)
            if (bytes.size() > maxBytes) return null
        }
    }

    class Result(val words: List<String>, val skipped: Int)

    /** [isKnownLowercase] is called with lowercase words, so it should check against lowercase known words. */
    fun filterWords(text: String, isKnownLowercase: (String) -> Boolean): Result {
        val entries = text.lineSequence()
            .map { it.trimStart('\uFEFF').trimEnd('\r') }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        val candidates = LinkedHashSet<String>()
        entries.forEach { entry ->
            val word = normalize(entry)
            if (isPlausibleWord(word)) candidates.add(word)
        }
        val words = candidates.filter { word ->
            val lower = word.lowercase()
            when {
                isKnownLowercase(lower) -> false // "YouTube" is known, so "youtube" isn't needed
                // learned at sentence start or in caps, the lowercase version is learned too
                word != lower && lower in candidates -> false
                word.isAllCaps() && lower.capitalize() in candidates -> false
                else -> true
            }
        }
        return Result(words, entries.size - words.size)
    }

    private fun normalize(entry: String) = Normalizer.normalize(entry, Normalizer.Form.NFC)
        .replace('\u2019', '\'').replace('\u2018', '\'')
        .trimEnd('.')

    private val wordShape = Regex("""\p{L}[\p{L}\p{M}\d'-]*|\d[\p{L}\p{M}\d'-]*""")
    private val ordinalOrNumberWithUnit = Regex("""\d+(st|nd|rd|th|s|k|x|am|pm)""", RegexOption.IGNORE_CASE)
    private val runTogether = Regex("""\p{Ll}{3,}\p{Lu}.*""") // "andThe", but not "iPad" or "eBay"

    private fun isPlausibleWord(word: String): Boolean {
        if (word.length < 2 || word.length > 25) return false
        if (!wordShape.matches(word)) return false // whitespace, mail addresses, links, handles, symbols
        if (word.none { it.isLetter() }) return false
        if (word.startsWith('\'') || word.startsWith('-') || word.endsWith('-') || word.count { it == '-' } > 2)
            return false
        // keep short things like 2fa or n95, but not ids, hashes and phone numbers
        if (word.any { it.isDigit() } && (word.length > 5 || ordinalOrNumberWithUnit.matches(word)))
            return false
        if (runTogether.matches(word)) return false
        val letters = word.filter { it.isLetter() }
        if (letters.length >= 7 && letters.drop(1).count { it.isUpperCase() } >= 3 && !word.isAllCaps())
            return false // random tokens
        return true
    }

    private fun String.isAllCaps() = any { it.isLetter() } && none { it.isLowerCase() }

    private fun String.capitalize() = replaceFirstChar { it.titlecase() }
}
