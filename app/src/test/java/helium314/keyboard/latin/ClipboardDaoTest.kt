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

    @Test fun `shortcut is stored lowercase and matched ignoring case`() {
        val id = addPinnedClip("Best regards")
        assert(dao.setShortcut(id, " Sig "))
        assertEquals("sig", dao.get(id).shortcut)
        assertEquals(listOf("Best regards"), dao.getSnippetsForShortcut("SIG").map { it.text })
        assertEquals("sig", readShortcutFromDb(id))
    }

    @Test fun `shortcut must be letters and digits`() {
        val id = addPinnedClip("Best regards")
        assertEquals(ShortcutError.INVALID_CHARACTERS, dao.getShortcutError(id, "s-g"))
        assertEquals(ShortcutError.INVALID_CHARACTERS, dao.getShortcutError(id, "two words"))
        assertNull(dao.getShortcutError(id, "größe2"))
        assert(!dao.setShortcut(id, "s-g"))
        assertNull(dao.get(id).shortcut)
    }

    @Test fun `shortcut must be unique ignoring case`() {
        val first = addPinnedClip("Best regards")
        val second = addPinnedClip("Kind regards")
        assert(dao.setShortcut(first, "sig"))
        assertEquals(ShortcutError.IN_USE, dao.getShortcutError(second, "SIG"))
        assertNull(dao.getShortcutError(first, "SIG")) // own shortcut is fine
    }

    @Test fun `blank shortcut removes it`() {
        val id = addPinnedClip("Best regards")
        assert(dao.setShortcut(id, "sig"))
        assert(dao.setShortcut(id, "  "))
        assertNull(dao.get(id).shortcut)
        assertNull(readShortcutFromDb(id))
        assertEquals(emptyList(), dao.getSnippetsForShortcut("sig"))
    }

    @Test fun `only pinned clips are snippets`() {
        val id = addPinnedClip("Best regards")
        assert(dao.setShortcut(id, "sig"))
        dao.togglePinned(id)
        assertEquals(emptyList(), dao.getSnippetsForShortcut("sig"))
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

    @Test fun `restoring a backup keeps shortcuts`() {
        val file = context.getDatabasePath("backup_v4.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE CLIPBOARD (ID INTEGER PRIMARY KEY, TIMESTAMP INTEGER NOT NULL, PINNED TINYINT NOT NULL, TEXT TEXT, FILE TEXT, MIME_TYPE TEXT, SHORTCUT TEXT)")
            db.execSQL(GestureDataDao.CREATE_TABLE)
            db.execSQL("INSERT INTO CLIPBOARD (TIMESTAMP, PINNED, TEXT, SHORTCUT) VALUES (1, 1, 'Best regards', 'sig')")
            db.version = 4
        }
        Database.copyFromDb(file, context)
        assertEquals(listOf("Best regards"), dao.getSnippetsForShortcut("sig").map { it.text })
    }

    private fun addPinnedClip(text: String): Long {
        dao.insertNewEntry(System.currentTimeMillis(), true, text, null, null, null)
        return dao.getAll().first { it.text == text }.id
    }

    private fun readShortcutFromDb(id: Long): String? =
        Database.getInstance(context).readableDatabase.rawQuery("SELECT SHORTCUT FROM CLIPBOARD WHERE ID = $id", null).use {
            it.moveToFirst()
            it.getStringOrNull(0)
        }
}
