package com.nopethedev.riftsolo.core.sync

import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.data.ArtistRef
import com.nopethedev.riftsolo.core.data.Library
import com.nopethedev.riftsolo.core.data.ListenStat
import kotlin.math.max

/**
 * Merges two copies of a library (this device's and the one on Rift Server) item by item.
 *
 * Every like, follow, exclusion, playlist and playlist entry has a "last changed" time in [Library.changes]. For each
 * item, the copy that changed it last wins: liked on the phone yesterday and unliked on the PC today → unliked.
 * Items with no recorded change (made before sync existed) are kept if either side has them.
 * Listening stats take the larger count of each side, so merging twice changes nothing.
 */
object LibraryMerge {
    /** Change times older than this are dropped once both sides agree, so the map doesn't grow forever. */
    private const val KEEP_CHANGES_MS = 120L * 86_400_000

    fun merge(a: Library, b: Library, now: Long = System.currentTimeMillis()): Library {
        val ca = a.changes
        val cb = b.changes
        /** Which side decides [key]: the one that changed it last (ties and unknowns: [a], the local copy). */
        fun aWins(key: String) = (ca[key] ?: 0L) >= (cb[key] ?: 0L)
        fun present(key: String, inA: Boolean, inB: Boolean): Boolean = when {
            inA == inB -> inA
            ca[key] == null && cb[key] == null -> true
            else -> if (aWins(key)) inA else inB
        }

        fun <T> mergeList(la: List<T>, lb: List<T>, id: (T) -> String, prefix: String): List<T> {
            val ida = la.associateBy(id)
            val idb = lb.associateBy(id)
            val order = (la.map(id) + lb.map(id)).distinct()
            return order.filter { present("$prefix:$it", it in ida, it in idb) }.map { k ->
                val x = ida[k]; val y = idb[k]
                if (x != null && y != null) (if (aWins("$prefix:$k")) x else y) else x ?: y!!
            }
        }

        val liked = mergeList(a.liked, b.liked, Track::id, "like")
        val followed = mergeList(a.followed, b.followed, ArtistRef::key, "follow")
        val excluded = mergeList(a.excluded, b.excluded, Track::id, "ex")

        val pa = a.playlists.associateBy { it.id }
        val pb = b.playlists.associateBy { it.id }
        val playlists = (a.playlists.map { it.id } + b.playlists.map { it.id }).distinct()
            .filter { present("pl:$it", it in pa, it in pb) }
            .map { id ->
                val x = pa[id]; val y = pb[id]
                if (x == null || y == null) return@map x ?: y!!
                val base = if (aWins("pl:$id")) x else y
                base.copy(tracks = mergeList(x.tracks, y.tracks, Track::id, "pl:$id"), createdAt = minOf(x.createdAt, y.createdAt).takeIf { it > 0 } ?: max(x.createdAt, y.createdAt))
            }

        val recent = (a.recent + b.recent).distinctBy { it.id }
            .sortedByDescending { max(a.stats[it.id]?.lastAt ?: 0, b.stats[it.id]?.lastAt ?: 0) }
            .take(50)

        val stats = HashMap<String, ListenStat>(a.stats)
        for ((id, s) in b.stats) {
            val m = stats[id]
            stats[id] = if (m == null) s else ListenStat(
                plays = max(m.plays, s.plays), completes = max(m.completes, s.completes), skips = max(m.skips, s.skips),
                lastAt = max(m.lastAt, s.lastAt), track = m.track ?: s.track,
            )
        }

        val cutoff = now - KEEP_CHANGES_MS
        val changes = HashMap<String, Long>(ca)
        for ((k, t) in cb) if (t > (changes[k] ?: 0L)) changes[k] = t
        changes.entries.removeIf { it.value < cutoff }

        return Library(
            liked = liked, recent = recent, playlists = playlists, plays = max(a.plays, b.plays),
            stats = stats, followed = followed, excluded = excluded, changes = changes,
        )
    }

    /** Same content (ignoring order of maps), so a sync that brings nothing new doesn't write. */
    fun same(a: Library, b: Library) = a.liked == b.liked && a.playlists == b.playlists && a.followed == b.followed &&
        a.excluded == b.excluded && a.recent == b.recent && a.plays == b.plays && a.stats == b.stats && a.changes == b.changes
}
