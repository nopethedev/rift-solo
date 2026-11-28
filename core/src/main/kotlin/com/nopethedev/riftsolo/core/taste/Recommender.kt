package com.nopethedev.riftsolo.core.taste

import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.engine.EngineHub
import kotlinx.coroutines.CancellationException

/**
 * Turns the taste profile into lists of songs. Candidates come from YouTube's mixes / related songs for a few seed
 * songs (cached for a week, so this rarely touches the network, and never the Lavalink node unless a seed has no
 * YouTube match), then [Taste.rank] scores them against the profile and keeps the list varied.
 */
class Recommender(private val hub: EngineHub) {
    /** Songs to add to a playlist (or Liked Songs): similar to its songs, minus what's already in it. */
    suspend fun forCollection(tracks: List<Track>, profile: TasteProfile, limit: Int = 20, salt: Int = 0): List<Track> {
        if (tracks.isEmpty()) return forYou(profile, limit, salt = salt)
        // The newest songs say most about where the playlist is going; a couple of random older ones keep it broad.
        val recent = tracks.takeLast(3)
        val rnd = java.util.Random(tracks.size * 31L + salt)
        val older = tracks.dropLast(3).shuffled(rnd).take(2)
        return fromSeeds(recent + older, profile, limit, exclude = tracks)
    }

    /** "Made for you": seeds are the user's best-liked songs, with some rotation so the row changes. */
    suspend fun forYou(profile: TasteProfile, limit: Int = 20, salt: Int = 0): List<Track> {
        if (profile.seeds.isEmpty()) return emptyList()
        val top = profile.seeds.take(12)
        val rnd = java.util.Random(salt.toLong())
        val seeds = (top.take(2) + top.drop(2).shuffled(rnd).take(2)).distinctBy { it.id }
        return fromSeeds(seeds, profile, limit, exclude = emptyList())
    }

    /** Autoplay: what to play after [last] when the queue runs out. */
    suspend fun next(last: Track, queue: List<Track>, profile: TasteProfile, limit: Int = 10): List<Track> =
        fromSeeds(listOf(last) + queue.takeLast(4).filter { it.id != last.id }.take(1), profile, limit, exclude = queue, perArtist = 2)

    suspend fun fromSeeds(seeds: List<Track>, profile: TasteProfile, limit: Int, exclude: List<Track>, perArtist: Int = 2): List<Track> {
        val candidates = ArrayList<Taste.Scored>()
        var last: Exception? = null
        seeds.forEachIndexed { i, seed ->
            try {
                // The first seed weighs most; others add variety.
                candidates += Taste.fromList(hub.similar(seed), weight = if (i == 0) 1.0 else 0.85)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { last = e }
        }
        if (candidates.isEmpty() && last != null) throw last!!
        val excludeSongs = exclude.map(Taste::songKey).toSet() + seeds.map(Taste::songKey)
        val excludeIds = exclude.map { it.id }.toSet() + seeds.map { it.id }
        var out = Taste.rank(candidates, profile, limit, exclude = excludeIds, excludeSongs = excludeSongs, perArtist = perArtist)
        // Few seeds (a new playlist) give a narrow list: take a second hop from the best picks so far.
        if (out.size < limit * 0.6) {
            // Only hop through artists we trust (the seeds' or ones the user likes), not random re-uploaders.
            val trusted = HashSet<String>()
            for (sd in seeds) trusted.addAll(Taste.artistKeys(sd.artist))
            for (a in profile.topArtists.take(30)) trusted.add(a.first)
            val hops = out.filter { t -> Taste.artistKeys(t.artist).any { a -> a in trusted } }.take(2)
            for (pick in hops) {
                try { candidates += Taste.fromList(hub.similar(pick), weight = 0.6) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            }
            out = Taste.rank(candidates, profile, limit, exclude = excludeIds, excludeSongs = excludeSongs, perArtist = perArtist)
        }
        return out
    }

    /** Re-orders a plain list (a genre search) so the songs that fit the user best come first. */
    fun rerank(list: List<Track>, profile: TasteProfile, limit: Int = list.size): List<Track> =
        if (profile.isEmpty) list.take(limit) else Taste.rank(Taste.fromList(list), profile, limit, perArtist = 3)
}
