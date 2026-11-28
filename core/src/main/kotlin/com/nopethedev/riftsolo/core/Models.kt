package com.nopethedev.riftsolo.core

import kotlinx.serialization.Serializable

/** A playable song as the app sees it. Mirrors Rift's `Candidate` (shared/music-protocol.ts). */
@Serializable
data class Track(
    /** Stable id per source item, e.g. "youtube:dQw4w9WgXcQ" or "soundcloud:123". */
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int = 0,
    /** Page URL the extractor can open (YouTube watch URL, SoundCloud permalink, ...). */
    val url: String,
    val artworkUrl: String? = null,
    /** "youtube", "soundcloud", ... */
    val source: String = "",
    /** Which engine found it: "phone" or "lavalink". */
    val engine: String = "phone",
)

@Serializable
data class Playlist(
    val id: String, val name: String, val tracks: List<Track> = emptyList(), val createdAt: Long = 0,
    val artworkUrl: String? = null,
    /** Where it was imported from (a Spotify link), if anywhere. */
    val importedFrom: String? = null,
)

/** What the player is handed once an engine has opened a track. */
@Serializable
data class PlayableStream(val url: String, val headers: Map<String, String> = emptyMap(), val durationSec: Int = 0, val mimeType: String? = null)

enum class EngineChoice(val id: String) {
    /** Lavalink first when a node is set, then the phone's own search: same rule as Rift's "auto". */
    AUTO("auto"),
    PHONE("phone"),
    LAVALINK("lavalink");

    companion object { fun of(id: String?) = if (id == "ytdlp") PHONE else entries.firstOrNull { it.id == id } ?: LAVALINK }
}

enum class SourceProvider(val id: String, val label: String, val lavalinkPrefix: String) {
    YOUTUBE("youtube", "YouTube", "ytsearch"),
    SOUNDCLOUD("soundcloud", "SoundCloud", "scsearch");

    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } }
}

fun Int.formatDuration(): String = if (this <= 0) "" else "%d:%02d".format(this / 60, this % 60)

/** Genres offered in onboarding (Rift asks new profiles for 3 favourites too) and the searches behind their home rows. */
data class Genre(val id: String, val label: String, val query: String, val colors: Pair<Long, Long>)

val GENRES = listOf(
    Genre("synthwave", "Synthwave", "synthwave mix", 0xFF7C3AED to 0xFFEC4899),
    Genre("lofi", "Lo-fi", "lofi hip hop beats", 0xFF0EA5E9 to 0xFF1E1B4B),
    Genre("pop", "Pop", "pop hits", 0xFFF43F5E to 0xFFF59E0B),
    Genre("hiphop", "Hip-hop", "hip hop hits", 0xFFF59E0B to 0xFF7C2D12),
    Genre("rock", "Rock", "rock classics", 0xFFDC2626 to 0xFF1F2937),
    Genre("electronic", "Electronic", "electronic music", 0xFF22D3EE to 0xFF312E81),
    Genre("indie", "Indie", "indie songs", 0xFF84CC16 to 0xFF14532D),
    Genre("jazz", "Jazz", "jazz classics", 0xFFD97706 to 0xFF451A03),
    Genre("classical", "Classical", "classical music", 0xFFA78BFA to 0xFF1E1B4B),
    Genre("rnb", "R&B", "r&b songs", 0xFFBE185D to 0xFF3B0764),
    Genre("metal", "Metal", "metal songs", 0xFF52525B to 0xFF09090B),
    Genre("ambient", "Ambient", "ambient music", 0xFF14B8A6 to 0xFF0F172A),
)
