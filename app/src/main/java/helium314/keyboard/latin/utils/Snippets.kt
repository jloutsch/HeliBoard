// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import androidx.core.content.edit
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/** Saved text for the snippets panel. [label] is shown in the panel instead of the text, typing [shortcut] suggests the text. */
@Serializable
data class Snippet(val text: String, val label: String? = null, val shortcut: String? = null)

enum class ShortcutError { INVALID_CHARACTERS, IN_USE }

/** Snippets are stored as JSON in a pref, so they are part of backups. */
object Snippets {
    // parsed list for the pref string it came from, shortcuts are looked up for every typed word
    @Volatile private var cache: Pair<String, List<Snippet>>? = null

    @Synchronized
    fun get(context: Context): List<Snippet> {
        val json = context.prefs().getString(Settings.PREF_SNIPPETS, null) ?: return copyClipboardSnippets(context)
        cache?.let { if (it.first == json) return it.second }
        val snippets = runCatching { Json.decodeFromString<List<Snippet>>(json) }
            .onFailure { Log.w(TAG, "can't read snippets", it) }.getOrDefault(emptyList())
        cache = json to snippets
        return snippets
    }

    fun save(context: Context, snippets: List<Snippet>) {
        context.prefs().edit { putString(Settings.PREF_SNIPPETS, Json.encodeToString(snippets)) }
    }

    /** Adds [text] at the end, unless it's blank or already saved. Returns whether it was added. */
    fun add(context: Context, text: String): Boolean {
        val snippets = get(context)
        if (text.isBlank() || snippets.any { it.text.trim() == text.trim() }) return false
        save(context, snippets + Snippet(text))
        return true
    }

    /** Texts of snippets whose shortcut is [word], ignoring case */
    fun textsForShortcut(context: Context, word: String): List<String> {
        val shortcut = normalizeShortcut(word) ?: return emptyList()
        return get(context).filter { it.shortcut == shortcut }.map { it.text.trim() }
    }

    /** Why [shortcut] can't be used for the snippet at [index] (-1 for a new one), or null if it can. Blank means no shortcut. */
    fun shortcutError(snippets: List<Snippet>, index: Int, shortcut: String): ShortcutError? {
        val normalized = normalizeShortcut(shortcut) ?: return null
        if (!normalized.matches(SHORTCUT_REGEX)) return ShortcutError.INVALID_CHARACTERS
        if (snippets.withIndex().any { (i, s) -> i != index && s.shortcut == normalized }) return ShortcutError.IN_USE
        return null
    }

    fun normalizeShortcut(shortcut: String) = shortcut.trim().lowercase(Locale.ROOT).ifEmpty { null }

    // texts are compared trimmed, as copied clipboard snippets can have spaces around them
    private fun missingKaomoji(snippets: List<Snippet>) = KAOMOJI.filter { k -> snippets.none { it.text.trim() == k } }

    // kaomoji the user gave a label or shortcut are kept
    private fun isRemovableKaomoji(snippet: Snippet) = snippet.text.trim() in KAOMOJI && snippet.label == null && snippet.shortcut == null

    fun canAddKaomoji(snippets: List<Snippet>) = missingKaomoji(snippets).isNotEmpty()

    fun canRemoveKaomoji(snippets: List<Snippet>) = snippets.any(::isRemovableKaomoji)

    fun addKaomoji(context: Context) {
        val snippets = get(context)
        save(context, snippets + missingKaomoji(snippets).map { Snippet(it) })
    }

    fun removeKaomoji(context: Context) {
        save(context, get(context).filterNot(::isRemovableKaomoji))
    }

    // Clipboard snippets used to be pinned clips with a shortcut. They are copied once, when there is no snippets pref yet
    // (also after restoring an older backup). The clips stay pinned in the clipboard history.
    private fun copyClipboardSnippets(context: Context): List<Snippet> {
        val dao = ClipboardDao.getInstance(context)
        // oldest first, so snippets keep the order they were added in
        val clips = dao?.let { synchronized(it) { it.getAll().sortedBy { clip -> clip.timeStamp } } }.orEmpty()
        val snippets = mutableListOf<Snippet>()
        clips.forEach {
            val shortcut = it.shortcut ?: return@forEach
            if (!it.isPinned || it.text.isNullOrBlank()) return@forEach
            snippets.add(Snippet(it.text!!, shortcut = if (shortcutError(snippets, -1, shortcut) == null) normalizeShortcut(shortcut) else null))
        }
        save(context, snippets)
        return snippets
    }

    /**
     * New snippets from SwiftKey's clipboard export (clipboard_sync.json), oldest first, for adding after [snippets].
     * Null if [json] is not such an export.
     */
    fun fromSwiftKey(snippets: List<Snippet>, json: String): SwiftKeyImport? {
        val export = try {
            swiftKeyJson.decodeFromString<SwiftKeyClipboard>(json.trimStart('﻿'))
        } catch (e: IllegalArgumentException) { // also thrown for invalid JSON and missing fields
            return null
        }
        val added = mutableListOf<Snippet>()
        var empty = 0; var saved = 0; var invalidShortcut = 0; var usedShortcut = 0
        export.shortcuts.sortedBy { it.time ?: 0 }.forEach { entry ->
            val text = entry.text
            val shortcut = entry.shortcut?.let { normalizeShortcut(it) }
            val all = snippets + added
            when {
                text.isNullOrBlank() -> empty++
                // importing the same file again adds nothing
                all.any { it.text.trim() == text.trim() && it.shortcut == shortcut } -> saved++
                else -> when (shortcutError(all, -1, shortcut.orEmpty())) {
                    ShortcutError.INVALID_CHARACTERS -> invalidShortcut++
                    ShortcutError.IN_USE -> usedShortcut++
                    null -> added.add(Snippet(text, shortcut = shortcut))
                }
            }
        }
        return SwiftKeyImport(added, empty, saved, invalidShortcut, usedShortcut)
    }

    class SwiftKeyImport(val added: List<Snippet>, val empty: Int, val saved: Int, val invalidShortcut: Int, val usedShortcut: Int)

    // other fields (id, pinned, last_modified, deleted_items) are not needed
    @Serializable
    private class SwiftKeyClipboard(val shortcuts: List<SwiftKeyShortcut>)

    @Serializable
    private class SwiftKeyShortcut(val text: String? = null, val shortcut: String? = null, val time: Long? = null)

    private val swiftKeyJson = Json { ignoreUnknownKeys = true }

    // letters and digits only, so a shortcut is always a single word
    private val SHORTCUT_REGEX = Regex("[\\p{L}\\p{N}]+")

    val KAOMOJI = listOf(
        "¯\\_(ツ)_/¯", "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "( ͡° ͜ʖ ͡°)", "ಠ_ಠ", "(づ｡◕‿‿◕｡)づ", "ʕ•ᴥ•ʔ", "(｡♥‿♥｡)",
        "\\(^o^)/", "(^_^)", "(T_T)", "(>_<)", "(¬‿¬)", "(⊙_⊙)", "(•_•) ( •_•)>⌐■-■ (⌐■_■)",
    )

    private const val TAG = "Snippets"
}
