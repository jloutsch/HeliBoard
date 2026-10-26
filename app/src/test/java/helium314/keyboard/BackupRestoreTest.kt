// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import helium314.keyboard.settings.preferences.deleteMovedFilesDirs
import helium314.keyboard.settings.preferences.moveFilesDirsAside
import helium314.keyboard.settings.preferences.putBackFilesDirs
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BackupRestoreTest {
    private val root = Files.createTempDirectory("restore").toFile()
    private val filesDir = File(root, "files")
    private val deviceProtectedFilesDir = File(root, "de/files")
    private val dirs = listOf(filesDir, deviceProtectedFilesDir)

    @AfterTest fun cleanup() { root.deleteRecursively() }

    private fun write(dir: File, name: String, text: String) = File(dir, name).apply { parentFile!!.mkdirs(); writeText(text) }

    @Test fun `failed restore puts back all files`() {
        write(filesDir, "custom_font", "old font")
        write(deviceProtectedFilesDir, "layouts/custom.a.", "old layout")
        moveFilesDirsAside(dirs)
        write(filesDir, "custom_font", "partly restored")
        write(deviceProtectedFilesDir, "layouts/custom.b.", "partly restored")

        putBackFilesDirs(dirs)
        assertEquals("old font", File(filesDir, "custom_font").readText())
        assertEquals("old layout", File(deviceProtectedFilesDir, "layouts/custom.a.").readText())
        assertFalse(File(deviceProtectedFilesDir, "layouts/custom.b.").exists())
    }

    @Test fun `successful restore replaces files, but keeps the gesture library`() {
        write(filesDir, "libjni_latinime.so", "library")
        write(deviceProtectedFilesDir, "layouts/custom.deleted.", "not in backup")
        moveFilesDirsAside(dirs)
        write(deviceProtectedFilesDir, "layouts/custom.a.", "restored")

        deleteMovedFilesDirs(dirs, listOf("libjni_latinime.so"))
        assertEquals("library", File(filesDir, "libjni_latinime.so").readText())
        assertEquals("restored", File(deviceProtectedFilesDir, "layouts/custom.a.").readText())
        assertFalse(File(deviceProtectedFilesDir, "layouts/custom.deleted.").exists())
        assertEquals(listOf("de", "files"), root.list()!!.sorted())
    }

    @Test fun `failed restore keeps files if both dirs are the same, as before Android 7`() {
        write(filesDir, "custom_font", "old font")
        moveFilesDirsAside(listOf(filesDir, filesDir))
        putBackFilesDirs(listOf(filesDir, filesDir))
        assertEquals("old font", File(filesDir, "custom_font").readText())
    }
}
