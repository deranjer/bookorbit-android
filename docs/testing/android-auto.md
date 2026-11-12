# Android Auto acceptance checks

The app uses Media3's media library service. Android Auto supplies the browsing UI and
Gemini/Assistant supplies voice recognition. The app resolves recognized queries to audiobooks;
it does not record microphone audio.

## Automated checks

Run the normal CI checks for both distributions:

```sh
./gradlew :app:lintFullDebug :app:lintFdroidDebug \
  :app:testFullDebugUnitTest :app:testFdroidDebugUnitTest \
  :app:assembleFullDebug :app:assembleFdroidDebug
```

With a disposable emulator or test device connected:

```sh
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.bookorbit.feature.player.AutoMediaSessionTest,com.bookorbit.feature.player.AutoArtworkProviderTest
```

The media-session test connects to the real service without launching a phone activity. It checks
search results, the legacy `playFromSearch` path used by Android Auto, saved speed and position,
cross-file chapter selection, live download notifications, invalid-selection errors, and
empty-queue resumption. Fixtures and saved settings are restored after the test. Artwork tests
check downloaded-cover reads, write rejection, foreign-scope rejection, and provider configuration.

These tests exercise media integration, not voice recognition or a projected Android Auto UI.
The cold service test also guards Android 15's requirement to run a foreground service before
requesting audio focus. The playback wrapper promotes the service before play and Media3 then
replaces the initial notification with the normal media controls.

## Phone and head-unit checks still required

Use Android Auto's Desktop Head Unit or a physical head unit, with a signed-in phone and a server
containing downloaded, streamed, finished, multi-file, and duplicate-title audiobooks.

| Scenario | Expected result |
| --- | --- |
| Connect while the phone app is closed and the phone is locked | BookOrbit appears; Continue Listening is first; a book starts without opening the phone app. |
| Say “Play [exact title] on BookOrbit” | The matching audiobook starts at its saved position and speed. |
| Ask to resume BookOrbit without a title | The newest unfinished playable book resumes; finished books are skipped. |
| Search by title, author, or narrator | Only audiobooks appear; alternative editions remain selectable. Check both Gemini and Assistant where available. |
| Search for an absent title while online | A no-results message appears; current playback is retained. |
| Disconnect the server | Downloaded browsing/search/playback/art still work; streamed requests show a connection message. |
| Expire the login | Cached/downloaded content remains usable; remote requests either refresh credentials or request sign-in when safe. |
| Inspect browse and Now Playing covers | Downloaded and authenticated streamed covers display without credentials in the artwork URI. |
| Download/delete a book while connected | The Downloads shelf updates without reconnecting. |
| Browse more than 40 books or chapters | Range folders expose all entries; no screen depends on Auto pagination. |
| Change chapters, use steering-wheel buttons, seek, pause, and reconnect | Position and progress remain consistent across files and between phone and car. |
| Change global speed or select a book with its own speed | Each newly started book gets its appropriate setting. |
| Switch server/account | Old artwork URIs and cached search results do not expose the previous scope. |
| Receive a call or lose audio output | Existing audio-focus/noisy-output handling pauses and recovers appropriately. |

Record the phone OS, Android Auto version, voice provider, server version, and head-unit/DHU version
with any failures. Authenticated streaming artwork and real spoken-command recognition require
this manual verification before release.
