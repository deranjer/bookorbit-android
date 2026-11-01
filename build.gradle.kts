// Top-level build file. Plugins are declared here with `apply false` and applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied anywhere (AGP 9 has built-in Kotlin); declared so this Kotlin version is the one AGP uses.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.play.publisher) apply false
    alias(libs.plugins.roborazzi) apply false
}
