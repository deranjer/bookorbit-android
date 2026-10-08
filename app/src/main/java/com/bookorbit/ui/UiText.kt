package com.bookorbit.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text that a view model can produce without a [Context]: either a string resource (with arguments)
 * or text that is already final, such as a message the server sent. The screen resolves it for display,
 * so view models stay free of Android resources, and the resolved language follows the device locale.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText

    /** Several pieces joined end to end; each is translated on its own, so word order stays natural. */
    data class Composite(val parts: List<UiText>) : UiText

    /** Resolves to a string. Arguments that are themselves [UiText] are resolved first. */
    fun resolve(context: Context): String = when (this) {
        is Res -> context.getString(id, *args.map { resolveArg(it, context) }.toTypedArray())
        is Plural -> context.resources.getQuantityString(id, count, *args.map { resolveArg(it, context) }.toTypedArray())
        is Raw -> value
        is Composite -> parts.joinToString("") { it.resolve(context) }
    }

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = Plural(id, count, args.toList())
        fun raw(value: String): UiText = Raw(value)
    }
}

private fun resolveArg(arg: Any, context: Context): Any = if (arg is UiText) arg.resolve(context) else arg

/** Resolve a [UiText] inside a composable, recomposing if the configuration (language) changes. */
@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
