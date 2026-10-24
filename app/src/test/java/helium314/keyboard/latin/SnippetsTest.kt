// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ShortcutError
import helium314.keyboard.latin.utils.Snippet
import helium314.keyboard.latin.utils.Snippets
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class SnippetsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dao get() = ClipboardDao.getInstance(context)!!

    @BeforeTest fun reset() {
        dao.clear()
        context.prefs().edit { remove(Settings.PREF_SNIPPETS) }
    }

    @Test fun `shortcut is matched ignoring case and suggests the trimmed text`() {
        Snippets.save(context, listOf(Snippet("Best regards\n", shortcut = "sig"), Snippet("Cheers")))
        assertEquals(listOf("Best regards"), Snippets.textsForShortcut(context, "SIG"))
        assertEquals(emptyList(), Snippets.textsForShortcut(context, "cheers"))
    }

    @Test fun `shortcut needs letters and digits only and must be unique`() {
        val snippets = listOf(Snippet("Best regards", shortcut = "sig"))
        assertEquals(ShortcutError.INVALID_CHARACTERS, Snippets.shortcutError(snippets, -1, "two words"))
        assertEquals(ShortcutError.IN_USE, Snippets.shortcutError(snippets, -1, " SIG "))
        assertNull(Snippets.shortcutError(snippets, 0, "sig")) // own shortcut is fine
        assertNull(Snippets.shortcutError(snippets, -1, "größe2"))
        assertNull(Snippets.shortcutError(snippets, -1, "  ")) // no shortcut
    }

    @Test fun `add skips blank and already saved text`() {
        assert(Snippets.add(context, "Best regards"))
        assert(!Snippets.add(context, "Best regards "))
        assert(!Snippets.add(context, "  "))
        assertEquals(listOf(Snippet("Best regards")), Snippets.get(context))
    }

    @Test fun `clipboard snippets are copied once and stay in the clipboard`() {
        dao.insertNewEntry(1, true, "Best regards", null, null, null, "SIG")
        dao.insertNewEntry(2, true, "Kind regards", null, null, null, "sig") // duplicate shortcut
        dao.insertNewEntry(3, true, "Cheers", null, null, null, "two words") // invalid shortcut
        dao.insertNewEntry(4, true, "Pinned only", null, null, null, null) // never was a snippet
        dao.insertNewEntry(5, false, "Bye", null, null, null, "bye") // not pinned
        val expected = listOf(Snippet("Best regards", shortcut = "sig"), Snippet("Kind regards"), Snippet("Cheers"))
        assertEquals(expected, Snippets.get(context))
        assertEquals(5, dao.count())
        dao.clear()
        assertEquals(expected, Snippets.get(context)) // read from the pref now
    }

    @Test fun `kaomoji are added once and removed unless edited`() {
        Snippets.save(context, listOf(Snippet(Snippets.KAOMOJI.first() + " "))) // like a copied clipboard snippet
        Snippets.addKaomoji(context)
        Snippets.addKaomoji(context)
        assertEquals(Snippets.KAOMOJI, Snippets.get(context).map { it.text.trim() })
        val edited = Snippet(Snippets.KAOMOJI.first(), shortcut = "shrug")
        Snippets.save(context, listOf(edited) + Snippets.get(context).drop(1))
        assert(!Snippets.canAddKaomoji(Snippets.get(context)))
        Snippets.removeKaomoji(context)
        assertEquals(listOf(edited), Snippets.get(context))
        assert(!Snippets.canRemoveKaomoji(Snippets.get(context)))
        assert(Snippets.canAddKaomoji(Snippets.get(context)))
    }

    // made-up export in the format of SwiftKey's clipboard_sync.json, unknown fields and deleted_items are ignored
    private val swiftKeyExport = """
        {"shortcuts": [
          {"id": "a", "text": "Second", "pinned": false, "time": 2000, "shortcut": "two", "last_modified": 2000, "extra": 1},
          {"id": "b", "text": "First", "pinned": true, "time": 1000, "shortcut": "One", "last_modified": 1000},
          {"id": "c", "text": "  ", "time": 3000, "shortcut": "blank"},
          {"id": "d", "text": "Invalid", "time": 4000, "shortcut": "two words"},
          {"id": "e", "text": "Same shortcut", "time": 5000, "shortcut": "ONE"},
          {"id": "f", "text": "No shortcut", "time": 6000},
          {"id": "g", "text": "Empty shortcut", "time": 7000, "shortcut": ""},
          {"id": "h", "text": "Used by existing", "time": 8000, "shortcut": "sig"}
        ],
        "deleted_items": [{"id": "x", "deleted_at": 9000}]}
    """

    @Test fun `SwiftKey export is imported oldest first, skipping what can't be added`() {
        val existing = listOf(Snippet("Best regards", shortcut = "sig"))
        val result = Snippets.fromSwiftKey(existing, swiftKeyExport)!!
        val expected = listOf(
            Snippet("First", shortcut = "one"), Snippet("Second", shortcut = "two"),
            Snippet("No shortcut"), Snippet("Empty shortcut"),
        )
        assertEquals(expected, result.added)
        assertEquals(1, result.empty)
        assertEquals(1, result.invalidShortcut)
        assertEquals(2, result.usedShortcut) // "ONE" from the file, "sig" from existing snippets
        assertEquals(0, result.saved)

        val again = Snippets.fromSwiftKey(existing + result.added, swiftKeyExport)!!
        assertEquals(emptyList(), again.added)
        assertEquals(4, again.saved)
    }

    @Test fun `SwiftKey import rejects other files`() {
        assertNull(Snippets.fromSwiftKey(emptyList(), "not json"))
        assertNull(Snippets.fromSwiftKey(emptyList(), """{"deleted_items": []}"""))
        assertNull(Snippets.fromSwiftKey(emptyList(), "# Your SwiftKey vocabulary\nhello"))
        assertEquals(emptyList(), Snippets.fromSwiftKey(emptyList(), """{"shortcuts": []}""")!!.added)
    }
}
