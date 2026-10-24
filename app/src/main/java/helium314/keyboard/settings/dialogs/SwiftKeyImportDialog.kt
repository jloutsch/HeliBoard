// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.UserDictionary
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryFactory
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.IntentUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.Snippets
import helium314.keyboard.latin.utils.SwiftKeyVocabulary
import helium314.keyboard.settings.filePicker
import helium314.keyboard.settings.screens.getLocaleDisplayNameForUserDictSettings
import helium314.keyboard.settings.screens.getSortedDictionaryLocales
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// any type, as cloud storage apps don't always report text/plain or application/json (content is checked anyway)
fun swiftKeyFileIntent(context: Context): Intent =
    IntentUtils.getResolvableTypeIntent(context, "*/*").setAction(Intent.ACTION_OPEN_DOCUMENT)

/** Picker for SwiftKey's vocabulary.txt, the new words are added to the personal dictionary for [locale]. */
@Composable
fun swiftKeyVocabularyPicker(locale: Locale?): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope { Dispatchers.IO }
    var state: ImportState? by remember { mutableStateOf(null) }
    var readJob: Job? by remember { mutableStateOf(null) }
    val picker = filePicker { uri ->
        state = ImportState.Reading
        readJob = scope.launch {
            val result = try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { SwiftKeyVocabulary.readText(it) }
                ensureActive()
                if (text == null || !SwiftKeyVocabulary.isSwiftKeyVocabulary(text)) ImportState.WrongFile
                else {
                    val known = getKnownWordsLowercase(ctx, locale)
                    ensureActive()
                    ImportState.Ready(SwiftKeyVocabulary.filterWords(text) { it in known })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "could not read SwiftKey vocabulary", e)
                ImportState.WrongFile
            }
            ensureActive() // cancelled while reading: don't show a result
            state = result
        }
    }
    when (val s = state) {
        null -> { }
        // reading is not dismissed by tapping outside, cancel actually stops it
        ImportState.Reading -> ThreeButtonAlertDialog(
            onDismissRequest = { readJob?.cancel(); state = null },
            onConfirmed = { },
            confirmButtonText = null,
            content = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.swiftkey_import_reading))
                }
            },
            properties = DialogProperties(dismissOnClickOutside = false)
        )
        ImportState.WrongFile -> InfoDialog(stringResource(R.string.swiftkey_import_wrong_file)) { state = null }
        is ImportState.Done -> InfoDialog(stringResource(R.string.swiftkey_import_done, s.added)) { state = null }
        is ImportState.Ready -> if (s.result.words.isEmpty()) {
            InfoDialog(stringResource(R.string.swiftkey_import_no_new_words, s.result.skipped)) { state = null }
        } else {
            ConfirmationDialog(
                onDismissRequest = { state = null },
                onConfirmed = {
                    scope.launch {
                        addToPersonalDictionary(ctx, s.result.words, locale)
                        state = ImportState.Done(s.result.words.size) // also reloads the word list
                    }
                },
                title = { Text(stringResource(R.string.import_swiftkey_vocabulary)) },
                content = {
                    Text(stringResource(
                        R.string.swiftkey_import_message,
                        s.result.words.size,
                        locale.getLocaleDisplayNameForUserDictSettings(ctx),
                        s.result.skipped
                    ))
                },
            )
        }
    }
    return picker
}

/** Picker for SwiftKey's clipboard export, the shortcuts are added to the snippets. [onImported] is called after saving. */
@Composable
fun swiftKeySnippetsPicker(onImported: () -> Unit): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message: String? by remember { mutableStateOf(null) }
    val picker = filePicker { uri ->
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val text = ctx.contentResolver.openInputStream(uri)?.use { SwiftKeyVocabulary.readText(it) }
                    text?.let { Snippets.fromSwiftKey(Snippets.get(ctx), it) }
                } catch (e: Exception) {
                    Log.w(TAG, "could not read SwiftKey clipboard", e)
                    null
                }
            }
            if (result == null) {
                message = ctx.getString(R.string.snippets_import_wrong_file)
                return@launch
            }
            if (result.added.isNotEmpty()) {
                Snippets.save(ctx, Snippets.get(ctx) + result.added)
                onImported()
            }
            message = listOfNotNull(
                ctx.getString(R.string.snippets_import_done, result.added.size),
                result.saved.takeIf { it > 0 }?.let { ctx.getString(R.string.snippets_import_skipped_saved, it) },
                result.usedShortcut.takeIf { it > 0 }?.let { ctx.getString(R.string.snippets_import_skipped_used, it) },
                result.invalidShortcut.takeIf { it > 0 }?.let { ctx.getString(R.string.snippets_import_skipped_invalid, it) },
                result.empty.takeIf { it > 0 }?.let { ctx.getString(R.string.snippets_import_skipped_empty, it) },
            ).joinToString("\n")
        }
    }
    message?.let { InfoDialog(it) { message = null } }
    return picker
}

private sealed interface ImportState {
    object Reading : ImportState
    object WrongFile : ImportState
    class Ready(val result: SwiftKeyVocabulary.Result) : ImportState
    class Done(val added: Int) : ImportState
}

/**
 * Words in the dictionaries of [locale] and the other languages the user has (so e.g. German words are not
 * added to the English personal dictionary if German is enabled), and words already in the personal dictionary.
 */
private fun getKnownWordsLowercase(context: Context, locale: Locale?): Set<String> {
    val known = HashSet<String>()
    val locales = getSortedDictionaryLocales().apply { if (locale != null) add(locale) }
    for (dictLocale in locales) {
        val (cachedDicts, assetsDicts) = DictionaryFactory.getAvailableDictsForLocale(dictLocale, context, false)
        val files = cachedDicts.filter { it.isFile && it.name.endsWith(".dict") } +
                assetsDicts.mapNotNull { DictionaryInfoUtils.extractAssetsDictionary(it, dictLocale, context) }
        files.forEach { known.addWordsFrom(it, dictLocale) }
    }
    context.contentResolver.query(UserDictionary.Words.CONTENT_URI, arrayOf(UserDictionary.Words.WORD), null, null, null)
        ?.use { cursor ->
            while (cursor.moveToNext())
                cursor.getString(0)?.let { known.add(it.lowercase()) }
        }
    return known
}

private fun MutableSet<String>.addWordsFrom(file: File, locale: Locale) {
    val dict = BinaryDictionary(file.absolutePath, 0, file.length(), false, locale, Dictionary.TYPE_MAIN, false)
    try {
        if (!dict.isValidDictionary) return
        var token = 0
        do {
            val result = dict.getNextWordProperty(token)
            val word = result.mWordProperty.mWord
            if (!result.mWordProperty.mIsNotAWord && word.isNotEmpty())
                add(word.lowercase())
            token = result.mNextToken
        } while (token != 0)
    } finally {
        dict.close()
    }
}

private fun addToPersonalDictionary(context: Context, words: List<String>, locale: Locale?) {
    val values = words.map { word ->
        ContentValues().apply {
            put(UserDictionary.Words.WORD, word)
            put(UserDictionary.Words.FREQUENCY, WEIGHT_FOR_SWIFTKEY_WORDS)
            // requires use of locale string for interaction with Android system, null means all languages
            put(UserDictionary.Words.LOCALE, locale?.toString())
            put(UserDictionary.Words.APP_ID, 0)
        }
    }
    context.contentResolver.bulkInsert(UserDictionary.Words.CONTENT_URI, values.toTypedArray())
}

// SwiftKey doesn't export how often words were used, so imported words get less weight
// than words added manually (250), roughly the frequency of a common word in the main dictionary
private const val WEIGHT_FOR_SWIFTKEY_WORDS = 160

private const val TAG = "SwiftKeyImport"
