package com.bookorbit.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookAssetsTest {

    private fun file(id: Int, format: String = "mp3", size: Long? = null) =
        BookFileRef(id = id, format = format, role = "primary", sizeBytes = size)

    private fun asset(id: String, sequence: Int, format: String = "mp3", size: Long? = null) =
        AudiobookManifestAsset(assetId = id, sequence = sequence, format = format, sizeBytes = size)

    @Test
    fun `single m4b maps to its asset`() {
        val result = AudiobookAssets.match(listOf(file(614, "m4b", 1000)), listOf(asset("aud_a", 0, "M4B", 1000)))
        assertEquals(mapOf(614 to "aud_a"), result)
    }

    @Test
    fun `matches by size even when server order differs from book-detail order`() {
        val files = listOf(file(1, size = 300), file(2, size = 100), file(3, size = 200))
        val assets = listOf(asset("aud_x", 0, size = 100), asset("aud_y", 1, size = 200), asset("aud_z", 2, size = 300))
        assertEquals(mapOf(1 to "aud_z", 2 to "aud_x", 3 to "aud_y"), AudiobookAssets.match(files, assets))
    }

    @Test
    fun `duplicate sizes and unknown sizes fall back to sequence order`() {
        val files = listOf(file(1, size = 100), file(2, size = 100), file(3), file(4, size = 500))
        val assets = listOf(
            asset("aud_c", 2),
            asset("aud_a", 0, size = 100),
            asset("aud_d", 3, size = 500),
            asset("aud_b", 1, size = 100),
        )
        assertEquals(
            mapOf(4 to "aud_d", 1 to "aud_a", 2 to "aud_b", 3 to "aud_c"),
            AudiobookAssets.match(files, assets),
        )
    }

    @Test
    fun `extra files without assets are left unmapped`() {
        val result = AudiobookAssets.match(listOf(file(1, size = 10), file(2, size = 20)), listOf(asset("aud_a", 0, size = 20)))
        assertEquals(mapOf(2 to "aud_a"), result)
    }

    @Test
    fun `manifest decodes ignoring fields the app doesn't use`() {
        val json = Json { ignoreUnknownKeys = true }
        val manifest = json.decodeFromString<AudiobookManifest>(
            """
            {"schema":"bookorbit.audiobook-manifest","schemaVersion":2,"revision":"r",
             "book":{"id":7,"title":"T","authors":[],"narrators":[],"hasCover":true},
             "assets":[{"assetId":"aud_1","sequence":0,"format":"m4b","durationMs":null,"sizeBytes":42,"etag":"e"}],
             "chapters":[],"totalDurationMs":0}
            """.trimIndent(),
        )
        assertEquals(listOf(AudiobookManifestAsset("aud_1", 0, "m4b", null, 42)), manifest.assets)
    }
}
