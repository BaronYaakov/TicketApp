# Version 1.1 validation

Validated November 1, 2026, in the managed cloud environment with JDK 21, Android SDK 35, Gradle 8.13, and an Android 11 / API 30 emulator. The emulator uses software acceleration and an 800×1200 physical display, with actual app windows resized between 400×880 dp (cover-like) and 768×896 dp (unfolded-like). The application has no Internet permission.

| Check | Final result |
| --- | --- |
| Debug APK build | Passed; version 1.1.0, version code 2 |
| Unit tests | 17 passed; 0 failures/errors/skips |
| Android instrumented tests | 17 passed; 0 failures/errors/skips |
| Android lint | 0 errors; 0 warnings |
| APK update signing | Same certificate as the original version 1.0 debug APK |

## Foldable behavior verified

Three real Android UI tests verify:

- The All scope includes active and archived tickets, selection is highlighted, and the panes do not overlap.
- Scrolling the detail timeline leaves list position unchanged; scrolling the list leaves detail position unchanged. Ticket actions remain visible while scrolling, and returning to a ticket restores its detail scroll position.
- Touch dragging changes pane width. Accessibility adjustment clamps both panes to their minimum widths. The chosen width persists in DataStore and is restored after activity recreation together with selection.
- An unsaved edit survives compact → expanded window transitions. Selecting another ticket while editing requires confirmation; cancelling that navigation keeps the edit. Saving commits the actual field value. Selection remains intact after folding and activity recreation.

The four new unit tests cover compact/expanded window classification, sizing bounds over several widths and invalid fractions, proportional width restoration, and combined All-scope search/sort behavior.

The regression suite also rechecks ticket creation/editing, repeated hold/archive/reopen cycles, field conversion/retirement history, atomic field/audit rollback, schema migration, backup integrity/restore recovery, and Android reminder delivery/deduplication/cancellation. The compact create/order/reopen workflow is exercised through the UI.

## Limits of validation

A physical Galaxy Z Fold7 was not available. The adaptive UI is verified at representative logical window sizes, not against Samsung hardware or hinge sensors. Real Fold7 acceptance testing should include device-specific display zoom/font scales, hinge/flex postures, keyboard behavior, accessibility services, multi-window mode, and physical camera/video capture. Android 13+ notification permission and OEM battery restrictions remain part of physical-device QA. Reminders remain inexact WorkManager notifications as documented in the app.

This is a debug-signed APK; production distribution requires release signing and device acceptance testing. The Room schema is unchanged by this UI update. The environment configuration was not republished or changed by this feature work.

## Reproduce

```sh
source scripts/cloud-env.sh  # managed cloud only
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# On a booted, sufficiently large emulator (see README.md):
./gradlew :app:connectedDebugAndroidTest
```

## APK integrity

`PurchasingDesk-1.1.0-debug.apk` SHA-256:

`0c98a98d91f2f52d64dfda59fbcbba10e6ccbbdd24bc735e8ea28d3e98e7745b`
