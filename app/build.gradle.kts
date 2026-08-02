import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.play.publisher)
}

// Release signing is loaded from keystore.properties (gitignored, see keystore.properties.example)
// rather than committed, so debug/lint/test builds and CI runs without secrets keep working.
val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("app/keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProperties.containsKey("storeFile")

// Play publishing (Gradle Play Publisher) is only configured when a service account credentials
// file is present. It's gitignored; CI writes it from the PLAY_SERVICE_ACCOUNT_JSON secret. Never
// present locally, so `publishBundle` et al. are simply unavailable outside that CI job -- every
// other task (build/lint/test) is unaffected either way.
val playServiceAccountFile = rootProject.file("app/play-service-account.json")
if (playServiceAccountFile.exists()) {
    play {
        serviceAccountCredentials.set(playServiceAccountFile)
        // Upload only -- promoting internal to production stays a manual step in Play Console.
        track.set("internal")
        defaultToAppBundles.set(true)
    }
}

android {
    namespace = "com.bookorbit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bookorbit"
        minSdk = 26
        targetSdk = 36
        // versionCode/versionName are bumped automatically by a companion step in
        // .github/workflows/release-please.yml (release-please's own generic-file updater is
        // unreliable for this -- see the commit that introduced this comment). Both must stay
        // plain literals so F-Droid's static manifest parser can find them too.
        versionCode = 300
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // OIDC redirect scheme used by the AppAuth intent-filter (see AndroidManifest).
        // Must match the server's whitelisted mobile redirect URI (bookorbit://oauth2-callback,
        // OIDC_MOBILE_REDIRECT_URIS default) and the value used by OidcManager.
        manifestPlaceholders["appAuthRedirectScheme"] = "bookorbit"
    }

    // "full" (default) links Google's proprietary Cast SDK for Chromecast support; "fdroid"
    // excludes it entirely -- see app/src/{full,fdroid}/java/com/bookorbit/feature/cast/. Both
    // flavors share the same applicationId; this isn't a fork, just a dependency toggle.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }

    // The "Dependency metadata" block AGP stamps into the signing block is only useful for Play
    // Console's own insights; it isn't needed for the GitHub/F-Droid channels, and its presence
    // trips F-Droid's binary "check apk" scanner (it looks like an unexpected extra signing
    // block). Disabled everywhere for one consistent binary across distribution channels.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = project.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / lifecycle / activity
    implementation(libs.androidx.core.ktx)
    // Theme.BookOrbit (themes.xml) needs an AppCompat parent theme. Previously this arrived only
    // transitively via androidx.mediarouter (full flavor only); made explicit and shared so the
    // fdroid flavor keeps a working base theme too.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Media + images
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.coil.compose)

    // Cast (Chromecast) -- proprietary, "full" flavor only. See the flavor comment in `android {}`
    // and app/src/{full,fdroid}/java/com/bookorbit/feature/cast/.
    "fullImplementation"(libs.androidx.media3.cast)
    "fullImplementation"(libs.play.services.cast.framework)
    "fullImplementation"(libs.androidx.mediarouter)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.documentfile)

    // Paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Background work + OIDC
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.appauth)

    // WebView (foliate reader host)
    implementation(libs.androidx.webkit)

    // Unit test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)

    // Instrumented test
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
}
