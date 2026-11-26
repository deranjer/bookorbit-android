package com.bookorbit.feature.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.bookorbit.core.auth.SessionManager
import okhttp3.OkHttpClient

/** Reopening the same HTTP source after seek must read the current token, not a captured header. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun audioStreamingFactory(main: OkHttpClient, session: SessionManager): DataSource.Factory {
    val client = audioStreamClient(main)
    return DataSource.Factory {
        val calls = AudioStreamCalls(client, session, session.owner())
        val delegate = OkHttpDataSource.Factory(calls).createDataSource()
        object : DataSource by delegate {
            override fun open(dataSpec: DataSpec): Long {
                calls.requireOwner()
                val length = delegate.open(dataSpec)
                try { calls.requireOwner() } catch (failure: Exception) { delegate.close(); throw failure }
                return length
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                calls.requireOwner()
                return delegate.read(buffer, offset, length)
            }
        }
    }
}
