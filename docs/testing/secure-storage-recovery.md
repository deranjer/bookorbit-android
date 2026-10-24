# Test PR #85: encrypted storage recovery

Use `com.bookorbit.debug`, not the production installation. Build/install the full debug APK,
log into your normal server, browse/download a book, force-stop and reopen. Login should survive
a normal restart. Sign out/in once and verify normal use still works.

## Controlled failure tests

With USB debugging authorized (or an emulator), run from this PR's checkout:

```powershell
.\gradlew.bat :app:connectedFullDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.bookorbit.core.auth.SecureStorageRecoveryTest'
```

The tests have separate preference files, a separate Keystore alias and a database marker.
They do not erase the app's real login or databases. They verify:

- Deleting the test Keystore key simulates encrypted preferences restored without their key.
  Reopening resets credentials, keeps the database owner and can save new credentials.
- The original encrypted `data_owner` mapping migrates into ordinary, backed-up preferences.
- A restored legacy database with no recoverable owner is retained but quarantined, so a login
  to another server cannot silently claim it. Its old data will not automatically reappear;
  identifying the original server safely requires a separate recovery mechanism.

## Backup/restore check

The manifest declares both pre-Android-12 backup rules and Android-12+ extraction rules.
Both exclude only `bookorbit_secure_prefs.xml`, which contains credentials AND Tink keysets.
`bookorbit_data_ownership.xml` remains eligible for backup alongside the databases.

A real backup/device-transfer test still requires two devices or a reset emulator: back up
the debug app with downloaded data, restore, then expect server setup/login rather than a crash.
Reconnect to the original server to access retained data; a different server must get a separate
database. USB key-loss tests cover the original crash without requiring a factory reset.
