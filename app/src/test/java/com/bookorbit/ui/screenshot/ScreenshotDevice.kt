package com.bookorbit.ui.screenshot

/**
 * The window sizes every screen is recorded at: a phone, an unfolded foldable, and a landscape
 * tablet. Each sits in a different Material window size class (compact, medium, expanded).
 */
enum class ScreenshotDevice(private val widthDp: Int, private val heightDp: Int, private val density: String) {
    Phone(411, 891, "xxhdpi"),
    Foldable(673, 841, "xhdpi"),
    Tablet(1280, 800, "xhdpi");

    fun qualifiers(dark: Boolean) =
        "w${widthDp}dp-h${heightDp}dp-${if (widthDp > heightDp) "land" else "port"}-${if (dark) "night" else "notnight"}-$density"

    companion object {
        /**
         * Android 16, not the app's targetSdk 37: Espresso (under Compose's test rule) calls
         * `InputManager.getInstance()`, which Android 17 removed. Rendering is the same.
         * Screenshot tests also run with a plain `Application` (in their `@Config`): the real one
         * starts Hilt and the keystore, which rendering a composable doesn't need.
         */
        const val SDK = 36
    }
}

/** `src/test/screenshots/<name>/<device>-<light|dark>.png`. */
fun screenshotPath(name: String, device: ScreenshotDevice, dark: Boolean) =
    "src/test/screenshots/$name/${device.name.lowercase()}-${if (dark) "dark" else "light"}.png"
