# ANDROID APPLICATION DEVELOPMENT PROMPT
## Personal Purchasing & Maintenance Request Ticket System

### 1. Project Objective

Build a complete, production-quality native Android application for managing industrial maintenance parts requests, procurement requests, and purchasing-related tasks.

The application is intended for **one individual user**, acting as a maintenance parts buyer at an industrial manufacturing facility.

It should resemble a professional IT help desk ticketing application, but be optimized for procurement workflows.

The core purpose is to track requests that fall outside the scope of traditional purchase requisition forms, emails, and purchasing systems.

The application must be:

- Fully functional offline.
- Designed primarily for Android smartphones.
- Fast, modern, intuitive, and professional.
- Highly customizable through settings.
- Backed by a reliable local database.
- Capable of storing attached photos and videos.
- Equipped with a complete, permanent ticket audit history.
- Able to generate local Android reminder notifications.
- Designed to accommodate future enhancements without requiring a complete rebuild.

**Do not create a mockup, nonfunctional prototype, or basic demonstration. Build a complete, working application.**

### 2. Technology Requirements

Use the following preferred technology stack:

- Language: Kotlin.
- UI: Jetpack Compose with Material 3.
- Architecture: MVVM with a clean repository/data layer.
- Database: Room using SQLite.
- Settings storage: Jetpack DataStore.
- Background reminder scheduling: WorkManager and appropriate Android alarm APIs.
- Media storage: App-private device storage.
- Minimum Android support: Android 9 or newer, preferably, unless technical considerations justify another baseline.
- Build system: Gradle with Kotlin DSL.

Use stable, compatible dependencies and current Android development best practices.

No cloud database, web server, external account, login system, or internet connection should be required.

### 3. Ticket Categories and Statuses

The application must distinguish between a ticket's broad **category** and its detailed **status**.

There are two primary ticket categories:

#### ACTIVE / OPEN

Statuses:

1. Open
2. Awaiting Quote
3. Awaiting Approval
4. On Hold

All four statuses must appear in the Active/Open ticket category.

Moving a ticket between any of these statuses must not archive or close the ticket.

#### ARCHIVED / CLOSED

Statuses:

1. Ordered
2. Cancelled

Changing a ticket to either Ordered or Cancelled must automatically remove it from the active ticket list and place it in the archive.

Ordered must be considered successful completion of the purchasing request.

Cancelled must be considered termination of the request without completion.

Both retain their status as Ordered or Cancelled rather than becoming a generic "Closed" status.

All archived tickets must remain searchable, viewable, and accessible indefinitely.

#### Status Transitions

Allow flexible transitions between active statuses rather than requiring strict sequential movement.

For example, a ticket may move directly from Open to Awaiting Approval.

All status changes must generate audit events.

#### On Hold Behavior

When a ticket is moved to On Hold:

- Require a nonempty hold reason.
- Record the hold reason in the ticket history.
- Record the previous status.
- Record the exact timestamp.
- Preserve the ticket as active.
- Clearly display that the ticket is On Hold.

Example hold reasons:

- Waiting on technical specifications.
- Vendor response pending.
- Requester clarification needed.
- Budget restriction.
- Pending internal decision.

The user may enter any custom reason.

When a ticket is taken off hold, allow the user to choose its new active status, with the previous status suggested by default.

Record the transition in the audit log. Preserve all historical hold reasons, including after reopening or closure.

#### Reopening Archived Tickets

Both Ordered and Cancelled tickets must be reopenable.

When the user selects Reopen:

1. Display a reopening dialog.
2. Require the user to enter a reopening reason.
3. Require the user to select the active status to which the ticket should return:
   - Open
   - Awaiting Quote
   - Awaiting Approval
   - On Hold
   - Any additional active statuses supported by configuration.
4. If On Hold is selected, require a hold reason.
5. Confirm reopening.
6. Move the ticket back to the active list.
7. Preserve the previous closure and every associated audit record.

The audit log must record:

- The archived status before reopening.
- The reopening reason.
- The selected new status.
- The timestamp.
- The reopening event itself.

A ticket may be reopened, closed, cancelled, and reopened again as many times as necessary.

Never overwrite or discard earlier closure and reopening information.

### 4. Default Ticket Fields

Every newly created ticket must have these default fields:

| Field | Type | Purpose |
|---|---|---|
| Title | Short Text | Brief description of request |
| Description | Long Text | Detailed request information |
| Requester | Short Text | Person requesting the part |
| Equipment | Short Text | Equipment associated with request |
| Part Number | Short Text | Requested manufacturer or internal part number |
| Quantity Requested | Integer Number | Quantity needed |
| Priority | Single-Select Dropdown | Purchasing urgency |
| Notes | Long Text | Additional details |
| Photos & Videos | Media Attachments | Images and video recordings |

Priority must initially offer these exact options, in the following urgency order:

1. Line Down
2. Imminent Failure
3. Weekend PM Item Request
4. Standard Leadtime

The priority field must support sorting using this defined priority ranking, not alphabetical order.

Ticket title should be required by default. Other fields should be optional unless configured otherwise.

Additional internal system fields include:

- Unique ticket ID.
- Date created.
- Date last updated.
- Current status.
- Active or archived category.
- Date closed, when applicable.
- Due date, when configured.
- Reminder information.

These system fields must be reliably maintained independently of customizable form fields.

### 5. Customizable Field System

This is one of the most important application features.

Create a comprehensive **Field Management** screen accessible from Settings.

The user must be able to:

- Add new fields.
- Rename existing fields.
- Reorder fields using drag-and-drop.
- Change field types where compatible.
- Change whether fields are required.
- Set default values.
- Edit dropdown selections.
- Reorder dropdown selections.
- Enable or disable fields.
- Remove fields from the active ticket form.
- Restore previously removed fields.
- Customize field descriptions and placeholder text.

#### Supported Field Types

At minimum:

1. Short Text — Single-line text entry.
2. Long Text — Multiline text entry.
3. Integer Number — Whole-number values.
4. Decimal Number — Decimal values.
5. Boolean — Yes/No toggle or selection.
6. Single-Select Dropdown — One choice from configured options.
7. Multi-Select Dropdown — Multiple configured choices.
8. Date — Android date picker.
9. Date & Time — Date/time selection.
10. Photo — Image attachment.
11. Video — Video attachment.
12. File Attachment — Generic document or other file.
13. URL — Link input with validation.

Provide appropriate data entry controls and validation for every field type.

#### Field Configuration

When creating a field, present a screen containing:

- Field name.
- Field type.
- Required/optional setting.
- Placeholder text.
- Optional helper description.
- Default value, when applicable.
- Available choices, for dropdown fields.
- Display order.
- Enabled/disabled setting.

For dropdowns, permit unlimited custom selections within reasonable device performance limits.

Fields should be reusable across all tickets.

The ticket creation and editing interfaces must be generated dynamically from these configurations rather than hard-coded.

#### Data Preservation

Custom field management must never destroy audit history.

If a field is removed, retire it from current forms while retaining its definition and historical values.

If a field is renamed, old audit events must still accurately represent what happened at the time.

If a field type changes, safely migrate compatible values. For incompatible changes, require user confirmation and retain the prior values in historical records.

Use persistent field IDs independent of their displayed names.

Record field configuration changes in an application-level settings audit log.

### 6. Ticket Creation

Provide a prominent **New Ticket** action.

The ticket creation screen should dynamically display the fields configured in Settings.

Requirements:

- Fast entry with a clean vertical form.
- Appropriate keyboards for each field type.
- Clear required-field indicators.
- Input validation.
- Scrollable layout.
- Photo and video attachment support.
- Save and Cancel actions.
- Optional due-date selection.
- Optional reminder configuration.

When a ticket is saved:

- Generate a unique, stable ticket ID.
- Assign its initial status as Open.
- Record creation date and time.
- Add a ticket-created audit event.
- Save all field values to the local database.
- Make the ticket immediately visible on the dashboard.

Prevent accidental loss of unsaved work by providing appropriate confirmation or local draft handling.

### 7. Ticket Detail Screen

Every ticket should have its own detailed screen.

Display:

- Ticket number.
- Title.
- Current status.
- Priority.
- Creation date.
- Last updated date.
- All configured ticket fields.
- Attached photos and videos.
- Due date and reminders.
- Notes.
- Full audit/activity timeline.

Provide actions to:

- Edit ticket.
- Change status.
- Place on hold.
- Resume from hold.
- Mark Ordered.
- Cancel ticket.
- Reopen archived ticket.
- Add notes.
- Add attachments.
- Update reminders.
- View history.

Archived tickets should remain fully viewable.

To prevent accidental historical modifications, normal active-ticket editing should be restricted while archived. Reopening should be the standard path for making new operational updates.

### 8. Full Audit Logging System

This is a critical requirement.

Every ticket must retain a complete and permanent audit history.

Audit entries must be append-only through normal application operations.

The application must record at minimum:

- Ticket creation.
- Ticket status changes.
- Priority changes.
- Every field modification.
- Previous and new field values.
- Note creation and modification.
- Attachment additions.
- Attachment removals from the active view.
- Due-date changes.
- Reminder changes.
- Hold events and hold reasons.
- Release-from-hold events.
- Closure events.
- Cancellation events.
- Reopening events and reopening reasons.

Every event must include:

- Unique audit event ID.
- Ticket ID.
- Event type.
- Exact timestamp, including timezone information.
- Field involved, if applicable.
- Previous value, if applicable.
- New value, if applicable.
- Reason or additional context, if applicable.

Store machine-readable UTC timestamps while displaying them in the device's local time.

#### Audit Timeline Interface

Show events chronologically, with newest events first by default.

Example:

**November 1, 2026 — 2:45 PM**

Status changed: Awaiting Quote → On Hold

Reason: Waiting for equipment measurements.

**November 1, 2026 — 11:20 AM**

Priority changed: Standard Leadtime → Imminent Failure

**October 31, 2026 — 9:15 AM**

Ticket created.

Provide an option to expand individual events to view complete details.

#### Audit Retention

- Do not automatically prune history.
- Do not erase history after closure.
- Do not erase history after cancellation.
- Do not erase history after reopening.
- Preserve deleted/retired field information in audit records.
- Preserve attachment history and associated files, including media removed from the current ticket view.
- Maintain reliable chronological ordering.
- Never use audit-entry updates to overwrite previous history.

Avoid implementing ordinary hard-delete functionality for tickets. Use archival instead.

Where feasible, write field updates and their audit events within the same database transaction to prevent inconsistencies.

Audit records should be protected from accidental editing within the app. Do not claim protection against users who deliberately modify the device database externally.

### 9. Ticket Dashboard

Create a professional, responsive home dashboard.

The main screen should prioritize active purchasing requests.

Suggested dashboard summary:

- Total Active Tickets.
- Open.
- Awaiting Quote.
- Awaiting Approval.
- On Hold.
- Overdue.
- Ordered/Cancelled totals accessible through the archive.

Provide an Active Tickets view and an Archived Tickets view.

Each ticket list item should display:

- Ticket ID.
- Title.
- Requester, when available.
- Equipment, when available.
- Priority.
- Status.
- Creation or last-updated date.
- Due date, when present.
- Overdue indication, when applicable.

Use clear status indicators, readable typography, and compact cards or list rows.

### 10. Sorting, Filtering, and Searching

Provide comprehensive organization features.

#### Sorting

Allow sorting by:

- Priority.
- Date created.
- Date last updated.
- Due date.
- Status.
- Requester.
- Equipment.
- Title.
- Ticket ID.
- Custom fields where their data types support meaningful sorting.

Allow ascending and descending order.

Custom dropdown fields should support sorting by their configured option order.

Handle missing values consistently.

#### Filtering

Allow simultaneous filters including:

- Ticket status.
- Active versus archived.
- Priority.
- Requester.
- Equipment.
- Creation date range.
- Due date range.
- Overdue tickets.
- Custom field values where applicable.

Provide an easy way to clear filters.

#### Searching

Support fast local search across:

- Ticket IDs.
- Titles.
- Descriptions.
- Requesters.
- Equipment.
- Part numbers.
- Notes.
- Text-based custom field values.

The search system must work without internet access.

Remember the user's preferred default sort order and optionally remember the most recent filters.

### 11. Due Dates and Android Notifications

Every ticket must support an optional due date.

A ticket may have one or more reminders associated with its due date.

Examples:

- At due date.
- One hour before.
- One day before.
- Three days before.
- Custom date and time.

Requirements:

- Use native Android notifications.
- Work without internet access.
- Support reminders when the application is not open, subject to Android background scheduling rules.
- Request notification permission where required.
- Allow users to reschedule reminders.
- Support overdue indications.
- Allow notification taps to open the correct ticket.
- Cancel pending reminders when a ticket becomes Ordered or Cancelled.
- Restore eligible reminder schedules if an archived ticket is reopened.
- Avoid duplicate notifications.
- Handle device restarts, time changes, and timezone changes correctly.
- Follow Android restrictions concerning exact alarms and battery management.

If precise alarms require special permission, explain the limitation and provide an appropriate fallback instead of silently failing.

### 12. Photos, Videos, and Attachments

The application must support attaching multiple photos and videos to individual tickets.

Support:

- Selecting existing media from the device.
- Taking a new photo using the camera.
- Recording a new video where supported.
- Multiple attachments per ticket.
- Image previews.
- Full-screen image viewing.
- Video playback.
- Attachment names and timestamps.
- Optional file attachments for PDFs and other documents.

When importing media, copy it into durable application-managed local storage rather than relying on temporary external content URIs.

Media must remain available offline.

Use efficient image thumbnails and avoid loading full-resolution videos into memory unnecessarily.

When an attachment is removed from the current ticket display, preserve the historical attachment and audit reference so the complete history remains intact.

### 13. Settings

Provide a dedicated Settings section with these areas:

#### Field Management

Manage all configurable ticket fields, field types, ordering, validation, defaults, and dropdown values.

#### Priority Management

Allow modification of priority labels, choices, and sorting order.

Initially provide the four specified purchasing priority options.

#### Status Management

Retain the required built-in statuses and Active/Archived semantics.

Optionally allow additional user-defined active statuses, display-name customization, and status sorting order.

Do not permit customization to break the required distinction between active and archived tickets or remove the ability to identify Ordered and Cancelled outcomes.

#### Notifications

Configure default reminder settings and notification preferences.

#### Display

Support light theme, dark theme, and system theme.

#### Data Management

Provide backup, restore, and export features.

#### About

Show app version, local database information, and basic application details.

### 14. Local Storage and Data Integrity

All application data must remain on the user's Android device.

Use Room/SQLite for structured data.

Store:

- Tickets.
- Field definitions.
- Field values.
- Dropdown options.
- Priorities.
- Status configuration.
- Notes.
- Audit events.
- Reminder information.
- Attachment metadata.
- Settings audit events.

Store attachment files in private device storage.

Use stable identifiers and appropriate foreign-key relationships.

Implement database migrations that preserve existing data when the application is updated.

Do not introduce cloud sync or user accounts.

The app must remain usable in airplane mode.

### 15. Backup, Restore, and Export

Because all information is local, provide reliable manual backups.

The user should be able to export a complete application backup containing:

- Tickets.
- Current field values.
- Field configurations.
- Settings.
- Full audit history.
- Photos.
- Videos.
- Attachments.
- Reminder configurations.

Use a portable, versioned backup format.

Support backup file creation through the Android document picker so the user can select a destination.

Support restoring a backup through the same system.

Validate backups before importing.

Include integrity checks and clear error handling.

Never silently overwrite the existing database during restore.

Allow the user to confirm replacement or follow a clearly defined merge process.

Provide additional CSV export for ticket lists, with the ability to include configured custom fields.

The complete backup must retain the audit trail even if CSV export cannot represent every historical event.

### 16. Design and User Experience

Use a clean, professional Material 3 design suitable for frequent use in an industrial purchasing environment.

Suggested navigation:

- Dashboard.
- Active Tickets.
- Archived Tickets.
- Settings.

A prominent floating action button or equivalent action should create a new ticket.

Design considerations:

- Clear visual hierarchy.
- Readable ticket statuses.
- Color-coded priorities.
- Minimal unnecessary navigation.
- Fast data entry.
- Responsive scrolling.
- Touch-friendly controls.
- Consistent form components.
- Good contrast and accessibility.
- Native Android date and time pickers.
- Drag-and-drop field reordering.
- Confirmation dialogs for major operations.
- Accessible labels and screen-reader support.

The user should typically be able to open a ticket and change its status within a few interactions.

Do not rely exclusively on color to communicate status or priority.

### 17. Edge Cases and Expected Behavior

Ensure the following situations work correctly:

1. A ticket can be placed On Hold multiple times with distinct reasons.
2. The user can resume a held ticket to a selected active status.
3. A ticket can move directly from Open to Ordered.
4. Ordered automatically archives the ticket.
5. Cancelled automatically archives the ticket.
6. An archived ticket can be reopened only after supplying a reason.
7. Reopening allows selection of a new active status.
8. Reopening to On Hold requires a hold reason.
9. Reopening retains every previous closure event.
10. A custom field can be renamed without losing ticket data.
11. A field can be removed from current forms without deleting historical values.
12. Custom field types cannot be changed in ways that silently destroy information.
13. Editing a ticket creates correct old/new value audit entries.
14. Notes and attachment changes are audited.
15. Removing an attachment from the active view does not erase its historical record or file.
16. Archived tickets remain searchable.
17. Sorting and filtering work together.
18. Reminders stop when tickets are archived.
19. Reminder schedules work appropriately after reopening.
20. Application upgrades preserve all data.
21. Backups retain full history and attachments.
22. The application functions entirely offline.
23. Repeated rapid taps must not create duplicate tickets, transitions, or audit events.
24. All operations must handle app interruption without creating partially updated ticket state.

### 18. Testing Requirements

Include meaningful automated tests for:

- Ticket creation and editing.
- Field configuration changes.
- Field value persistence.
- Status transitions.
- On Hold behavior.
- Ordered/Cancelled archival.
- Reopening with mandatory reasons.
- Audit log completeness.
- Atomic field/audit updates.
- Sorting and filtering.
- Reminder scheduling and cancellation.
- Database migrations.
- Backup/restore integrity.

Include UI tests for core workflows where practical.

Verify that archived tickets preserve all earlier history after repeated reopening and closure cycles.

Do not replace testing with assumptions that the code should work.

### 19. Required Deliverables

Provide:

1. Complete Android Studio project.
2. All Kotlin source code.
3. Complete Jetpack Compose UI implementation.
4. Local database entities, DAOs, repositories, and migrations.
5. Field customization implementation.
6. Ticket management and status workflow logic.
7. Append-only audit system.
8. Media and attachment handling.
9. Notification scheduling implementation.
10. Backup, restoration, and export functionality.
11. Settings implementation.
12. Necessary manifests, permissions, and resources.
13. Gradle build configuration.
14. Automated tests.
15. README containing build, installation, usage, and troubleshooting instructions.
16. Debug APK if the build environment supports Android compilation.

All source files must be included in the finished project. Do not provide pseudocode in place of working implementations.

### 20. Development Instructions for the AI Agent

Treat all requirements in this prompt as mandatory unless explicitly identified as optional.

Implement the application using a maintainable architecture with clear separation between UI, business rules, storage, and notification scheduling.

Build the application in coherent stages while maintaining a functional and compilable project.

Do not substitute static demonstration screens for real database-backed functionality.

Do not hard-code the default ticket fields directly into the ticket UI. The custom field configuration system must drive field rendering and storage.

Do not simplify the audit system to a basic list of status changes. It must capture the full set of events described above.

Do not discard audit history during archival, reopening, settings changes, or data migrations.

If the environment supports compilation, compile and fix all errors, run available automated tests, and provide the resulting working APK.

Where technical decisions are not specified, choose robust, maintainable Android-native implementations that satisfy the stated requirements.

**Final objective:** Deliver a fully functional, offline-first, customizable Android purchasing ticket management application with a professional UI, reliable local storage, complete auditability, status-based archival and reopening, and native Android reminders.