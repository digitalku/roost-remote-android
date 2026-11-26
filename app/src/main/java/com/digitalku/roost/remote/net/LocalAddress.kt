package com.digitalku.roost.remote.net

import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/** The port a Roost window listens on unless it was told otherwise (RR-1). */
const val DEFAULT_PORT = 7387

/**
 * The addresses this app will talk to: the ones the server answers (RR-1).
 *
 * The transport is plain HTTP (RR-3), so a password typed into this app crosses
 * the network as it was typed. RR-3 asks for cleartext to be allowed for the
 * server's hosts only, and an Android network security configuration can only
 * name hosts known when the app is built. This is the same rule applied at run
 * time instead, to the address a connection actually reached, so it holds for a
 * name as much as for a number, and for every request: Coil's and Media3's go
 * through the same client.
 */
object LocalAddress {
    fun isLocal(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) {
            return true
        }
        val bytes = address.address
        return when (address) {
            // 100.64.0.0/10, where Tailscale numbers its devices.
            is Inet4Address -> unsigned(bytes[0]) == 100 && unsigned(bytes[1]) in 64..127
            // fc00::/7, unique-local, which is also where Tailscale's IPv6 lives.
            is Inet6Address -> (unsigned(bytes[0]) and 0xfe) == 0xfc
            else -> false
        }
    }

    private fun unsigned(byte: Byte): Int = byte.toInt() and 0xff
}

/** Refused before a byte of the request is written. */
class NotLocalException(address: InetAddress) :
    IOException("${address.hostAddress} is not on a local network")

/**
 * A network interceptor, so it runs once the socket is connected and before the
 * request is written: it checks the address that was reached, not the name that
 * was asked for.
 */
object LocalOnly : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val reached = chain.connection()?.route()?.socketAddress?.address
        if (reached != null && !LocalAddress.isLocal(reached)) throw NotLocalException(reached)
        return chain.proceed(chain.request())
    }
}

/**
 * What the owner typed, as the server's base URL, or `null` when it is not an
 * address at all.
 *
 * `192.168.1.20`, `192.168.1.20:7387`, `http://laptop.tail1234.ts.net` and an
 * IPv6 literal in brackets are all accepted. A port left out is 7387, not 80,
 * because that is the port the window prints. `https` is refused: version 1 of
 * the protocol has none (RR-3).
 */
fun parseServerAddress(typed: String): HttpUrl? {
    val trimmed = typed.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() != "http" || uri.host.isNullOrEmpty()) return null
    val url = withScheme.toHttpUrlOrNull() ?: return null
    val port = if (uri.port == -1) DEFAULT_PORT else uri.port
    return url.newBuilder().port(port).encodedPath("/").query(null).fragment(null).build()
}
