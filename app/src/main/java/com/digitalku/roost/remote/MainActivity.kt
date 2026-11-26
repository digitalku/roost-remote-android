package com.digitalku.roost.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.digitalku.roost.remote.store.SavedLibrary
import com.digitalku.roost.remote.ui.LibrariesScreen
import com.digitalku.roost.remote.ui.LibraryModel
import com.digitalku.roost.remote.ui.LibraryScreen
import com.digitalku.roost.remote.ui.PairScreen
import com.digitalku.roost.remote.ui.RoostTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RoostRemote
        setContent { RoostTheme { App(app) } }
    }
}

/** Where the app is: the list, pairing, or one library. */
private sealed interface Place {
    data object List : Place

    /** [address] is filled when an address is known: a new one, or one unpaired. */
    data class Pair(val address: String?) : Place

    data class Open(val library: SavedLibrary, val token: String) : Place
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
        )
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
