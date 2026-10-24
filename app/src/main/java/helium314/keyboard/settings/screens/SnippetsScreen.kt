// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.ShortcutError
import helium314.keyboard.latin.utils.Snippet
import helium314.keyboard.latin.utils.Snippets
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.dialogs.swiftKeyFileIntent
import helium314.keyboard.settings.dialogs.swiftKeySnippetsPicker

@Composable
fun SnippetsScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    // bumped after saving, so the list is read again
    var savedCount by remember { mutableIntStateOf(0) }
    val snippets = remember(savedCount) { Snippets.get(ctx) }
    fun save(list: List<Snippet>) {
        Snippets.save(ctx, list)
        savedCount++
    }
    // index of the snippet being edited, snippets.size for a new one
    var editIndex: Int? by remember { mutableStateOf(null) }
    val swiftKeyPicker = swiftKeySnippetsPicker { savedCount += 1 }
    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.snippets)) },
        menu = listOfNotNull(
            if (Snippets.canAddKaomoji(snippets))
                stringResource(R.string.snippets_add_kaomoji) to { Snippets.addKaomoji(ctx); savedCount += 1 }
            else null,
            if (Snippets.canRemoveKaomoji(snippets))
                stringResource(R.string.snippets_remove_kaomoji) to { Snippets.removeKaomoji(ctx); savedCount += 1 }
            else null,
            stringResource(R.string.snippets_import_swiftkey) to { swiftKeyPicker.launch(swiftKeyFileIntent(ctx)) },
        ),
        filteredItems = { term ->
            snippets.withIndex().filter { (_, s) ->
                s.text.contains(term, true) || s.label?.contains(term, true) == true || s.shortcut?.startsWith(term, true) == true
            }
        },
        itemContent = { (index, snippet) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editIndex = index }
                    .padding(vertical = 6.dp, horizontal = 16.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(snippet.label ?: snippet.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val details = listOfNotNull(snippet.shortcut, snippet.label?.let { snippet.text }).joinToString("  |  ")
                    if (details.isNotEmpty())
                        Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (index > 0)
                    IconButton(onClick = { save(snippets.toMutableList().apply { add(index - 1, removeAt(index)) }) }) {
                        Icon(painterResource(R.drawable.ic_arrow_left), stringResource(R.string.snippet_move_up), Modifier.rotate(90f))
                    }
            }
        },
        content = if (snippets.isEmpty()) {
            { Text(stringResource(R.string.snippets_empty), Modifier.padding(16.dp)) }
        } else null
    )
    editIndex?.let { index ->
        EditSnippetDialog(snippets, index, ::save) { editIndex = null }
    }
    ExtendedFloatingActionButton(
        onClick = { editIndex = snippets.size },
        text = { Text(stringResource(R.string.snippet_add)) },
        icon = { Icon(painter = painterResource(R.drawable.ic_plus), stringResource(R.string.snippet_add)) },
        modifier = Modifier.wrapContentSize(Alignment.BottomEnd).padding(all = 12.dp)
            .then(Modifier.safeDrawingPadding())
    )
}

@Composable
private fun EditSnippetDialog(snippets: List<Snippet>, index: Int, save: (List<Snippet>) -> Unit, onDismissRequest: () -> Unit) {
    val isNew = index == snippets.size
    val snippet = snippets.getOrNull(index)
    val focusRequester = remember { FocusRequester() }
    var text by remember { mutableStateOf(snippet?.text ?: "") }
    var label by remember { mutableStateOf(snippet?.label ?: "") }
    var shortcut by remember { mutableStateOf(snippet?.shortcut ?: "") }
    val error = Snippets.shortcutError(snippets, index, shortcut)
    val canSave = text.isNotBlank() && error == null
    fun save() {
        val new = Snippet(text, label.trim().ifEmpty { null }, Snippets.normalizeShortcut(shortcut))
        save(if (isNew) snippets + new else snippets.toMutableList().apply { set(index, new) })
    }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { save() },
        checkOk = { canSave },
        confirmButtonText = stringResource(R.string.save),
        neutralButtonText = if (isNew) null else stringResource(R.string.delete),
        onNeutral = {
            save(snippets.toMutableList().apply { removeAt(index) })
            onDismissRequest()
        },
        title = { Text(stringResource(if (isNew) R.string.snippet_add else R.string.snippet_edit)) },
        content = {
            LaunchedEffect(index) { focusRequester.requestFocus() }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    maxLines = 6,
                    label = { Text(stringResource(R.string.snippet_text)) },
                )
                TextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.snippet_label)) },
                )
                TextField(
                    value = shortcut,
                    onValueChange = { shortcut = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.snippet_shortcut)) },
                    isError = error != null,
                    keyboardActions = KeyboardActions {
                        if (canSave) {
                            save()
                            onDismissRequest()
                        }
                    }
                )
                when (error) {
                    ShortcutError.INVALID_CHARACTERS -> stringResource(R.string.snippet_shortcut_invalid)
                    ShortcutError.IN_USE -> stringResource(R.string.snippet_shortcut_used)
                    null -> null
                }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    )
}
