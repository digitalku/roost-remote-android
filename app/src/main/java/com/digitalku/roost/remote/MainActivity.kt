package com.digitalku.roost.remote

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.digitalku.roost.remote.store.SavedLibrary
import com.digitalku.roost.remote.ui.LibrariesScreen
import com.digitalku.roost.remote.ui.LibraryModel
import com.digitalku.roost.remote.ui.LibraryScreen
import com.digitalku.roost.remote.ui.PairScreen
import com.digitalku.roost.remote.ui.RoostTheme
import com.digitalku.roost.remote.ui.SettingsScreen
import com.digitalku.roost.remote.ui.isDark
import com.digitalku.roost.remote.ui.hostOf
import com.digitalku.roost.remote.upload.Held
import com.digitalku.roost.remote.upload.Uploads

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as RoostRemote
        val shared = shared(intent)
        setContent {
            val theme by app.settings.theme.collectAsState()
            val dark = isDark(theme)
            // The bars' icons follow the app's theme, not the system's: a light app
            // on a dark system would otherwise draw white icons on white.
            DisposableEffect(dark) {
                val bars = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            RoostTheme(dark) {
                App(app)
                if (shared.isNotEmpty()) ShareTo(app, shared, onDone = ::finish)
            }
        }
    }
}

/** The files another application shared (ADR-0073 §3.8), or none. */
private fun shared(intent: Intent?): List<Uri> = when (intent?.action) {
    Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
    Intent.ACTION_SEND_MULTIPLE ->
        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    else -> emptyList()
}

/**
 * Which library the shared files go to. They go into no folder: nothing here
 * says which one was meant. The work outlives this screen, which closes back
 * to the application that shared them.
 */
@Composable
private fun ShareTo(app: RoostRemote, uris: List<Uri>, onDone: () -> Unit) {
    val context = LocalContext.current
    val libraries by app.libraries.all.collectAsState()
    var open by remember { mutableStateOf(true) }
    if (!open) return
    val what = if (uris.size == 1) "1 file" else "${uris.size} files"
    if (libraries.isEmpty()) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("No library yet") },
            text = { Text("Pair this phone with a library first, then share the $what again.") },
            confirmButton = { TextButton(onClick = { open = false }) { Text("OK") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Send $what to") },
        text = {
            LazyColumn {
                items(libraries, key = { it.libraryId }) { library ->
                    Text(
                        library.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Opened now, while this screen holds the grant
                                // to read them; the work takes them from here.
                                val held = Held.hold(context.contentResolver, uris)
                                Uploads.send(context, library, null, held)
                                val said = if (held.size == uris.size) {
                                    "Sending $what to ${library.name}"
                                } else {
                                    "${uris.size - held.size} could not be read; sending ${held.size} to ${library.name}"
                                }
                                Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
                                onDone()
                            }
                            .padding(vertical = 12.dp),
                    )
                    Text(
                        hostOf(library.address),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/** Where the app is: the list, pairing, or one library. */
private sealed interface Place {
    data object List : Place

    /** [address] is filled when an address is known: a new one, or one unpaired. */
    data class Pair(val address: String?) : Place

    data class Open(val library: SavedLibrary, val token: String) : Place

    data object Settings : Place
}

@Composable
private fun App(app: RoostRemote) {
    var place by remember { mutableStateOf<Place>(Place.List) }

    val open = { library: SavedLibrary ->
        val token = app.libraries.token(library)
        place = if (token == null) Place.Pair(library.address) else Place.Open(library, token)
    }

    when (val now = place) {
        Place.List -> LibrariesScreen(
            libraries = app.libraries,
            onOpen = open,
            onAdd = { place = Place.Pair(null) },
            onChangeAddress = { place = Place.Pair(it.address) },
            onSettings = { place = Place.Settings },
        )
        Place.Settings -> {
            BackHandler { place = Place.List }
            SettingsScreen(app.settings, onBack = { place = Place.List })
        }
        is Place.Pair -> {
            BackHandler { place = Place.List }
            PairScreen(
                app = app,
                address = now.address,
                onBack = { place = Place.List },
                onPaired = open,
            )
        }
        is Place.Open -> {
            // A plain object rather than a ViewModel: it holds an open event
            // stream, and leaving the library must close it, not keep it for a
            // return that may never come. Rotation does not recreate the
            // activity (the manifest's configChanges), so nothing is lost.
            val model = remember(now.library.libraryId) { LibraryModel(app, now.library, now.token) }
            DisposableEffect(model) { onDispose { model.close() } }
            BackHandler { place = Place.List }
            LibraryScreen(
                model = model,
                onBack = { place = Place.List },
                onPairAgain = { place = Place.Pair(now.library.address) },
            )
        }
    }
}
