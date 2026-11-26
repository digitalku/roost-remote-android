package com.digitalku.roost.remote.upload

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/**
 * Files another application shared, opened while the grant to read them lasts.
 *
 * A share's grant belongs to the screen it was handed to, and that screen closes
 * at once, before the work has read a byte: the files were unreadable by then.
 * So the screen opens each one and leaves it here, in this process, for the
 * work to take. A process that dies first loses them, and the work says so.
 */
object Held {
    class File(val picked: Picked, val descriptor: ParcelFileDescriptor)

    private val files = ConcurrentHashMap<String, File>()

    /** Opens [uris] now, while they can be read. Answers the ones that could be. */
    fun hold(resolver: ContentResolver, uris: List<Uri>): List<Uri> = uris.filter { uri ->
        runCatching {
            val picked = describe(resolver, uri) ?: run {
                Log.w("RoostRemote", "a shared file says nothing of its size")
                return@runCatching false
            }
            val descriptor = resolver.openFileDescriptor(uri, "r") ?: return@runCatching false
            files.put(uri.toString(), File(picked, descriptor))?.descriptor?.close()
            true
        }.getOrElse { error ->
            Log.w("RoostRemote", "a shared file could not be opened: ${error.javaClass.simpleName}")
            false
        }
    }

    /** The open file for [uri], handed over once: the taker closes it. */
    fun take(uri: Uri): File? = files.remove(uri.toString())
}

/**
 * A held file's bytes, from the start each time it is written, so a retry
 * sends the whole file again. The descriptor is a file's, which can seek.
 */
class HeldBody(
    private val file: Held.File,
    private val onSent: (Long) -> Unit,
) : RequestBody() {
    override fun contentType() = "application/octet-stream".toMediaType()

    override fun contentLength() = file.picked.size

    override fun writeTo(sink: BufferedSink) {
        val input = FileInputStream(file.descriptor.fileDescriptor)
        input.channel.position(0)
        // Not closed with `use`: closing the stream would close the descriptor,
        // which a retry still needs. The worker closes it when it is done.
        val source = input.source()
        var sent = 0L
        while (true) {
            val read = source.read(sink.buffer, 64L * 1024)
            if (read == -1L) break
            sink.emit()
            sent += read
            onSent(sent)
        }
    }
}
