package com.digitalku.roost.remote.ui

import com.digitalku.roost.remote.net.NotLocalException
import com.digitalku.roost.remote.net.RemoteException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.ln
import kotlin.math.pow

/**
 * What to tell the owner about a failure, in a sentence.
 *
 * A problem's `code` decides (RR-8); its `message` is the engine's, written for
 * the window, and is shown only when there is no better sentence here.
 */
fun explain(error: Throwable, address: String): String = when (error) {
    is NotLocalException ->
        "Roost Remote only talks to computers on your own network or your Tailscale."
    is RemoteException -> when (error.code) {
        "password_wrong" -> "That is not this library's password."
        "too_many_attempts" -> "Too many wrong passwords. Wait a minute, then try again."
        "not_shared" -> "That computer is not sharing a library with a password right now."
        "not_paired" -> "This phone is no longer paired with the library."
        "not_allowed_remotely" -> "Roost does not allow that from a phone."
        else -> error.problem.message
    }
    is UnknownHostException -> "No computer called $address was found."
    is ConnectException, is NoRouteToHostException, is SocketTimeoutException ->
        "Nothing answered at $address. Is Share on this network… ticked in Roost on that computer?"
    is IOException -> error.message ?: "The connection failed."
    else -> error.message ?: error.toString()
}

/** 1.4 MB, as the window writes it. */
fun bytes(size: Long): String {
    if (size < 1024) return "$size B"
    val units = listOf("KB", "MB", "GB", "TB")
    val exponent = (ln(size.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = size / 1024.0.pow(exponent)
    return "%.1f %s".format(value, units[exponent - 1])
}

/** 1:05:09 or 4:07. */
fun duration(ms: Long): String {
    val seconds = ms / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
