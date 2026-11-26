package com.digitalku.roost.remote.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import com.digitalku.roost.remote.net.wire

/**
 * A library this phone is paired with, remembered per library (RR-1).
 *
 * Keyed by the library's id rather than the address, because an address is the
 * part that changes: a laptop given another number by the router is the same
 * library, and the owner edits the address rather than pairing again.
 */
@Serializable
data class SavedLibrary(
    val libraryId: String,
    val name: String,
    /** The base URL, `http://192.168.1.20:7387/`. */
    val address: String,
    val deviceId: String,
    /** The device token, sealed by [Vault]. Never the password (RR-6). */
    val sealedToken: String,
)

/** The paired libraries, in the app's private preferences. */
class Libraries(context: Context, private val vault: Vault) {
    private val preferences = context.getSharedPreferences("libraries", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(SavedLibrary.serializer())
    private val state = MutableStateFlow(read())

    val all: StateFlow<List<SavedLibrary>> = state.asStateFlow()

    fun token(library: SavedLibrary): String? = vault.open(library.sealedToken)

    fun remember(libraryId: String, name: String, address: String, deviceId: String, token: String) {
        val saved = SavedLibrary(libraryId, name, address, deviceId, vault.seal(token))
        write(listOf(saved) + state.value.filter { it.libraryId != libraryId })
    }

    fun rename(libraryId: String, name: String) {
        write(state.value.map { if (it.libraryId == libraryId) it.copy(name = name) else it })
    }

    /** The same library at another address, keeping its token. */
    fun move(libraryId: String, address: String) {
        write(state.value.map { if (it.libraryId == libraryId) it.copy(address = address) else it })
    }

    fun forget(libraryId: String) {
        write(state.value.filter { it.libraryId != libraryId })
    }

    private fun read(): List<SavedLibrary> {
        val text = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching { wire.decodeFromString(serializer, text) }.getOrDefault(emptyList())
    }

    private fun write(libraries: List<SavedLibrary>) {
        preferences.edit().putString(KEY, wire.encodeToString(serializer, libraries)).apply()
        state.value = libraries
    }

    private companion object {
        const val KEY = "paired"
    }
}
