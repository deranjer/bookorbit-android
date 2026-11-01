package com.bookorbit.feature.reader

import android.content.Context
import android.util.Base64
import android.util.Log
import com.bookorbit.core.model.ReaderFont
import com.bookorbit.core.network.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A family of uploaded font files, under the CSS name the web reader stores for it. */
data class UploadedFontFamily(val name: String, val cssFamily: String, val fromServer: Boolean, val files: List<ReaderFont>)

/** Fonts larger than this aren't inlined into the page (a CJK face can run to tens of MB). */
private const val MAX_INLINE_FONT_BYTES = 8_000_000L

/**
 * The fonts uploaded to the server: the user's own (`fonts`) and the shared ones (`server-fonts`).
 * A chosen family is fetched once into the cache and handed to the page as @font-face rules with
 * data: URLs, since the WebView never reaches the network itself.
 */
@Singleton
class ReaderFontsRepository @Inject constructor(
    private val api: ApiService,
    @ApplicationContext private val context: Context,
) {
    @Volatile private var cached: List<UploadedFontFamily>? = null

    suspend fun families(): List<UploadedFontFamily> {
        cached?.let { return it }
        val user = runCatching { api.getUserFonts() }.getOrDefault(emptyList())
        val server = runCatching { api.getServerFonts() }.getOrDefault(emptyList())
        fun group(fonts: List<ReaderFont>, fromServer: Boolean) = fonts.groupBy { it.familyName }.map { (name, files) ->
            UploadedFontFamily(name, cssFamilyName(name, fromServer), fromServer, files)
        }
        return (group(user, false) + group(server, true)).sortedBy { it.name.lowercase() }.also { cached = it }
    }

    /** @font-face rules for [cssFamily], or "" when it isn't an uploaded family (or can't be fetched). */
    suspend fun fontFaceCss(cssFamily: String?): String {
        if (cssFamily == null || !isUploadedFamily(cssFamily)) return ""
        val family = families().firstOrNull { it.cssFamily == cssFamily } ?: return ""
        return withContext(Dispatchers.IO) {
            family.files.mapNotNull { font ->
                val file = runCatching { download(font, family.fromServer) }
                    .onFailure { Log.w("ReaderFonts", "font ${font.id} unavailable", it) }
                    .getOrNull() ?: return@mapNotNull null
                if (file.length() > MAX_INLINE_FONT_BYTES) return@mapNotNull null
                val data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                val weight = if (font.weightMin != null && font.weightMax != null) "${font.weightMin} ${font.weightMax}" else "${font.weight}"
                "@font-face { font-family: \"$cssFamily\"; src: url(\"data:${mimeOf(font.format)};base64,$data\") format(\"${cssFormatOf(font.format)}\"); " +
                    "font-weight: $weight; font-style: ${font.style}; font-display: swap; }"
            }.joinToString("\n")
        }
    }

    private suspend fun download(font: ReaderFont, fromServer: Boolean): File {
        val dir = File(context.cacheDir, "reader-fonts").apply { mkdirs() }
        val target = File(dir, "${if (fromServer) "server" else "user"}-${font.id}.${font.format}")
        if (target.length() > 0) return target
        val body = api.getFontFile(if (fromServer) "server-fonts" else "fonts", font.id)
        val partial = File(dir, target.name + ".part")
        body.byteStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
        partial.renameTo(target)
        return target
    }

    companion object {
        private const val USER_PREFIX = "__userfont_"
        private const val SERVER_PREFIX = "__serverfont_"

        /** The web client's `fontCssFamilyGroupName`, so a family chosen on either side names the same font. */
        fun cssFamilyName(familyName: String, fromServer: Boolean): String {
            val safe = familyName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "font" }
            return (if (fromServer) SERVER_PREFIX else USER_PREFIX) + safe
        }

        fun isUploadedFamily(value: String?): Boolean =
            value != null && (value.startsWith(USER_PREFIX) || value.startsWith(SERVER_PREFIX))

        private fun cssFormatOf(format: String) = when (format) {
            "otf" -> "opentype"
            "woff" -> "woff"
            "woff2" -> "woff2"
            else -> "truetype"
        }

        private fun mimeOf(format: String) = when (format) {
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "font/ttf"
        }
    }
}
