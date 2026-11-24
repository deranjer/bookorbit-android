package com.bookorbit.feature.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComicOcrTest {
    @Test fun blankPageAndSimpleArtworkDoNotBecomeSelectableText() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val ocr = ComicOcr(context)
            assertTrue(ocr.recognize(bitmap).words.isEmpty())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 12f }
            canvas.drawCircle(400f, 350f, 180f, paint)
            canvas.drawLine(50f, 720f, 740f, 510f, paint)
            assertTrue(ocr.recognize(bitmap).words.isEmpty())
        } finally { bitmap.recycle() }
    }
    @Test fun bundledModelsRecognizeSyntheticTextAndProduceSelectableCoordinatesOffline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(1200, 500, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 72f }
            canvas.drawText("Hello world!", 80f, 150f, paint)
            canvas.drawText("Read this sentence.", 80f, 290f, paint)
            val result = ComicOcr(context).recognize(bitmap)
            val text = result.words.joinToString(" ") { it.text }
            assertTrue("Synthetic OCR should recognize Hello: $text", text.contains("Hello", ignoreCase = true))
            assertTrue("Synthetic OCR should recognize world: $text", text.contains("world", ignoreCase = true))
            assertEquals(1200, result.width)
            assertEquals(500, result.height)
            assertTrue(result.words.all { it.left >= 0 && it.right <= result.width && it.top >= 0 && it.bottom <= result.height && it.left < it.right })
        } finally { bitmap.recycle() }
    }
}
