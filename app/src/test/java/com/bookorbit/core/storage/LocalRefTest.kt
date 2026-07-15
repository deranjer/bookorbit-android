package com.bookorbit.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Only [LocalRef.parse]'s legacy bare-path branch is exercisable here: the content:// and file://
 * branches go through android.net.Uri.parse, which (like the rest of this project's DataStore-
 * and ContentResolver-backed code) has no local-unit-test mocking infra (no Robolectric, no
 * returnDefaultValues) and throws unmocked - verified manually per the plan's verification section.
 */
class LocalRefTest {

    @Test
    fun `parse treats a legacy bare path as PlainFile`() {
        val path = "/data/user/0/com.bookorbit/files/downloads/1/2.mp3"
        val ref = LocalRef.parse(path)
        assertTrue(ref is LocalRef.PlainFile)
        assertEquals(File(path), (ref as LocalRef.PlainFile).file)
    }
}
