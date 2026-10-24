# Importing SwiftKey clipboard shortcuts as snippets

HeliBoard can import the clipboard shortcuts you saved in SwiftKey. Each one becomes a snippet. Typing the snippet's shortcut suggests its text, and you can tap it in the snippets panel.

## How to import

1. Get SwiftKey's clipboard export, `clipboard_sync.json`, onto the phone.
2. Open HeliBoard settings > **Preferences** > **Snippets**.
3. Tap the **Import SwiftKey snippets** button, above **Add snippet**.
4. Pick `clipboard_sync.json`. You can pick it from any file provider the system picker offers, such as Downloads or device storage.
5. A dialog shows how many snippets were imported and how many entries were skipped, and why.

The new snippets go to the end of your list. Nothing is replaced or removed.

## What the file looks like

The fields below are from a made-up file in the same format:

```json
{
  "shortcuts": [
    { "id": "a1", "text": "On my way!", "pinned": false, "time": 1700000000000,
      "shortcut": "omw", "last_modified": 1700000000000 },
    { "id": "b2", "text": "123 Example Street", "pinned": true, "time": 1700000500000,
      "shortcut": "addr", "last_modified": 1700000500000 }
  ],
  "deleted_items": [
    { "id": "c3", "deleted_at": 1700001000000 }
  ]
}
```

| SwiftKey field | What HeliBoard does with it |
|---|---|
| `text` | Becomes the snippet text, stored as is. It is inserted with spaces and line breaks at the start and end removed, the same as other snippets. |
| `shortcut` | Becomes the snippet shortcut. Spaces around it are removed and it is lowercased. An empty or missing shortcut means the snippet has no shortcut. |
| `time` | Sets the order: entries are added oldest first. A missing `time` counts as oldest. |
| `id`, `pinned`, `last_modified` | Ignored. |
| `deleted_items` | Ignored. These are entries already deleted in SwiftKey. |
| Any other field | Ignored. |

Imported snippets have no label, so the panel shows their text. You can add a label later by tapping the snippet in the Snippets screen.

## Which entries are skipped

Each entry is checked in this order. The first rule that matches decides the skip reason shown in the result dialog.

| Result line | When |
|---|---|
| Skipped, no text | `text` is missing, `null`, or only spaces. |
| Skipped, already saved | A snippet with the same text (ignoring spaces at the start and end) and the same shortcut already exists. Because of this, importing the same file twice adds nothing the second time. |
| Skipped, shortcut isn't only letters and digits | The shortcut contains spaces, punctuation or symbols. Letters from any language and digits are allowed, so `qaé` or `größe2` work. |
| Skipped, shortcut used by another snippet | Another snippet already has this shortcut, or an earlier entry in the same file does. Case doesn't matter: `Sig` and `sig` are the same shortcut. |

Lines with a count of 0 are not shown. Example result:

```
Snippets imported: 9
Skipped, shortcut used by another snippet: 1
Skipped, shortcut isn't only letters and digits: 1
Skipped, no text: 2
```

An entry whose shortcut is in use or invalid is skipped completely; it is not imported without its shortcut. To keep it, change the shortcut in SwiftKey or in the file, or add the snippet by hand.

## When nothing is imported

The dialog says "This is not a SwiftKey clipboard file (clipboard_sync.json). Nothing was imported." and your snippets stay as they were when:

- the file is not valid JSON
- the file has no `shortcuts` list
- the file is larger than 10 MB (the same limit as the SwiftKey vocabulary import; a clipboard export is normally a few KB)
- the file can't be read

A byte order mark at the start of the file is ignored, so files saved that way still import.

## After importing

- Typing a shortcut suggests the snippet's text, ignoring case.
- With **Expand snippet shortcuts** on (Preferences, below Snippets), a shortcut followed by a space or punctuation is replaced by its text, and backspace right after puts the shortcut back.
- The snippets appear at the end of the snippets panel (the Snippets toolbar key).
- Snippets are stored in HeliBoard's settings, so they are included in settings backups.
- There is no undo for an import. To remove imported snippets, tap each one in the Snippets screen and choose Delete.

## Privacy

A real export contains your saved clipboard text. HeliBoard reads it on the phone and stores the snippets locally; nothing is sent anywhere. Keep real exports out of bug reports, issues and the repository. Use a made-up file like the example above when reporting a problem.

## Not verified

- Where SwiftKey lets you export `clipboard_sync.json` from. HeliBoard only reads the file.
- Import from a real SwiftKey export. Testing used made-up files in the format above, so details such as whether SwiftKey writes a byte order mark are unconfirmed.
- Picking the file from cloud storage apps such as Google Drive or OneDrive.

## For developers

| What | Where |
|---|---|
| Parsing and skip rules | `Snippets.fromSwiftKey` in `app/src/main/java/helium314/keyboard/latin/utils/Snippets.kt` |
| File picker and result dialog | `swiftKeySnippetsPicker` in `app/src/main/java/helium314/keyboard/settings/dialogs/SwiftKeyImportDialog.kt` |
| Import button | `SnippetsScreen` in `app/src/main/java/helium314/keyboard/settings/screens/SnippetsScreen.kt` |
| Strings | `snippets_import_*` in `app/src/main/res/values/strings.xml` |
| Tests | `SwiftKey export is imported...` and `SwiftKey import rejects other files` in `app/src/test/java/helium314/keyboard/latin/SnippetsTest.kt` |

Added in PR #17 (issue #15).
