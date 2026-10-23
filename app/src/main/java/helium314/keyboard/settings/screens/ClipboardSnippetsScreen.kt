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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.database.ShortcutError
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog

@Composable
fun ClipboardSnippetsScreen(
    onClickBack: () -> Unit,
) {
    val dao = ClipboardDao.getInstance(LocalContext.current)
    // clips are changed in place in the dao cache, so we need to trigger recomposition after saving
    var savedCount by remember { mutableIntStateOf(0) }
    val clips = remember(savedCount) { dao?.getAll()?.filter { it.isPinned && !it.text.isNullOrBlank() }.orEmpty() }
    var selectedClip: ClipboardHistoryEntry? by remember { mutableStateOf(null) }
    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.clipboard_snippets)) },
        filteredItems = { term ->
            clips.filter { it.text!!.contains(term, true) || it.shortcut?.startsWith(term, true) == true }
        },
        itemContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedClip = it }
                    .padding(vertical = 6.dp, horizontal = 16.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(it.text!!, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (it.shortcut != null)
                        Text(it.shortcut!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.clipboard_snippet_edit))
            }
        },
        content = if (clips.isEmpty()) {
            { Text(stringResource(R.string.clipboard_snippets_empty), Modifier.padding(16.dp)) }
        } else null
    )
    val clip = selectedClip
    if (clip != null && dao != null) {
        EditSnippetDialog(clip, dao, onChanged = { savedCount++ }) { selectedClip = null }
    }
    ExtendedFloatingActionButton(
        onClick = { selectedClip = ClipboardHistoryEntry(NEW_SNIPPET_ID, 0, true, "", null, null) },
        text = { Text(stringResource(R.string.clipboard_snippet_add)) },
        icon = { Icon(painter = painterResource(R.drawable.ic_plus), stringResource(R.string.clipboard_snippet_add)) },
        modifier = Modifier.wrapContentSize(Alignment.BottomEnd).padding(all = 12.dp)
            .then(Modifier.safeDrawingPadding())
    )
}

@Composable
private fun EditSnippetDialog(clip: ClipboardHistoryEntry, dao: ClipboardDao, onChanged: () -> Unit, onDismissRequest: () -> Unit) {
    val isNew = clip.id == NEW_SNIPPET_ID
    val focusRequester = remember { FocusRequester() }
    var text by remember { mutableStateOf(clip.text ?: "") }
    var shortcut by remember { mutableStateOf(clip.shortcut ?: "") }
    val error = dao.getShortcutError(clip.id, shortcut)
    fun save() {
        if (dao.saveSnippet(if (isNew) null else clip.id, text, shortcut)) onChanged()
    }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { save() },
        checkOk = { text.isNotBlank() && error == null },
        confirmButtonText = stringResource(R.string.save),
        neutralButtonText = if (isNew) null else stringResource(R.string.delete),
        onNeutral = {
            dao.deleteClip(clip.id)
            onChanged()
            onDismissRequest()
        },
        title = { Text(stringResource(if (isNew) R.string.clipboard_snippet_add else R.string.clipboard_snippet_edit)) },
        content = {
            LaunchedEffect(clip) { focusRequester.requestFocus() }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    maxLines = 6,
                    label = { Text(stringResource(R.string.clipboard_snippet_text)) },
                )
                TextField(
                    value = shortcut,
                    onValueChange = { shortcut = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.clipboard_snippet_shortcut)) },
                    isError = error != null,
                    keyboardActions = KeyboardActions {
                        if (text.isNotBlank() && error == null) {
                            save()
                            onDismissRequest()
                        }
                    }
                )
                when (error) {
                    ShortcutError.INVALID_CHARACTERS -> stringResource(R.string.clipboard_snippet_shortcut_invalid)
                    ShortcutError.IN_USE -> stringResource(R.string.clipboard_snippet_shortcut_used)
                    null -> null
                }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    )
}

private const val NEW_SNIPPET_ID = -1L
