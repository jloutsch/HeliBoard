// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.getStringOrNull
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.database.Database
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

    @Test fun `unpinning removes the shortcut`() {
        dao.insertNewEntry(1, true, "Best regards", null, null, null, "sig")
        val id = dao.getAll().single().id
        dao.togglePinned(id)
        assertNull(dao.get(id).shortcut)
        assertNull(readFromDb(id, "SHORTCUT"))
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
    @Test fun `restoring a backup keeps shortcuts of pinned clips`() {
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
        // checked when Snippets copies them
        assertEquals(setOf("SIG", "sig", "two words"), dao.getAll().mapNotNull { it.shortcut }.toSet())
        assertEquals(4, dao.count())
    }

    @Test fun `database keeps upstream's version and has the shortcut column`() {
        assertEquals(3, Database.getInstance(context).readableDatabase.version)
        dao.insertNewEntry(1, true, "Best regards", null, null, null, "sig")
        assertEquals("sig", readFromDb(dao.getAll().single().id, "SHORTCUT"))
    }

    private fun readFromDb(id: Long, column: String): String? =
        Database.getInstance(context).readableDatabase.rawQuery("SELECT $column FROM CLIPBOARD WHERE ID = $id", null).use {
            it.moveToFirst()
            it.getStringOrNull(0)
        }
}
