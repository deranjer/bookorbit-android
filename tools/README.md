# Local development

## Android (Docker, no host JDK/SDK needed)

```sh
bash tools/android/gradle.sh :app:testFdroidDebugUnitTest :app:assembleFdroidDebug
python tools/android/test_storage_sql.py
node --test tools/android/test_reader_appearance.mjs tools/android/test_read_along.mjs
python tools/android/test_localization.py
```

The pinned image runs as your UID and caches dependencies in
`~/.cache/bookorbit-client/`. APK: `app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk`.
The debug application ID is `io.github.monkecode.bookorbitclient.debug`.
The source namespace stays `com.bookorbit` to minimize unrelated fork churn.

For a Google Cast-enabled build, use `:app:assembleFullDebug`. Both flavors
share an installation ID, but are separate from upstream/official installations.

0.9.4 uses AGP 9.5.2 built-in Kotlin and Gradle 9.9.2; Compose requires compile
SDK 37.2. Target SDK remains 36 and minimum SDK 26. The Dockerfile installs the
compile platform. Deprecated API warnings are not hidden; both lint variants pass.

The Python checks run production download migration/guarded SQL on an in-memory
SQLite database. Android Room migrations, real WorkManager interruption and SAF
permissions still require a device/emulator test.

## Isolated BookOrbit 3.9.0 test server

```sh
python tools/test-server/init.py
python tools/test-server/fixtures.py
docker compose -f tools/test-server/compose.yaml up -d --wait --wait-timeout 180
python tools/test-server/smoke.py
```

Only `127.0.0.1:6264` is exposed. The database has no published port.
The smoke check creates a local administrator and a library containing original
Two EPUB editions/two equal-sized MP3 tracks/comic fixtures. Generating MP3 uses the pinned
server image's encoder without network access. Its account is in `.local/test-account.json` (0600).
Do not commit `.local/`, import production data, or reuse these credentials elsewhere.

Re-running the smoke check is safe; library creation can already have started a scan.
It tests HTTP contracts, not Android UI, decoder support or background playback.

An Android emulator uses `http://10.0.2.2:6264`; for a USB device, forward the
loopback port using `adb reverse tcp:6264 tcp:6264` and use `http://127.0.0.1:6264`.
Do not expose this development server on the LAN just to test a device.

OIDC is a separate device test: the server must explicitly allow
`bookorbitclient://oauth2-callback` in `OIDC_MOBILE_REDIRECT_URIS` (the local
Compose file already does). Local password login does not require an OIDC provider.

Stop the test services without deleting their data:

```sh
docker compose -f tools/test-server/compose.yaml stop
```

## Isolated Android emulator (Linux/KVM)

Build the Android image once using the Gradle wrapper above, then:

```sh
docker build -f tools/android/emulator.Dockerfile -t bookorbit-client-emulator:local tools/android
mkdir -p .local/emulator-home
docker run --rm --init --name bookorbit-client-emulator --device /dev/kvm \
  --group-add "$(stat -c %g /dev/kvm)" --user "$(id -u):$(id -g)" \
  --network bookorbit-client-test_default --shm-size=1g \
  --mount "type=bind,source=$PWD,target=/workspace,readonly" \
  --mount "type=bind,source=$PWD/.local/emulator-home,target=/emulator-home" \
  bookorbit-client-emulator:local
```

The command runs until stopped; no emulator/ADB ports are published. In another
terminal, wait for Android boot, then install and inspect the app:

```sh
docker exec bookorbit-client-emulator adb wait-for-device
docker exec bookorbit-client-emulator adb shell getprop sys.boot_completed
docker exec bookorbit-client-emulator adb install -r /workspace/app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
python tools/android/device-ui.py launch
python tools/android/device-ui.py snapshot --name start
```

`server` fills the isolated app container's internal URL; `credentials` fills the
synthetic account without printing its password. Use these only on the respective
URL/login screens. `tap --text LABEL` uses current UI bounds; `back` goes back.
Snapshots go to ignored `.local/device-screenshots/` and may contain private UI
text. The harness targets only this named Docker emulator, not a connected phone.
Fixtures include two text editions, equal-sized audio, a 75-track audiobook and
a 20-second silent EPUB/SMIL. Smoke waits for scanning before checking metadata.
This headless emulator has no audio output; see `docs/DEVICE_TESTING.md` for limits.
