package com.digitalku.roost.remote.upload

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** What a picked or shared file says about itself. */
data class Picked(val name: String, val size: Long, val modified: Long?)

/**
 * The name, the size and the date of a picked or shared file, from whatever
 * columns its provider answers. `null` when the size cannot be had: the server
 * wants the length before the bytes (RR-15).
 *
 * The date is the one a person would call the file's: when a photograph was
 * taken where the media store knows it, the file's modification time otherwise.
 */
fun describe(resolver: ContentResolver, uri: Uri): Picked? {
    val cursor = resolver.query(uri, null, null, null, null) ?: return null
    return cursor.use {
        if (!it.moveToFirst()) return null
        val name = it.text(OpenableColumns.DISPLAY_NAME)?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "file"
        val size = it.number(OpenableColumns.SIZE)
            ?: resolver.openAssetFileDescriptor(uri, "r")?.use { fd -> fd.length.takeIf { n -> n >= 0 } }
            ?: return null
        val modified = it.number(MediaStore.MediaColumns.DATE_TAKEN)?.takeIf { ms -> ms > 0 }
            ?: it.number(DocumentsContract.Document.COLUMN_LAST_MODIFIED)?.takeIf { ms -> ms > 0 }
            ?: it.number(MediaStore.MediaColumns.DATE_MODIFIED)?.takeIf { s -> s > 0 }?.times(1000)
        Picked(cleanName(name), size, modified)
    }
}

/** RR-15 refuses a separator, NUL, `.` and `..`; a provider's name could hold one. */
internal fun cleanName(name: String): String {
    val clean = name.replace('/', '_').replace('\\', '_').replace("\u0000", "").trim()
    return if (clean.isEmpty() || clean == "." || clean == "..") "file" else clean.takeLast(200)
}

private fun Cursor.text(column: String): String? {
    val index = getColumnIndex(column)
    return if (index < 0 || isNull(index)) null else getString(index)
}

private fun Cursor.number(column: String): Long? {
    val index = getColumnIndex(column)
    return if (index < 0 || isNull(index)) null else getLong(index)
}

/**
 * A file's bytes as a request body, read as they are sent and never held, with
 * [onSent] told how far it has got. It can be written again, for a retry.
 */
class ContentBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val length: Long,
    private val onSent: (Long) -> Unit,
) : RequestBody() {
    override fun contentType() = OCTET_STREAM

    override fun contentLength() = length

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: error("the file could not be opened")
        input.source().use { source ->
            var sent = 0L
            while (true) {
                val read = source.read(sink.buffer, CHUNK)
                if (read == -1L) break
                sink.emit()
                sent += read
                onSent(sent)
            }
        }
    }

    private companion object {
        val OCTET_STREAM = "application/octet-stream".toMediaType()
        const val CHUNK = 64L * 1024
    }
}
