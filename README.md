# BookOrbit — Android (native)

## No longer maintained

This project will no longer be maintained. I am ending development because BookOrbit's server
has begun restricting selected API routes to its official applications through app attestation,
making continued support for an independently signed, open-source Android client uncertain.

The server [added attestation requirements to existing routes](https://github.com/bookorbit/bookorbit/commit/b44d902eca79c082f8378ae336b9cc89c6a7de4e),
and its [Android attestation policy](https://github.com/bookorbit/bookorbit/blob/v3.8.0/server/src/modules/app-attest/app-attest.policy.ts)
accepts only the official package and approved signing certificates. Some routes remain available
to other clients, and alternatives exist for the current widget and smart-scope restrictions;
this does not mean every backend request is blocked today. Future server compatibility, fixes,
and new features will no longer be maintained here.

For a maintained alternative, use the
[official BookOrbit Android application](https://play.google.com/store/apps/details?id=app.bookorbit.reader).
The official Android app is not open source and offers paid Premium features; see its
[purchase information](https://bookorbit.app/android/support/). As of November 11, 2026, the
[official announcement](https://github.com/bookorbit/bookorbit/issues/2001) describes a closed
Google Play beta with Premium free during testing. Follow that announcement for access instructions
and check the current app listing for availability and purchase terms.

The source and build instructions below remain available for reference and for anyone who wants
to fork the project.

Native Kotlin / Jetpack Compose client for a self-hosted
[BookOrbit](https://github.com/bookorbit/bookorbit) server. This is a **standalone Gradle project** —
open this repository's root directly in Android Studio.

> User-facing docs: **https://deranjer.github.io/bookorbit-android/** (source in [`website/`](website/)).
> Privacy policy: **https://deranjer.github.io/bookorbit-android/privacy.html**.
> Historical roadmap: see [`ROADMAP.md`](ROADMAP.md); development has ended.
> Also available via [F-Droid](https://deranjer.github.io/fdroid/repo) (self-hosted repo).

> **Existing features (unmaintained):** auth + OIDC, library browsing, search,
> smart scopes, collections, authors/series, book detail, the foliate.js ebook reader, the Media3
> audiobook player (with sleep timer and Chromecast), and downloads/offline. See
> [Architecture & conventions](#architecture--conventions) below, the
> [Cross-repo couplings](#cross-repo-couplings) section. [`ROADMAP.md`](ROADMAP.md) records historical plans.

## Prerequisites

| Tool               | Version                                | Notes                                                   |
| ------------------ | -------------------------------------- | ------------------------------------------------------- |
| **Android Studio** | Ladybug (2024.2) or newer              | Easiest path; bundles a compatible JDK + SDK manager.   |
| **JDK**            | 17+ (21 recommended)                   | Android Studio's bundled JBR works. CI uses Temurin 21. |
| **Android SDK**    | Platform **36**, Build-Tools 35+       | Installed via Android Studio's SDK Manager.             |
| **A device**       | Emulator (API 26+) or a physical phone | Min SDK is **26**; compile/target SDK is **36**.        |

Gradle itself does **not** need to be installed — the committed wrapper (`./gradlew`) downloads the
right version (8.11.1) on first run.

## First-time setup

1. **Install the SDK.** In Android Studio: _Settings → Languages & Frameworks → Android SDK_ → install
   **Android API 36** (SDK Platform) and a recent **Build-Tools**. Accept the SDK licenses.
2. **`local.properties`.** This machine-specific file points Gradle at your SDK and must **not** be
   committed (it's git-ignored). Android Studio creates it automatically on first open. To create it
   by hand, put one line in `android/local.properties` (use forward slashes, even on Windows, to
   avoid Java-properties escaping issues):

   ```properties
   sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
   ```

   On macOS/Linux it's typically `~/Library/Android/sdk` / `~/Android/Sdk`.

## Run it in Android Studio (recommended)

1. **File → Open** and select this repository's **root** folder.
2. Wait for the initial **Gradle sync** to finish (first time pulls dependencies).
3. Pick a device in the toolbar's device dropdown (or create one in **Device Manager**).
4. Click **Run ▶**. Studio builds, installs, and launches the app.

The debug variant installs as application id **`com.bookorbit.debug`** (the `.debug` suffix lets debug
and release builds coexist on one device).

## Run it from the command line

From the repository root:

```bash
./gradlew :app:installDebug      # build + install on the connected device/emulator
./gradlew :app:assembleDebug     # just build the APK -> app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest # JVM unit tests
./gradlew :app:lintDebug         # Android lint
```

Launch after installing:

```bash
adb shell monkey -p com.bookorbit.debug -c android.intent.category.LAUNCHER 1
```

(`adb` lives in `<sdk>/platform-tools/`.) To start an emulator headlessly:
`"$ANDROID_HOME/emulator/emulator" -avd <avd-name>`.

## Connecting to a BookOrbit server

On first launch the app asks for a server URL. To point at a **local dev server**
(`pnpm run db:up && pnpm dev` from the repo root, listening on `:3000`):

- **Emulator:** the host machine is reachable at `10.0.2.2`, so enter `http://10.0.2.2:3000`.
- **Physical device:** use your machine's LAN IP, e.g. `http://192.168.1.50:3000`.

Cleartext HTTP is permitted (self-hosted servers are often plain HTTP on a LAN); prefer HTTPS when
exposed. For **OIDC** login, the server must whitelist the mobile redirect URI
`bookorbit://oauth2-callback` (`OIDC_MOBILE_REDIRECT_URIS`, the default).

## Project layout

```
app/src/main/
    assets/reader/            # foliate.js + bridge.js + index.html (foliate copied verbatim from the web client's build)
    java/com/bookorbit/
      app/                    # Application (Hilt), MainActivity, nav graph (BookOrbitApp / AuthenticatedApp)
      core/
        auth/                 # SessionManager, SecureStorage (EncryptedSharedPreferences)
        network/              # Retrofit ApiService, interceptors, token refresh, ImageUrls
        model/                # hand-written @Serializable DTOs (mirror the server's API types)
        db/                   # Room: reader/audio progress + downloads
        di/                   # Hilt modules (network, database, player)
        paging/               # Paging 3 sources
      feature/
        auth/ library/ search/ scopes/ collections/ authors/ series/
        dashboard/ bookdetail/ main/
        reader/               # foliate WebView host + bridge + CFI progress
        player/               # Media3 service, MediaController manager, player UI
        downloads/            # WorkManager downloads + offline catalog
      ui/                     # shared Compose components + theme
```

## Architecture & conventions

- **Kotlin + Jetpack Compose** (Material 3), **MVVM** with `ViewModel` + `StateFlow`, **Hilt** DI,
  **Coroutines/Flow**. Networking is Retrofit/OkHttp + kotlinx.serialization; images via Coil;
  audiobooks via Media3/ExoPlayer; persistence via Room + DataStore; downloads via WorkManager.
- **No emoji as icons** — use `androidx.compose.material.icons` (Material Symbols), matching the repo
  convention.
- **API models are hand-written** Kotlin `@Serializable` classes (there is no OpenAPI codegen), so
  server DTO changes must be mirrored here by hand. See [Cross-repo couplings](#cross-repo-couplings).
- **Reader theming is a three-way sync.** The foliate assets (`assets/reader/`) are a verbatim copy of
  the web client's build; when the web foliate or its `bridge.js` `THEMES`/`generateCSS` changes,
  re-copy into `assets/reader/` and keep `feature/reader/ReaderThemes.kt` aligned. See
  [Cross-repo couplings](#cross-repo-couplings) for the full rule.

## Testing

```bash
./gradlew :app:testDebugUnitTest    # unit tests (query builder, playback math, serialization)
```

CI (`.github/workflows/android-build.yml`) runs lint + unit tests + `assembleDebug` on every PR and
on pushes to `main`.

## Cross-repo couplings

This app is developed independently of the [BookOrbit server](https://github.com/bookorbit/bookorbit),
but two things must be kept in sync **by hand** when the server/web client changes — there is no build
dependency or codegen that enforces them:

1. **API DTOs.** The models in `app/src/main/java/com/bookorbit/core/model/` are hand-written
   `@Serializable` mirrors of the server's `/api/v1` response types. When a server endpoint's shape
   changes, update the matching Kotlin model here. `BaseUrlInterceptor` rewrites every request onto the
   configured server + `/api/v1`.
2. **Foliate reader assets (three-way sync).** `app/src/main/assets/reader/` is a verbatim copy of the
   web client's foliate.js build (`client/public/assets/foliate/` in the server repo) plus `bridge.js`,
   `index.html`. When the web client updates foliate or the `bridge.js` `THEMES`/`generateCSS` block,
   re-copy those files here and keep the settings-UI swatches in
   `app/src/main/java/com/bookorbit/feature/reader/ReaderThemes.kt` aligned with the same theme
   definitions. This preserves byte-identical EPUB CFI sync with the web client and Kobo.
