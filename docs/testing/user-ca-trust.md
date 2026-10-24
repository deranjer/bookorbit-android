# Test PR #86: user-installed CA trust

Use the debug app (`com.bookorbit.debug`), which is separate from the production app.
Normal HTTP and public-CA HTTPS servers should still work with the toggle off.

## Automated Android tests

Connect a device with USB debugging authorized, or start an emulator, then run:

```powershell
.\gradlew.bat :app:connectedFullDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.bookorbit.core.network.UserCaTrustTest'
```

These tests use real TLS, an injected test CA, and Android's certificate validator. They cover
default rejection, enabling trust on an existing client, revoking trust before a pooled request
sends HTTP headers, a redirect to another hostname, wrong hostnames, and certificate expiry. They do not install
a device CA: the manual check below verifies enumeration of Android's actual user store and UI.

## Local fixture over USB or paired wireless ADB

Python 3.10+ with `cryptography` is required (`python -m pip install cryptography` if missing).
Run from the repository root and keep the server running:

If more than one device/emulator is connected, add `-s <device-address-or-serial>` after every
`adb` below. These reverse mappings work over paired wireless ADB as well as USB.

```powershell
python tools/user_ca_test_server.py
adb reverse tcp:8443 tcp:8443
adb reverse tcp:8444 tcp:8444
adb reverse tcp:8445 tcp:8445
adb push build/user-ca-fixture/bookorbit-test-ca.crt /sdcard/Download/bookorbit-test-ca.crt
```

1. In Android Settings, find **Install a certificate → CA certificate**, typically under
   Security & privacy → More security settings → Encryption & credentials. Select the test
   certificate in Downloads. Android requires confirmation/device unlock.
2. In the **debug** app's server screen enter `https://localhost:8443`, leave **Trust
   user-installed certificates** off, then Connect. Expect a certificate-specific error.
3. Enable the toggle and Connect. Expect the login screen. Stop there: this is a TLS fixture,
   not a real BookOrbit server, and it does not support login.
4. Tap Change server. Reconnect to the same URL: the toggle should remain on.
5. Turn it off and Connect. Expect rejection without restarting the app. Turning it off
   blocks new requests; a response/stream already in progress can finish.
6. With trust on, test `https://localhost:8444` and `https://localhost:8445`.
   Expect rejection for the wrong server name and expired certificate respectively.
7. Try `https://127.0.0.1:8443`. Its toggle should start off: consent for localhost must not
   transfer to another hostname, even though the certificate is valid for both names.

The local servers bind only to the PC's loopback interface and are reached through ADB. The CA
expires in two days; use `--regenerate` and reinstall its certificate if testing later.

## Full private-CA server check

The fixture verifies setup and validation only. Before merging, test an actual private-CA
BookOrbit server: login, browse uncached covers, download, stream/seek audio, and Cast upstream
traffic (full flavor). Repeat with the fdroid build for login, covers, downloads and playback.
An OIDC browser has its own trust policy; the app does not change it. Redirect destinations
also need their own hostname consent; consent never propagates to another host automatically.

### Settings layout check

On a narrow phone, open Settings with a long server hostname. Confirm the certificate-trust
title and description wrap, the complete switch stays within the screen padding, and toggling
it still works. Repeat with a large system font size and in an RTL locale. Check the Wi-Fi-only
switch and download-location Change button too, since they share the same row layout.

## Cleanup

Remove **BookOrbit temporary test CA** from Android's user credentials and delete the downloaded
certificate. Stop Python with Ctrl+C, then remove only these USB mappings:

```powershell
adb reverse --remove tcp:8443
adb reverse --remove tcp:8444
adb reverse --remove tcp:8445
```

Uninstall the debug app when finished. The fixture private leaf keys stay under ignored `build/`;
the CA signing key is never written to disk. Do not commit or share the generated keys.
