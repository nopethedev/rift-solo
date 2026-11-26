package com.nopethedev.riftsolo.core.taste

import com.nopethedev.riftsolo.core.GENRES
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.data.Library
import com.nopethedev.riftsolo.core.data.ListenStat
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * What the user likes, built on the phone from what they do: likes, playlist adds, plays, songs finished and songs
 * skipped, plus the genres picked in onboarding. Recent behaviour counts more (30-day half-life).
 */
data class TasteProfile(
    /** Artist key → affinity (can be negative for artists that keep getting skipped). */
    val artists: Map<String, Double> = emptyMap(),
    /** Words from titles / artists of songs they like ("lofi", "remix", "acoustic"...). */
    val terms: Map<String, Double> = emptyMap(),
    /** Genre id → 0..1. */
    val genres: Map<String, Double> = emptyMap(),
    /** Track id → affinity, for picking seeds. */
    val tracks: Map<String, Double> = emptyMap(),
    /** Songs the user skips more than they finish. */
    val skipped: Set<String> = emptySet(),
    /** Track ids played in the last 24 h (kept out of recommendations so they feel fresh). */
    val recentlyPlayed: Set<String> = emptySet(),
    /** Seeds for recommendations: their best-liked songs, best first. */
    val seeds: List<Track> = emptyList(),
    val signals: Int = 0,
) {
    val topArtists: List<Pair<String, Double>> get() = artists.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key to it.value }
    val topGenres: List<Pair<String, Double>> get() = genres.entries.sortedByDescending { it.value }.map { it.key to it.value }
    val isEmpty get() = signals == 0
}

object Taste {
    private const val HALF_LIFE_DAYS = 30.0

    /** Signal weights. A like says more than a play; finishing a song says more than starting it; a quick skip says "no". */
    const val W_LIKE = 4.0
    const val W_PLAYLIST = 2.5
    const val W_PLAY = 1.0
    const val W_COMPLETE = 1.5
    const val W_SKIP = -2.0

    private val STOP = setOf(
        "the", "a", "an", "and", "of", "to", "in", "on", "for", "with", "by", "my", "your", "you", "me", "is", "it", "feat", "ft",
        "official", "video", "audio", "lyrics", "lyric", "music", "hd", "hq", "4k", "mv", "visualizer", "topic", "vevo", "version",
        "original", "full", "new", "song", "x", "vs", "prod", "records",
    )

    /** Extra words that point to a genre, on top of the genre's own name. */
    private val GENRE_WORDS = mapOf(
        "synthwave" to listOf("synthwave", "retrowave", "outrun", "80s", "synth"),
        "lofi" to listOf("lofi", "lo-fi", "chillhop", "study", "chill"),
        "pop" to listOf("pop"),
        "hiphop" to listOf("rap", "hiphop", "hip-hop", "trap", "drill", "phonk"),
        "rock" to listOf("rock", "punk", "grunge", "guitar"),
        "electronic" to listOf("edm", "house", "techno", "trance", "dubstep", "electronic", "remix", "dnb", "drum"),
        "indie" to listOf("indie", "alt", "alternative", "bedroom"),
        "jazz" to listOf("jazz", "swing", "bossa", "saxophone"),
        "classical" to listOf("classical", "symphony", "piano", "concerto", "orchestra", "sonata"),
        "rnb" to listOf("r&b", "rnb", "soul", "neo-soul"),
        "metal" to listOf("metal", "metalcore", "deathcore", "thrash"),
        "ambient" to listOf("ambient", "drone", "meditation", "sleep", "relaxing"),
    )

    /** "Daft Punk - Topic", "DaftPunkVEVO", "Daft Punk, Pharrell" → "daft punk" (the first credited artist). */
    fun artistKey(artist: String): String = artist.lowercase()
        .replace(" - topic", "").replace(Regex("vevo$"), "").replace("official", "")
        .split(Regex(",|&| feat\\.? | ft\\.? | x | and | with "))
        .first().replace(Regex("[^\\p{L}\\p{N} ]"), "").trim().replace(Regex("\\s+"), " ")

    fun artistKeys(artist: String): List<String> = artist.lowercase().replace(" - topic", "")
        .split(Regex(",|&| feat\\.? | ft\\.? | x | with ")).map { it.replace(Regex("[^\\p{L}\\p{N} ]"), "").trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }

    /** Title without "(Official Video)", "[Lyrics]", "- Remastered 2011"... so different uploads of a song collapse. */
    fun songKey(t: Track): String {
        val title = t.title.lowercase()
            .replace(Regex("[(\\[].*?[)\\]]"), " ")
            .replace(Regex("\\s[-–|]\\s.*(remaster|official|video|audio|lyric|visuali).*$"), " ")
            .let { s -> artistKeys(t.artist).fold(s) { acc, a -> acc.replace("$a - ", " ").replace(" - $a", " ") } }
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
        return title + "|" + artistKey(t.artist)
    }

    fun terms(t: Track): List<String> = (t.title + " " + t.artist).lowercase()
        .split(Regex("[^\\p{L}\\p{N}&-]+")).filter { it.length > 2 && it !in STOP && !it.all(Char::isDigit) }.distinct()

    fun genresOf(t: Track): Set<String> {
        val text = (t.title + " " + t.artist).lowercase()
        return GENRE_WORDS.filter { (_, words) -> words.any { w -> Regex("(^|[^\\p{L}])${Regex.escape(w)}([^\\p{L}]|$)").containsMatchIn(text) } }.keys
    }

    private fun decay(at: Long, now: Long): Double = if (at <= 0) 1.0 else exp(-ln(2.0) * max(0L, now - at) / (HALF_LIFE_DAYS * 86_400_000.0))

    fun build(library: Library, pickedGenres: Set<String>, now: Long = System.currentTimeMillis()): TasteProfile {
        val artists = HashMap<String, Double>()
        val terms = HashMap<String, Double>()
        val genres = HashMap<String, Double>()
        val trackScore = HashMap<String, Double>()
        val byId = HashMap<String, Track>()
        var signals = 0

        fun signal(t: Track, w: Double) {
            byId.putIfAbsent(t.id, t)
            trackScore.merge(t.id, w, Double::plus)
            artistKeys(t.artist).forEachIndexed { i, a -> artists.merge(a, if (i == 0) w else w * 0.5, Double::plus) }
            if (w > 0) {
                terms(t).forEach { terms.merge(it, w, Double::plus) }
                genresOf(t).forEach { genres.merge(it, w, Double::plus) }
            }
            signals++
        }

        library.liked.forEachIndexed { i, t -> signal(t, W_LIKE * (if (i < 20) 1.0 else 0.8)) }
        library.playlists.forEach { p -> p.tracks.forEach { signal(it, W_PLAYLIST * decay(p.createdAt, now).coerceAtLeast(0.5)) } }
        val stats = library.stats
        library.recent.forEach { t ->
            val s = stats[t.id]
            if (s == null) signal(t, W_PLAY) else signal(t, statWeight(s, now))
        }
        // Songs that fell out of "recent" still count through their stats.
        stats.forEach { (id, s) -> if (library.recent.none { it.id == id } && s.track != null) signal(s.track, statWeight(s, now)) }

        // Genres picked in onboarding are a prior that fades as real listening takes over.
        val maxG = genres.values.maxOrNull() ?: 0.0
        val prior = max(3.0, maxG * 0.6)
        pickedGenres.forEach { genres.merge(it, prior, Double::plus) }
        val gMax = genres.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0

        val skipped = stats.filter { (_, s) -> s.skips >= 2 && s.skips > s.completes }.keys
        val recentCut = now - 86_400_000L
        val seeds = trackScore.entries.filter { it.value > 0 && it.key !in skipped }.sortedByDescending { it.value }.mapNotNull { byId[it.key] }

        return TasteProfile(
            artists = artists, terms = terms, genres = genres.mapValues { min(1.0, it.value / gMax) }, tracks = trackScore,
            skipped = skipped, recentlyPlayed = stats.filter { it.value.lastAt > recentCut }.keys, seeds = seeds, signals = signals,
        )
    }

    private fun statWeight(s: ListenStat, now: Long) =
        (W_PLAY * min(s.plays, 10) + W_COMPLETE * min(s.completes, 10) + W_SKIP * min(s.skips, 10)) * decay(s.lastAt, now)

    /**
     * How well [t] fits the profile, roughly 0..1 (can go below 0). [context] is how strongly the source suggests it
     * (1 = first item of a song's mix, 0 = last). Weights: what the source thinks 35 %, artist 30 %, words 15 %,
     * genre 10 %, a little exploration for artists they've never played, minus penalties for things they skip, songs
     * they just heard and hour-long compilations.
     */
    fun score(t: Track, p: TasteProfile, context: Double = 0.5): Double {
        val aMax = p.artists.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val aff = artistKeys(t.artist).maxOfOrNull { p.artists[it] ?: 0.0 } ?: 0.0
        val artist = if (aff >= 0) ln(1 + aff) / ln(1 + aMax) else max(-1.0, aff / 4.0)

        val tMax = p.terms.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val ws = terms(t)
        val term = if (ws.isEmpty()) 0.0 else min(1.0, ws.sumOf { (p.terms[it] ?: 0.0) / tMax } / 2.0)
        val genre = genresOf(t).maxOfOrNull { p.genres[it] ?: 0.0 } ?: 0.0

        val novelty = if (aff == 0.0) 0.08 else 0.0
        var penalty = 0.0
        if (t.id in p.skipped) penalty += 0.6
        if (t.id in p.recentlyPlayed) penalty += 0.3
        val title = t.title.lowercase()
        if (Regex("\\b(\\d+\\s*hours?|full album|compilation|mix 20\\d\\d|playlist|megamix|nonstop)\\b").containsMatchIn(title)) penalty += 0.5
        if (t.durationSec > 12 * 60 && genre < 0.5) penalty += 0.3
        // Variants people rarely want unless they already listen to them.
        VARIANTS.find(title)?.let { m -> if ((p.terms[m.value.split(' ').first()] ?: 0.0) <= 0.0) penalty += 0.25 }
        if (t.durationSec in 1..59) penalty += 0.3
        return 0.35 * context + 0.30 * artist + 0.15 * term + 0.10 * genre + novelty - penalty
    }

    /**
     * Ranks candidates and keeps the list varied: duplicates (same song, other upload) are merged, an artist gets less
     * attractive each time it's picked (and at most [perArtist] times), and [exclude]d ids never show.
     */
    fun rank(candidates: List<Scored>, p: TasteProfile, limit: Int, exclude: Set<String> = emptySet(), excludeSongs: Set<String> = emptySet(), perArtist: Int = 2): List<Track> {
        val best = LinkedHashMap<String, Scored>()
        // Covers, remixes and re-uploads of a song that's already there ("Never Gonna Give You Up (Rick Astley Cover)").
        val titles = excludeSongs.map { it.substringBefore('|') }.filter { it.length >= 4 }.toSet()
        for (c in candidates) {
            if (c.track.id in exclude) continue
            val k = songKey(c.track)
            if (k in excludeSongs) continue
            val title = k.substringBefore('|')
            if (titles.any { title.contains(it) }) continue
            val prev = best[k]
            // Found by several seeds = more likely a good fit.
            best[k] = if (prev == null) c else Scored(if (prev.context >= c.context) prev.track else c.track, max(prev.context, c.context) + 0.15, prev.hits + 1)
        }
        val pool = best.values.map { it to score(it.track, p, min(1.0, it.context)) }.sortedByDescending { it.second }.toMutableList()
        val out = ArrayList<Track>()
        val perA = HashMap<String, Int>()
        while (out.size < limit && pool.isNotEmpty()) {
            var bi = -1; var bs = Double.NEGATIVE_INFINITY
            for (i in pool.indices) {
                val a = artistKey(pool[i].first.track.artist)
                val n = perA[a] ?: 0
                if (n >= perArtist) continue
                val s = pool[i].second - 0.25 * n
                if (s > bs) { bs = s; bi = i }
            }
            if (bi < 0) break
            val pick = pool.removeAt(bi).first.track
            perA.merge(artistKey(pick.artist), 1, Int::plus)
            out += pick
        }
        return out
    }

    private val VARIANTS = Regex("\\b(cover|bootleg|nightcore|sped up|slowed|8d|karaoke|instrumental|reaction|tutorial|reverb)\\b")

    data class Scored(val track: Track, val context: Double, val hits: Int = 1)

    /** Turns a source's list (mix, related, search) into candidates, earlier = stronger. */
    fun fromList(list: List<Track>, weight: Double = 1.0): List<Scored> =
        list.mapIndexed { i, t -> Scored(t, weight * (1.0 - i.toDouble() / max(1, list.size) * 0.7)) }

    fun genreLabel(id: String) = GENRES.firstOrNull { it.id == id }?.label ?: id.replaceFirstChar(Char::uppercase)
}
