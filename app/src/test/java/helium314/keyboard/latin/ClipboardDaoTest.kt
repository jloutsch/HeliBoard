// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.getStringOrNull
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.database.Database
import helium314.keyboard.latin.database.ShortcutError
import helium314.keyboard.latin.utils.GestureDataDao
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class ClipboardDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dao get() = ClipboardDao.getInstance(context)!!

    @BeforeTest fun reset() {
        dao.clear()
    }

    @Test fun `new snippet is a pinned clip with lowercase shortcut matched ignoring case`() {
        assert(dao.saveSnippet(null, "Best regards", " Sig "))
        val clip = dao.getAll().single()
        assert(clip.isPinned)
        assertEquals("sig", clip.shortcut)
        assertEquals("sig", readFromDb(clip.id, "SHORTCUT"))
        assertEquals(listOf("Best regards"), dao.getSnippetsForShortcut("SIG").map { it.text })
    }

    @Test fun `snippet needs text and a unique shortcut of letters and digits`() {
        assertEquals(ShortcutError.INVALID_CHARACTERS, dao.getShortcutError(-1, "two words"))
        assertNull(dao.getShortcutError(-1, "größe2"))
        assert(!dao.saveSnippet(null, "  ", "sig"))
        assert(!dao.saveSnippet(null, "Best regards", "s-g"))
        assert(dao.saveSnippet(null, "Best regards", "sig"))
        assert(!dao.saveSnippet(null, "Kind regards", "SIG"))
        assertNull(dao.getShortcutError(dao.getAll().single().id, "SIG")) // own shortcut is fine
        assertEquals(listOf("Best regards"), dao.getAll().map { it.text })
    }

    @Test fun `editing a snippet changes text and blank removes the shortcut`() {
        assert(dao.saveSnippet(null, "Best regards", "sig"))
        val id = dao.getAll().single().id
        assert(dao.saveSnippet(id, "Kind regards", "kr"))
        assertEquals("Kind regards", readFromDb(id, "TEXT"))
        assertEquals(emptyList(), dao.getSnippetsForShortcut("sig"))
        assertEquals(listOf("Kind regards"), dao.getSnippetsForShortcut("kr").map { it.text })
        assert(dao.saveSnippet(id, "Kind regards", "  "))
        assertNull(readFromDb(id, "SHORTCUT"))
        assertEquals(emptyList(), dao.getSnippetsForShortcut("kr"))
    }

    @Test fun `unpinning removes the shortcut so it can be used again`() {
        assert(dao.saveSnippet(null, "Best regards", "sig"))
        val id = dao.getAll().single().id
        dao.togglePinned(id)
        assertEquals(emptyList(), dao.getSnippetsForShortcut("sig"))
        assertNull(dao.get(id).shortcut)
        assertNull(readFromDb(id, "SHORTCUT"))
        assert(dao.saveSnippet(null, "Kind regards", "sig"))
    }

    @Test fun `deleted snippet is not suggested`() {
        assert(dao.saveSnippet(null, "Best regards", "sig"))
        dao.deleteClip(dao.getAll().single().id)
        assertEquals(emptyList(), dao.getSnippetsForShortcut("sig"))
        assertEquals(0, dao.count())
    }

    @Test fun `restoring a version 3 backup adds the shortcut column`() {
        val file = context.getDatabasePath("backup_v3.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE CLIPBOARD (ID INTEGER PRIMARY KEY, TIMESTAMP INTEGER NOT NULL, PINNED TINYINT NOT NULL, TEXT TEXT, FILE TEXT, MIME_TYPE TEXT)")
            db.execSQL(GestureDataDao.CREATE_TABLE)
            db.execSQL("INSERT INTO CLIPBOARD (TIMESTAMP, PINNED, TEXT) VALUES (1, 1, 'Best regards')")
            db.version = 3
        }
        Database.copyFromDb(file, context)
        val clip = dao.getAll().single()
        assertEquals("Best regards", clip.text)
        assert(clip.isPinned)
        assertNull(clip.shortcut)
    }

    // version 4 is what this fork used for the shortcut column before it moved to Database.onOpen
    @Test fun `restoring a backup keeps valid shortcuts of pinned clips`() {
        val file = context.getDatabasePath("backup_v4.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE CLIPBOARD (ID INTEGER PRIMARY KEY, TIMESTAMP INTEGER NOT NULL, PINNED TINYINT NOT NULL, TEXT TEXT, FILE TEXT, MIME_TYPE TEXT, SHORTCUT TEXT)")
            db.execSQL(GestureDataDao.CREATE_TABLE)
            db.execSQL("INSERT INTO CLIPBOARD (TIMESTAMP, PINNED, TEXT, SHORTCUT) VALUES (1, 1, 'Best regards', 'SIG')")
            db.execSQL("INSERT INTO CLIPBOARD (TIMESTAMP, PINNED, TEXT, SHORTCUT) VALUES (2, 1, 'Kind regards', 'sig')") // duplicate
            db.execSQL("INSERT INTO CLIPBOARD (TIMESTAMP, PINNED, TEXT, SHORTCUT) VALUES (3, 1, 'Cheers', 'two words')")
            db.execSQL("INSERT INTO CLIPBOARD (TIMESTAMP, PINNED, TEXT, SHORTCUT) VALUES (4, 0, 'Bye', 'bye')") // not pinned
            db.version = 4
        }
        Database.copyFromDb(file, context)
        assertEquals(1, dao.getAll().count { it.shortcut == "sig" })
        assertEquals(listOf("sig"), dao.getAll().mapNotNull { it.shortcut })
        assertEquals(4, dao.count())
    }

    @Test fun `database keeps upstream's version and has the shortcut column`() {
        assertEquals(3, Database.getInstance(context).readableDatabase.version)
        assert(dao.saveSnippet(null, "Best regards", "sig"))
        assertEquals("sig", readFromDb(dao.getAll().single().id, "SHORTCUT"))
    }

    private fun readFromDb(id: Long, column: String): String? =
        Database.getInstance(context).readableDatabase.rawQuery("SELECT $column FROM CLIPBOARD WHERE ID = $id", null).use {
            it.moveToFirst()
            it.getStringOrNull(0)
        }
}
