package com.bookorbit.ui.components

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat

/**
 * Converts the light HTML found in server-provided descriptions (`<br />`, `<p>`, `<i>`, `<b>`) into an
 * [AnnotatedString]. Metadata providers often return markup, which would otherwise render literally.
 */
fun htmlToAnnotatedString(html: String): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    // Trim the end only: span offsets index into the untrimmed string, so trimming the start would shift them.
    val text = spanned.toString().trimEnd()
    return buildAnnotatedString {
        append(text)
        if (spanned is Spanned) {
            for (span in spanned.getSpans(0, spanned.length, Any::class.java)) {
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)
                if (start < 0 || end > text.length || start >= end) continue
                when (span) {
                    is StyleSpan -> when (span.style) {
                        Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                        Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                        Typeface.BOLD_ITALIC -> addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                            start,
                            end,
                        )
                    }
                    is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                }
            }
        }
    }
}
