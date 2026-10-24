// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.getStringOrNull
import androidx.core.database.sqlite.transaction
import helium314.keyboard.latin.utils.GestureDataDao
import helium314.keyboard.latin.utils.Log
import java.io.File

class Database private constructor(context: Context, name: String = NAME) : SQLiteOpenHelper(context, name, null, VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(ClipboardDao.CREATE_TABLE)
        onUpgrade(db, 0, VERSION)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion <= 1) {
            db.execSQL(GestureDataDao.CREATE_TABLE)
        }
        if (oldVersion <= 2) {
            db.execSQL(ClipboardDao.ADD_FILE_COLUMN)
            db.execSQL(ClipboardDao.ADD_MIME_TYPE_COLUMN)
        }
    }

    // the shortcut column (clipboard snippets) is added here instead of with a version bump,
    // so this fork keeps upstream's version numbers and upstream's next migration still runs
    override fun onOpen(db: SQLiteDatabase) {
        if (!db.isReadOnly) ClipboardDao.addShortcutColumnIfMissing(db)
    }

    // version 4 was this fork's shortcut column, which onOpen now takes care of
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion != 4 || newVersion != 3) super.onDowngrade(db, oldVersion, newVersion)
    }

    companion object {
        private val TAG = Database::class.java.simpleName
        private const val VERSION = 3
        const val NAME = "heliboard.db"
        private var instance: Database? = null
        fun getInstance(context: Context): Database {
            if (instance == null)
                instance = Database(context)
            return instance!!
        }

        // needs to be in sync with db version
        fun copyFromDb(file: File, context: Context) {
            if (!file.exists())
                return
            val otherDb = Database(context, file.name) // this upgrades the DB if necessary
            val clipDao = ClipboardDao.getInstance(context) // insert to dao because of cache
            val db = getInstance(context)

            try {
                db.writableDatabase.transaction {
                    if (clipDao == null) {
                        Log.e(TAG, "can't transfer clipboard data because ClipboardDao is null")
                    } else {
                        otherDb.readableDatabase.rawQuery("SELECT TIMESTAMP, PINNED, TEXT, FILE, MIME_TYPE, SHORTCUT FROM CLIPBOARD", null)
                            .use {
                                clipDao.clear()
                                while (it.moveToNext()) {
                                    val pinned = it.getInt(1) != 0
                                    clipDao.insertNewEntry(
                                        it.getLong(0),
                                        pinned,
                                        it.getStringOrNull(2),
                                        it.getStringOrNull(3),
                                        it.getStringOrNull(4)?.split("§"),
                                        null,
                                        if (pinned) it.getStringOrNull(5) else null // checked when Snippets copies it
                                    )
                                }
                            }
                    }
                    db.writableDatabase.execSQL("DELETE FROM GESTURE_DATA")
                    otherDb.readableDatabase.rawQuery("SELECT TIMESTAMP, WORD, EXPORTED, SOURCE_ACTIVE, DATA FROM GESTURE_DATA", null)
                        .use { c ->
                            while (c.moveToNext()) {
                                execSQL("INSERT INTO GESTURE_DATA (TIMESTAMP, WORD, EXPORTED, SOURCE_ACTIVE, DATA) " +
                                    "VALUES (${c.getLong(0)},?,${c.getInt(2)},${c.getInt(3)},?)", arrayOf(c.getString(1), c.getString(4)))
                            }
                        }
                }
            } finally {
                otherDb.close()
                file.delete()
            }
        }
    }
}
