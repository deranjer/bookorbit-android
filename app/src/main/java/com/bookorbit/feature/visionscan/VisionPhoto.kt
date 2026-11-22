package com.bookorbit.feature.visionscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Bounded local decoding; re-encoding deliberately removes GPS, EXIF and other photo metadata. */
object VisionPhoto {
    suspend fun prepare(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 10 * 1024 * 1024) { "Photo exceeds local size limit" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Photo is unavailable")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000)
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize.coerceAtLeast(1) > 1600) {
            options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
        }
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, options) ?: error("Photo could not be decoded")
        var rotated: Bitmap? = null
        try {
            val orientation = runCatching { ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(270f); postScale(-1f, 1f) }
                    8 -> setRotate(270f)
                }
            }
            val image = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            rotated = image
            ByteArrayOutputStream().use { output ->
                check(image.compress(Bitmap.CompressFormat.JPEG, 85, output))
                require(output.size() <= 6 * 1024 * 1024)
                output.toByteArray()
            }
        } finally { if (rotated !== bitmap) rotated?.recycle(); bitmap.recycle() }
    }
}
