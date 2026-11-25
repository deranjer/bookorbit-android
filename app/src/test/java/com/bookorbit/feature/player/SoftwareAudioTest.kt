package com.bookorbit.feature.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.*
import org.junit.Test

class SoftwareAudioTest {
    @Test fun `decoder failure retries only supported audio on the platform renderer once`() {
        val code = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        assertTrue(shouldRetrySoftwareAudio(code, true, true, true, false))
        assertFalse(shouldRetrySoftwareAudio(code, true, true, true, true))
        assertFalse(shouldRetrySoftwareAudio(code, false, true, true, false))
        assertFalse(shouldRetrySoftwareAudio(code, true, false, true, false))
        assertFalse(shouldRetrySoftwareAudio(code, true, true, false, false))
    }

    @Test fun `HTTP network source and DRM failures never become codec retries`() {
        for (code in listOf(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED)) {
            assertFalse(shouldRetrySoftwareAudio(code, true, true, true, false))
        }
    }
}
