package com.bookorbit.core.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Android 17 blocks traffic to the local network for apps targeting API 37 unless
 * `ACCESS_LOCAL_NETWORK` (Nearby devices group) is granted, and a blocked TCP connect simply times
 * out. A self-hosted server is often on that network, and Cast discovery always is, so the app asks
 * before talking to one. "Local" follows the platform's definition: RFC 1918, CGNAT, link-local and
 * IPv6 unique-local / link-local addresses, plus mDNS `.local` names.
 */
object LocalNetworkAccess {
    const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

    // Android 17 (API 37); the permission doesn't exist below it, and nothing is blocked there.
    private const val FIRST_ENFORCING_SDK = 37

    // DNS for the startup check must not hold the app on its loading screen for long.
    private const val RESOLVE_TIMEOUT_MS = 3_000L

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < FIRST_ENFORCING_SDK ||
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** True when the permission is missing and [serverUrl] points at the local network. */
    suspend fun needsPermission(context: Context, serverUrl: String): Boolean =
        !isGranted(context) && isLocalServer(serverUrl)

    /**
     * Whether [serverUrl]'s host is on the local network. A host that doesn't resolve counts as not
     * local, so the normal connection error shows instead of a permission prompt.
     */
    suspend fun isLocalServer(serverUrl: String): Boolean {
        val withScheme = if ("://" in serverUrl) serverUrl else "https://$serverUrl"
        val host = withScheme.toHttpUrlOrNull()?.host ?: return false
        if (host.endsWith(".local", ignoreCase = true)) return true
        val addresses = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { runCatching { InetAddress.getAllByName(host) }.getOrNull() }
        } ?: return false
        return addresses.any(::isLocalAddress)
    }

    internal fun isLocalAddress(address: InetAddress): Boolean {
        val bytes = address.address.map { it.toInt() and 0xff }
        return when (address) {
            is Inet4Address -> bytes[0] == 10 ||
                (bytes[0] == 172 && bytes[1] in 16..31) ||
                (bytes[0] == 192 && bytes[1] == 168) ||
                (bytes[0] == 169 && bytes[1] == 254) ||
                (bytes[0] == 100 && bytes[1] in 64..127)
            is Inet6Address -> address.isLinkLocalAddress || (bytes[0] and 0xfe) == 0xfc
            else -> false
        }
    }
}
