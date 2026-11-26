package com.digitalku.roost.remote.upload

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.digitalku.roost.remote.R
import com.digitalku.roost.remote.RoostRemote
import com.digitalku.roost.remote.net.RemoteException
import com.digitalku.roost.remote.net.Session
import com.digitalku.roost.remote.store.SavedLibrary
import com.digitalku.roost.remote.ui.explain
import com.digitalku.roost.remote.ui.hostOf
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Sending files to a library (ADR-0073), one at a time through WorkManager, so
 * that a video goes on when the screen locks or the app is closed. A batch is a
 * piece of work of its own, queued behind the library's earlier ones.
 */
object Uploads {
    private const val CHANNEL = "uploads"

    /** WorkManager's `Data` holds 10 KB: a batch of addresses stays well under it. */
    private const val BATCH = 40

    internal const val LIBRARY = "library"
    internal const val FOLDER = "folder"
    internal const val URIS = "uris"

    // The answer's keys are never the question's: WorkManager hands a batch's
    // answer to the next batch in the queue as input, and a key both share is
    // overwritten, which once sent a whole queue to the first batch's folder.
    const val FILED_IN = "filedIn"
    const val SENT = "sent"
    const val DUPLICATES = "duplicates"
    const val FAILED = "failed"
    const val PROBLEM = "problem"
    const val INDEX = "index"
    const val COUNT = "count"
    const val NAME = "name"
    const val BYTES_SENT = "bytesSent"
    const val BYTES_TOTAL = "bytesTotal"

    /** The queue of one library's batches. */
    fun queue(libraryId: String) = "upload-$libraryId"

    fun channel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Sending files", NotificationManager.IMPORTANCE_LOW)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Queues [uris] for [library], into [folder] or none. The read grant on each
     * is made to last where its provider allows it, since the work may run after
     * the screen that was given it has gone.
     */
    fun send(context: Context, library: SavedLibrary, folder: Long?, uris: List<Uri>) {
        if (uris.isEmpty()) return
        for (uri in uris) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val requests = uris.chunked(BATCH).map { batch ->
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(
                    workDataOf(
                        LIBRARY to library.libraryId,
                        FOLDER to (folder ?: 0L),
                        URIS to batch.map(Uri::toString).toTypedArray(),
                    ),
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        }
        WorkManager.getInstance(context)
            .beginUniqueWork(queue(library.libraryId), ExistingWorkPolicy.APPEND_OR_REPLACE, requests)
            .enqueue()
    }

    internal fun builder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_upload)
            .setOnlyAlertOnce(true)
}

/** One batch: each file sent, retried from the start three times if the connection drops. */
class UploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val app = context.applicationContext as RoostRemote
    private var told = 0L

    override suspend fun doWork(): Result {
        val libraryId = inputData.getString(Uploads.LIBRARY) ?: return Result.failure()
        val folder = inputData.getLong(Uploads.FOLDER, 0L).takeIf { it > 0 }
        val uris = inputData.getStringArray(Uploads.URIS).orEmpty().map(Uri::parse)
        val library = app.libraries.all.value.find { it.libraryId == libraryId }
        val token = library?.let(app.libraries::token)
        if (library == null || token == null) {
            return Result.failure(workDataOf(Uploads.PROBLEM to "This phone is no longer paired."))
        }
        val session = Session(library.address.toHttpUrl(), token, app.http)

        var added = 0
        var failed = 0
        val duplicates = mutableListOf<String>()
        var problem: String? = null

        setForeground(foreground(library.name, 0, uris.size, null, 0, 0))
        for ((index, uri) in uris.withIndex()) {
            // A shared file was opened when it was shared; a picked one is read now.
            val held = Held.take(uri)
            val picked = held?.picked ?: runCatching { describe(applicationContext.contentResolver, uri) }.getOrNull()
            if (picked == null) {
                failed++
                problem = "A file could not be read on this phone."
                continue
            }
            val sent = { bytes: Long -> progress(library.name, index, uris.size, picked.name, bytes, picked.size) }
            try {
                val received = withRetries {
                    session.upload(
                        picked.name,
                        folder,
                        picked.modified,
                        if (held != null) {
                            HeldBody(held, sent)
                        } else {
                            ContentBody(applicationContext.contentResolver, uri, picked.size, sent)
                        },
                    )
                }
                if (received.id != null) added++ else received.duplicateOf?.let(duplicates::add)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RemoteException) {
                failed++
                problem = e.problem.message
                // RR-7: nothing after this would be let in either.
                if (e.status == 401) {
                    app.libraries.forget(libraryId)
                    failed += uris.size - index - 1
                    break
                }
            } catch (e: Exception) {
                failed++
                problem = explain(e, hostOf(library.address))
            } finally {
                held?.descriptor?.close()
            }
        }

        val result = workDataOf(
            Uploads.SENT to added,
            Uploads.DUPLICATES to duplicates.toTypedArray(),
            Uploads.FAILED to failed,
            Uploads.PROBLEM to problem,
            Uploads.FILED_IN to (folder ?: 0L),
        )
        summarize(library.name, added, duplicates.size, failed, problem)
        return Result.success(result)
    }

    /** RR-16's lost answer: a retry may meet its own file as a duplicate, which is fine. */
    private suspend fun <T> withRetries(send: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return send()
            } catch (e: RemoteException) {
                throw e
            } catch (e: IOException) {
                if (++attempt >= RETRIES || isStopped) throw e
                delay(2_000L * attempt)
            }
        }
    }

    private fun progress(library: String, index: Int, count: Int, name: String, sent: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (now - told < 500 && sent < total) return
        told = now
        val data = Data.Builder()
            .putInt(Uploads.INDEX, index)
            .putInt(Uploads.COUNT, count)
            .putString(Uploads.NAME, name)
            .putLong(Uploads.BYTES_SENT, sent)
            .putLong(Uploads.BYTES_TOTAL, total)
            .build()
        setProgressAsync(data)
        setForegroundAsync(foreground(library, index, count, name, sent, total))
    }

    private fun foreground(library: String, index: Int, count: Int, name: String?, sent: Long, total: Long): ForegroundInfo {
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val percent = if (total > 0) ((sent * 100) / total).toInt() else 0
        val notification = Uploads.builder(applicationContext)
            .setContentTitle(if (count == 1) "Sending to $library" else "Sending ${index + 1} of $count to $library")
            .setContentText(name ?: "Starting…")
            .setProgress(100, percent, name == null)
            .setOngoing(true)
            .addAction(0, "Cancel", cancel)
            .build()
        return ForegroundInfo(NOTIFICATION_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun summarize(library: String, sent: Int, duplicates: Int, failed: Int, problem: String?) {
        val parts = buildList {
            if (sent > 0) add(if (sent == 1) "1 file sent" else "$sent files sent")
            if (duplicates > 0) add(if (duplicates == 1) "1 was already there" else "$duplicates were already there")
            if (failed > 0) add(if (failed == 1) "1 could not be sent" else "$failed could not be sent")
        }
        val notification = Uploads.builder(applicationContext)
            .setContentTitle(library)
            .setContentText(parts.joinToString(", ").replaceFirstChar(Char::uppercase))
            .apply { if (failed > 0 && problem != null) setStyle(NotificationCompat.BigTextStyle().bigText("${parts.joinToString(", ")}. $problem")) }
            .setAutoCancel(true)
            .build()
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification)
    }

    private companion object {
        const val RETRIES = 3
        const val NOTIFICATION_PROGRESS = 7387
    }
}
