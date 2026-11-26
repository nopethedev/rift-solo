package com.nopethedev.riftsolo.core.data

import android.content.Context
import com.nopethedev.riftsolo.core.Playlist
import com.nopethedev.riftsolo.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class Library(
    val liked: List<Track> = emptyList(),
    val recent: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val plays: Int = 0,
    /** Listening history per song (plays, finished, skipped): what the taste profile learns from. */
    val stats: Map<String, ListenStat> = emptyMap(),
)

@Serializable
data class ListenStat(val plays: Int = 0, val completes: Int = 0, val skips: Int = 0, val lastAt: Long = 0, val track: Track? = null)

enum class Listen { STARTED, COMPLETED, SKIPPED }

/** Liked songs, history and playlists in one small JSON file: no database needed for a portable app. */
class LibraryStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "library.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _library = MutableStateFlow(load())
    val library: StateFlow<Library> = _library.asStateFlow()

    private fun load(): Library = runCatching { json.decodeFromString<Library>(file.readText()) }.getOrDefault(Library())

    private fun change(block: (Library) -> Library) {
        _library.update(block)
        val snapshot = _library.value
        scope.launch(Dispatchers.IO) {
            val tmp = File(file.parentFile, "library.json.tmp")
            tmp.writeText(json.encodeToString(Library.serializer(), snapshot))
            tmp.renameTo(file)
        }
    }

    fun isLiked(id: String) = _library.value.liked.any { it.id == id }
    fun toggleLike(t: Track) = change { l -> if (l.liked.any { it.id == t.id }) l.copy(liked = l.liked.filterNot { it.id == t.id }) else l.copy(liked = listOf(t) + l.liked) }
    fun played(t: Track) = change { l -> l.copy(recent = (listOf(t) + l.recent.filterNot { it.id == t.id }).take(50), plays = l.plays + 1) }

    /** Called by the player: a song started, was listened to the end (or most of it), or was skipped early. */
    fun listen(t: Track, kind: Listen) = change { l ->
        val s = l.stats[t.id] ?: ListenStat(track = t)
        val n = when (kind) {
            Listen.STARTED -> s.copy(plays = s.plays + 1, lastAt = System.currentTimeMillis(), track = t)
            Listen.COMPLETED -> s.copy(completes = s.completes + 1)
            Listen.SKIPPED -> s.copy(skips = s.skips + 1)
        }
        var stats = l.stats + (t.id to n)
        if (stats.size > MAX_STATS) stats = stats.entries.sortedByDescending { it.value.lastAt }.take(MAX_STATS).associate { it.key to it.value }
        l.copy(stats = stats)
    }
    fun createPlaylist(name: String): String {
        val id = UUID.randomUUID().toString()
        change { it.copy(playlists = it.playlists + Playlist(id, name.ifBlank { "My playlist #${it.playlists.size + 1}" }, createdAt = System.currentTimeMillis())) }
        return id
    }
    fun addPlaylist(p: Playlist) = change { it.copy(playlists = it.playlists + p) }
    fun renamePlaylist(id: String, name: String) = change { l -> l.copy(playlists = l.playlists.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it }) }
    fun removeFromPlaylist(playlistId: String, trackId: String) = change { l -> l.copy(playlists = l.playlists.map { if (it.id == playlistId) it.copy(tracks = it.tracks.filterNot { t -> t.id == trackId }) else it }) }
    fun addToPlaylist(playlistId: String, t: Track) = change { l -> l.copy(playlists = l.playlists.map { if (it.id == playlistId && it.tracks.none { x -> x.id == t.id }) it.copy(tracks = it.tracks + t) else it }) }
    fun deletePlaylist(id: String) = change { l -> l.copy(playlists = l.playlists.filterNot { it.id == id }) }

    private companion object { const val MAX_STATS = 3000 }
}
