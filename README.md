# Purchasing Desk (TicketApp)

A native, single-user Android purchasing and maintenance request tracker. Kotlin, Jetpack Compose Material 3, MVVM, Room/SQLite, DataStore, and WorkManager. Runs on Android 9 (API 28) and newer. The application has **no Internet permission**, account, server, analytics, or cloud database. Manual backups go only to the document provider you choose.

## Build and install

Open this directory in Android Studio with a JDK 17 or 21, Android SDK platform 35, and build-tools 35.0.0. Let Android Studio create `local.properties`, or set `ANDROID_HOME` to your SDK directory.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. It uses Android's development signing key; configure your own protected release signing credentials before distributing a release. No signing secrets are included.

The project pins Gradle 8.13 (wrapper distribution SHA-256 verified), Android Gradle Plugin 8.9.2, Kotlin 2.1.20, and compatible Compose/Room dependencies. Builds need access to the official Google/Maven/Gradle repositories; the installed app does not.

### Managed cloud environment

Use the existing `/workspace/TicketApp` checkout. Each cloud task already has an isolated environment; do not create a Git worktree unless explicitly requested.

```sh
bash scripts/cloud-setup.sh
source scripts/cloud-env.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The setup script retains a verified Temurin JDK and official Android SDK under `/workspace/toolchains`, accepts SDK licenses, installs platform/build tools, and builds/tests the project. It preserves dependency declarations and existing local configuration. `cloud-env.sh` activates those tools and uses the inherited proxy and system CA trust without disabling TLS verification. A filesystem snapshot retains tools/dependencies; new task processes must source the activation script again.

## Use

- **Dashboard / Active:** Create a ticket with the prominent New ticket button. Fields are loaded from their persistent definitions, including custom fields. Title starts required; other fields start optional. New-ticket drafts persist on the device. Use Save to commit or Cancel to leave the saved draft.
- **Archive:** Ordered means successfully completed; Cancelled means terminated. Both archive immediately, preserve their distinct outcomes, stop pending notifications, and remain searchable. Archived requests are read-only. Reopen requires a reason and an active destination; reopening to On Hold additionally requires a hold reason.
- **Ticket detail:** Edit fields, due date, notes, attachments, reminders, or status. All operational mutations append old/new audit records in the same transaction as the data update. Timeline entries expand to show identifiers, complete values, and UTC timestamps. Display timestamps use the device timezone.
- **On Hold:** Supply a reason each time. The previous active status is suggested when resuming. Hold reasons, closures, cancellations, and reopening reasons accumulate permanently.
- **Fields:** Settings → Manage ticket fields. Add any of the 13 supported types, configure labels, required state, helper/placeholder text, defaults, enabled state, and choices. Long-press/drag or use accessible arrow buttons to reorder fields. Dropdown options have explicit up/down ordering. Priority stays a single-select dropdown; its configured option order determines urgency sorting. Built-in status/category semantics are fixed.
- **Field preservation:** Rename keeps stable IDs and values. Retire removes a field from current forms without deleting it or its data. Restore brings it back. Incompatible conversion requires confirmation; prior values are copied to permanent per-ticket audit records before affected current values are cleared. Field configuration changes also appear in Settings audit history. Archived values can be affected only by this explicitly confirmed global schema operation, not ordinary ticket editing.
- **Find requests:** Search identifiers, all stored field text, and notes entirely locally. Combine statuses, overdue, creation/due ranges, and field-value filters. Sort ascending/descending by system fields or supported custom field types. Numbers sort numerically, dropdowns by configured order; missing values remain last. Default sort and direction are remembered.
- **Media:** Import multiple images, videos, or documents, or capture through an installed camera application. Imports copy bytes to durable app-private storage. Images have thumbnail/full-screen pinch zoom views; videos play in the app using Android's media player. Other documents open through an installed handler with a temporary read grant. Removing an attachment hides it from the current view but keeps metadata, bytes, and history; enable “Include removed attachments” to access it again.
- **Reminders:** Add due-relative or custom reminders; reschedule or cancel independently. Android 13+ requires notification permission. Reminders pause in the archive and eligible undelivered reminders resume on reopening. Unique work and stable notification tags avoid duplicate notifications. WorkManager restores work after device reboot; clock/timezone broadcasts reconcile schedules. A missed undelivered reminder runs when Android next permits it. Delivered reminders do not notify again just because a ticket is reopened.

### Reminder timing

WorkManager is intentionally inexact: Doze, battery saver, force-stop, and manufacturer restrictions can delay notifications. The app explains this in Settings and ticket detail. It does not request exact-alarm permission or silently promise precise delivery. Allow notifications and background operation in Android settings. A force-stopped app must be opened again for Android to resume its work.

## Foldable and large-window workspace (v1.1)

The layout responds to the **available app window width**, including safe-area insets. At 700 dp or wider, an unfolded-size window shows a ticket browser on the left and the selected ticket on the right. Narrower windows, such as the Fold7 cover display or a small split-screen window, retain phone navigation. There is no device-name or orientation assumption.

- The left pane defaults to **All** tickets, including Ordered and Cancelled. Use All / Active / Archived, local search, filters, and sorting to refine it. The selected ticket is highlighted. Filtering it out keeps its details visible and shows a notice in the browser.
- Both panes scroll independently. Search/scope controls stay above the list. Each ticket remembers its own detail scroll position when switching between tickets.
- Drag the divider horizontally to resize the browser. Its 48 dp interaction area includes a visible grip. Tap the grip for a slider/reset dialog, use accessibility adjustment actions, or focus it and press the left/right arrow keys (Home resets). Width is retained in DataStore and included in manual backups. Minimum widths are 260 dp for the list and 360 dp for details.
- New ticket and Settings stay in the top app bar. Edit / Change status / Add note stay above the scrolling detail body. In an expanded editor, Save and Cancel stay below the form and above the keyboard. Forms and detail content have a maximum readable width in larger windows.
- Selection, filters, list position, pane ratio, and unsaved ticket edits survive Android activity recreation. Folding/unfolding retains the selected ticket. Selecting a different ticket, opening Settings, or starting another request while editing asks before leaving unsaved edits.

The device UI tests resize an emulator between 400×880 dp and 768×896 dp. Android limits forced sizes to twice the emulator's physical dimensions, so the original tiny 320-pixel AVD cannot run the expanded tests. Start a large enough emulator, for example:

```sh
source scripts/cloud-env.sh
"$ANDROID_HOME/emulator/emulator" -avd TicketAppTests -skin 800x1200 -no-window -no-audio -no-boot-anim -no-snapshot -accel off -gpu swiftshader_indirect -cores 4 -memory 2048
# In a separate shell, after sys.boot_completed is 1:
./gradlew :app:connectedDebugAndroidTest
```

Run display-resizing tests on an emulator. They capture and restore prior size/density overrides. They do not replace acceptance testing on a physical Fold7, especially hinge postures, OEM keyboard behavior, font scaling, and multi-window transitions.

## Backups, restore, and CSV

Settings → Data management uses Android's document picker to export/restore a versioned ZIP. `data.json` contains structured records, full append-only histories, field definitions, preferences, notes, reminder configurations, and attachment metadata. `media/` includes every referenced attachment, including removed media. `manifest.json` contains format version and SHA-256 checksums. Unsaved new-ticket drafts are local working state and are not included in backups. Backups are not encrypted: choose a secure destination if data is sensitive. Checksums detect corruption, not deliberate forgery by a person who can rewrite the manifest.

Restore validates ZIP paths, entry uniqueness, checksums, schema version, identifiers, relationships, required field definitions, and media references before confirmation. Limits: 64 MiB per JSON entry, 16 GiB total uncompressed, 100,000 entries. Replacement requires explicit confirmation and first writes a complete safety backup of current data into app-private `files/safety-backups/`. Media are staged under fresh names; the logical database replacement is transactional. A durable restore journal reconciles DataStore settings with the committed database after interruption or process death. Old media are retained to support safety backups. No silent merge or overwrite is offered. Access safety backups through Android Studio Device Explorer (debug build), then restore them through the picker.

CSV contains every ticket and configured custom field, with proper quoting and spreadsheet-formula neutralization. It is a report, not a full backup; use ZIP to retain media and audit history. Android auto-backup/device transfer is disabled; uninstalling or clearing app storage permanently removes private data unless you have a manual backup elsewhere.

## Architecture and data integrity

- `data/Models.kt`: Room entities and serializable portable records; field IDs are independent of display names.
- `data/Repository.kt`: serialized commands, optimistic ticket revisions, idempotent creation, validation, and atomic mutation/audit transactions. No ordinary hard-delete or audit-update DAO is exposed.
- `domain/Rules.kt`: status/field invariants, typed sorting, combined query filtering, reminder eligibility.
- `ui/`: real database-driven Compose screens; the Android ViewModel handles background work and errors.
- `reminders/`: persistent unique WorkManager jobs, notification channel/deep links, boot/clock reconciliation.
- `backup/`: streaming ZIP media I/O, integrity/relationship validation, staged replacement, safety backup, and CSV.

Room schema v2 is exported in `app/schemas/`. The explicit v1→v2 migration only adds the audit timestamp index and preserves all records. Future schema changes must increment the database version and provide tested migrations. There is no destructive migration fallback. Epoch milliseconds are UTC machine timestamps; same-millisecond events use SQLite insertion order for stable newest-first history. Audit records are append-only through ordinary app operations; this is not protection against deliberate external database modification.

## Tests

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
# With a booted emulator or USB Android device:
./gradlew :app:connectedDebugAndroidTest
```

The unit suite covers transitions, mandatory reasons, archival, 13-type validation, conversion compatibility, priority/custom numeric sorting, simultaneous filters, archive search, reminder eligibility, malformed backup references/paths, hashes, and CSV safety. Instrumented tests cover real SQLite transactions, rollback with audit integrity, value persistence, repeated close/reopen/hold cycles, stale/duplicate actions, global field conversions, notes/media/reminder audits, schema migration, backup/restore with removed media, and a Compose create/archive/reopen workflow. See [validation notes](docs/VALIDATION.md) for actual executed results and remaining device checks.

## Troubleshooting

- **SDK missing:** Set `ANDROID_HOME` or use Android Studio's SDK manager. Cloud users should source `scripts/cloud-env.sh` in each new shell/task.
- **Proxy / certificate error:** Keep the inherited proxy, use its configured CA trust, and check environment network settings. Do not disable certificate validation. Required destinations include `dl.google.com`, Maven Central, the Gradle distribution host, and the official Temurin release on GitHub.
- **No camera / unsupported video / document handler:** Use device import, a supported Android media format, or install an appropriate local handler. The app reports unsupported playback instead of loading a full video into memory.
- **Can't edit archived request:** Reopen it with a reason first.
- **Stale editor:** A background operation changed the ticket revision. Leave and reopen the editor to load current data.
- **Restore rejected:** Preserve the original ZIP and check the reported integrity/version error. Do not manually bypass validation.
