package com.digitalku.roost.remote.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * The shapes this app reads and sends, declared again in Kotlin.
 *
 * The reference is `@roost/contract` in the roost repository (`dto.ts`,
 * `request.ts`, `event.ts`), as REMOTE.md says. Only the fields this app uses
 * are declared, and the decoder ignores the rest, so a server that adds a field
 * breaks nothing here. A field this app declares and the server stops sending
 * is a decoding error, which is the right answer: it would be a change version 1
 * of the API promised not to make (RR-2).
 */

/** The one decoder and encoder for everything on the wire. */
val wire = Json {
    ignoreUnknownKeys = true
    // RR-8: a field the contract requires is sent, `null` included.
    explicitNulls = true
    encodeDefaults = true
    classDiscriminator = "type"
}

@Serializable
data class Hello(val api: Int, val build: String, val library: HelloLibrary?)

@Serializable
data class HelloLibrary(val id: String, val name: String)

@Serializable
data class Paired(val token: String, val device: Device)

@Serializable
data class Device(val id: String, val name: String, val pairedAt: Long, val lastSeen: Long?)

@Serializable
data class Problem(
    val code: String,
    val message: String,
    val detail: JsonObject = JsonObject(emptyMap()),
)

/**
 * Where a listing resumes. Opaque: it is handed back exactly as it came, and
 * `key` is a number or a string depending on the order.
 */
@Serializable
data class Cursor(val key: JsonPrimitive, val id: String, val section: String)

@Serializable
data class Sort(val key: String, val descending: Boolean, val seed: Int)

@Serializable
data class AssetCard(
    val id: String,
    val name: String,
    val ext: String,
    val kind: String,
    val size: Long?,
    val width: Int?,
    val height: Int?,
    val importedAt: Long,
    val rating: Int?,
    val posterMs: Long?,
    val derivedAt: Long?,
    val undrawn: String?,
    val chosenThumbnail: Boolean,
)

@Serializable
data class AssetPage(val assets: List<AssetCard>, val next: Cursor?, val beneath: Beneath?)

@Serializable
data class Beneath(val at: Int, val count: Int)

/**
 * A row of the window's navigation pane. A folder's row also carries `folder`
 * and `path`, which the other rows do not have.
 */
@Serializable
data class SidebarRow(
    val id: String,
    val name: String,
    val query: String,
    val count: Long?,
    val depth: Int,
    val stars: Int?,
    val group: String?,
    val folder: Long? = null,
    val path: String? = null,
)

@Serializable
data class Sidebar(
    val places: List<SidebarRow>,
    val folders: List<SidebarRow>,
    val smartFolders: List<SidebarRow>,
    val tags: List<SidebarRow>,
)

@Serializable
data class FolderRef(val id: Long, val path: String)

@Serializable
data class Comment(
    val id: Long,
    val atMs: Long?,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long?,
)

@Serializable
data class AssetDetail(
    val id: String,
    val name: String,
    val ext: String,
    val kind: String,
    val browserDrawable: Boolean,
    val size: Long?,
    val width: Int?,
    val height: Int?,
    val contentHash: String?,
    val derivedAt: Long?,
    val createdAt: Long?,
    val importedAt: Long,
    val trashedAt: Long?,
    val rating: Int?,
    val note: String?,
    val sourceUrl: String?,
    val tags: List<String>,
    val folders: List<FolderRef>,
    val durationMs: Long?,
    val posterMs: Long?,
    val pageCount: Int?,
    val props: Map<String, String> = emptyMap(),
    val comments: List<Comment> = emptyList(),
)

/** One asset `assets:derived` says has a new picture. */
@Serializable
data class Redrawn(
    val id: String,
    val derivedAt: Long?,
    val undrawn: String?,
    val chosen: Boolean,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class LibrarySummary(val id: String, val name: String, val hasPassword: Boolean)

/**
 * The requests this app sends, each spelled as the contract spells it. The
 * class discriminator is the contract's `type`.
 *
 * Every one of them is on the server's allowlist (RR-9). Deleting for good is
 * not here yet: when it is, RR-10's confirmation comes with it.
 */
@Serializable
sealed interface Request

@Serializable
@SerialName("library:current")
data object CurrentLibrary : Request

@Serializable
@SerialName("library:sidebar")
data object ReadSidebar : Request

@Serializable
@SerialName("assets:page")
data class ReadPage(val query: String, val limit: Int, val after: Cursor?, val sort: Sort?) : Request

@Serializable
@SerialName("assets:count")
data class CountAssets(val query: String) : Request

@Serializable
@SerialName("asset:detail")
data class ReadDetail(val id: String) : Request

@Serializable
@SerialName("asset:rate")
data class Rate(val ids: List<String>, val rating: Int?) : Request

@Serializable
@SerialName("asset:note")
data class WriteNote(val ids: List<String>, val note: String?) : Request

@Serializable
@SerialName("asset:tagAdd")
data class AddTags(val ids: List<String>, val tags: List<String>) : Request

@Serializable
@SerialName("asset:tagRemove")
data class RemoveTags(val ids: List<String>, val tags: List<String>) : Request

@Serializable
@SerialName("asset:folderAdd")
data class AddToFolder(val ids: List<String>, val folder: Long) : Request

@Serializable
@SerialName("asset:folderRemove")
data class RemoveFromFolder(val ids: List<String>, val folder: Long) : Request

@Serializable
@SerialName("assets:trash")
data class Trash(val ids: List<String>) : Request

@Serializable
@SerialName("assets:restore")
data class Restore(val ids: List<String>) : Request

@Serializable
@SerialName("derive:onScreen")
data class DrawOnScreen(val ids: List<String>) : Request
