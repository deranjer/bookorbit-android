plugins { alias(libs.plugins.android.library) }

abstract class PrepareSoftwareAudioSources : Exec() {
    @get:OutputDirectory abstract val javaDirectory: DirectoryProperty
    @get:OutputDirectory abstract val assetDirectory: DirectoryProperty
}

val imported = layout.buildDirectory.dir("imported")
val prepareSources = tasks.register<PrepareSoftwareAudioSources>("prepareSoftwareAudioSources") {
    inputs.file("sources.lock.json")
    inputs.file(rootProject.file("tools/android/prepare-software-audio.py"))
    inputs.file(rootProject.file("gradle/libs.versions.toml"))
    outputs.dir(imported)
    javaDirectory.set(imported.map { it.dir("media3/src/main/java") })
    assetDirectory.set(imported.map { it.dir("assets") })
    commandLine("python3", rootProject.file("tools/android/prepare-software-audio.py"),
        "--output", imported.get().asFile)
}

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
        externalNativeBuild.cmake {
            arguments += "-DIMPORTED_SOURCES=${imported.get().asFile.absolutePath}"
            arguments += "-DFFMPEG_ANDROID_API=26"
        }
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    externalNativeBuild.cmake {
        path = file("CMakeLists.txt")
        version = "3.30.5"
    }
}

androidComponents.onVariants { variant ->
    variant.sources.java?.addGeneratedSourceDirectory(prepareSources, PrepareSoftwareAudioSources::javaDirectory)
    variant.sources.assets?.addGeneratedSourceDirectory(prepareSources, PrepareSoftwareAudioSources::assetDirectory)
}

tasks.configureEach {
    if (name == "preBuild" || name.startsWith("configureCMake") || name.startsWith("buildCMake")) {
        dependsOn(prepareSources)
    }
}

dependencies {
    api(libs.androidx.media3.exoplayer)
    implementation("androidx.annotation:annotation:1.10.0")
    compileOnly("org.checkerframework:checker-qual:3.43.0")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
}
