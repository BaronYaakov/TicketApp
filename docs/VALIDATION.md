# Validation evidence

Validated in the managed cloud environment on November 1, 2026, using Temurin JDK 21, Android SDK 35, Gradle 8.13, and an AOSP Android 11 / API 30 x86_64 emulator. Software emulation was used because `/dev/kvm` is unavailable. The device test run executed in airplane mode. The APK manifest was inspected with `aapt dump permissions`: no Internet permission is requested.

| Check | Actual outcome |
| --- | --- |
| `:app:assembleDebug` | Passed; installable, debug-signed APK produced |
| `:app:testDebugUnitTest` | 13 passed; 0 failed, 0 skipped |
| `:app:connectedDebugAndroidTest` | 14 passed; 0 failed, 0 skipped |
| `:app:lintDebug` | 0 errors; 0 warnings |
| `scripts/cloud-setup.sh` | Executed successfully end to end on the current instance |
| Native dashboard | Rendered and visually inspected on the emulator |
| Reusable environment configuration | `install_script` and `start_skill` saved to the environment draft |

The instrumented suite verifies actual SQLite persistence and field/audit rollback after a deliberately rejected audit insert; repeated hold/archive/reopen cycles; stale edits and idempotent creation; field rename/retirement/conversion; note, attachment, and reminder history; reminder rescheduling; schema migration; rejected corrupt backups; backup restoration with removed media; and restore-journal recovery on both sides of the commit boundary. Android reminder tests verify real notification delivery, deduplication, no delivery for archived/future reminders, unique scheduling, cancellation, and restoration after reopening. The Compose test verifies that an unsaved form survives activity recreation, creates and saves a request, orders/archives it, requires a reopening reason, reopens it, and checks that editing is available again.

## Device QA before production distribution

The following are implemented but not exhaustively validated by this environment's API 30 emulator: physical-camera photo/video capture, representative manufacturer video formats and document handlers, Android 13+ notification permission grant/denial, OEM Doze/battery restrictions, actual power-off/reboot and wall-clock/timezone changes, screen-reader interaction, large real-world datasets/backups, and a multi-version physical-device matrix. WorkManager notifications are inexact and can be delayed by Android. The app explains this limitation; no exact-alarm permission is requested.

The delivered APK uses debug signing. Production distribution still requires release signing and physical-device acceptance testing. The environment draft has not been published, and restoration in a fresh cloud task has not been independently verified. Review/save the environment settings and publish to retain the prepared filesystem snapshot.

## APK integrity

`PurchasingDesk-debug.apk` SHA-256:

`661a37be5fd1e0a377bce87f96244e86ff1128d29fb502caacde5b7ab23675db`
