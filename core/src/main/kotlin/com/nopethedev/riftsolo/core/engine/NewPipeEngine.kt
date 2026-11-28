package com.nopethedev.riftsolo.core.engine

import com.nopethedev.riftsolo.core.PlayableStream
import com.nopethedev.riftsolo.core.SourceProvider
import com.nopethedev.riftsolo.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

/** NewPipe Extractor's HTTP layer on OkHttp (same approach as the NewPipe app's DownloaderImpl). */
class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val body = request.dataToSend()?.toRequestBody() ?: if (method == "POST") ByteArray(0).toRequestBody() else null
        val b = okhttp3.Request.Builder().url(request.url()).method(method, body).header("User-Agent", WEB_UA)
        request.headers().forEach { (k, values) -> b.removeHeader(k); values.forEach { b.addHeader(k, it) } }
        client.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            return Response(r.code, r.message, r.headers.toMultimap(), r.body.string(), r.request.url.toString())
        }
    }

    companion object {
        const val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}

/**
 * The phone's own extractor: NewPipe Extractor (pure Java, no Python), replacing yt-dlp. It turns a YouTube /
 * SoundCloud / Bandcamp page URL (what Lavalink returns) into a direct audio stream URL for ExoPlayer, in about a second.
 * It can also search, as the fallback when no Lavalink node answers, and find similar songs (YouTube mixes).
 */
class NewPipeEngine(
    private val providers: suspend () -> List<SourceProvider>,
    http: OkHttpClient = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build(),
) : Engine {
    override val id = "phone"

    init {
        synchronized(NewPipeEngine::class.java) { if (!initialized) { NewPipe.init(OkHttpDownloader(http), Localization.DEFAULT); initialized = true } }
    }

    override suspend fun search(text: String, limit: Int): List<Track> = coroutineScope {
        val ps = providers().ifEmpty { listOf(SourceProvider.YOUTUBE) }
        val jobs = ps.map { p -> async { runCatching { searchProvider(p, text, limit) } } }
        val results = jobs.map { it.await() }
        if (results.all { it.isFailure }) throw wrap(results.first().exceptionOrNull()!!)
        interleave(results.map { it.getOrDefault(emptyList()) }).take(limit)
    }

    /** YouTube Music "songs" for YouTube (clean titles/artists, like Lavalink's ytmsearch), tracks for SoundCloud. */
    suspend fun searchProvider(p: SourceProvider, text: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val (service, filter) = when (p) {
            SourceProvider.YOUTUBE -> ServiceList.YouTube to listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)
            SourceProvider.SOUNDCLOUD -> ServiceList.SoundCloud to listOf("tracks")
        }
        fun run(f: List<String>): List<Track> {
            val info = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(text, f, ""))
            val items = info.relatedItems.filterIsInstance<StreamInfoItem>().filter { it.infoType == InfoItem.InfoType.STREAM }
            if (items.isEmpty() && info.errors.isNotEmpty()) throw wrap(info.errors.first())
            return items.take(limit).map { it.toTrack(p.id) }
        }
        try {
            run(filter).ifEmpty { if (p == SourceProvider.YOUTUBE) run(listOf(YoutubeSearchQueryHandlerFactory.VIDEOS)) else emptyList() }
        } catch (e: Exception) {
            // YouTube Music's search can fail on its own; regular YouTube search still finds the song.
            if (p == SourceProvider.YOUTUBE) run(listOf(YoutubeSearchQueryHandlerFactory.VIDEOS)) else throw wrap(e)
        }
    }

    /** Regular YouTube video uploads (official videos, lyric videos...): used as alternates when a song upload won't play. */
    suspend fun searchVideos(text: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val handler = ServiceList.YouTube.searchQHFactory.fromQuery(text, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        SearchInfo.getInfo(ServiceList.YouTube, handler).relatedItems.filterIsInstance<StreamInfoItem>()
            .filter { it.infoType == InfoItem.InfoType.STREAM }.take(limit).map { it.toTrack("youtube") }
    }

    override suspend fun open(track: Track): PlayableStream = openWithRelated(track).first

    /** The stream, plus the "related" songs YouTube sends on the same page (kept as similar songs, no extra request). */
    suspend fun openWithRelated(track: Track): Pair<PlayableStream, List<Track>> = withContext(Dispatchers.IO) {
        if (!track.url.startsWith("http")) throw EngineException("Unsupported URL", EngineException.Code.FAILED)
        val info = try { StreamInfo.getInfo(track.url) } catch (e: Exception) { throw wrap(e) }
        val audio = pickAudio(info.audioStreams)
            ?: throw EngineException("No playable audio for \"${track.title}\"", EngineException.Code.NOT_FOUND)
        val source = if (info.service == ServiceList.SoundCloud) "soundcloud" else "youtube"
        val stream = PlayableStream(
            url = audio.content,
            headers = headersFor(audio.content),
            durationSec = info.duration.toInt(),
            mimeType = if (audio.deliveryMethod == DeliveryMethod.HLS) "application/x-mpegURL" else audio.format?.mimeType,
        )
        Pair(stream, relatedOf(info, source))
    }

    /**
     * Songs like this one: YouTube's own "Mix" for the video (the radio YouTube plays after a song), else the related
     * videos of its page. Straight from YouTube, so the rate-limited Lavalink nodes aren't asked.
     */
    suspend fun similar(track: Track): List<Track> = withContext(Dispatchers.IO) { similarBlocking(track) }

    private fun similarBlocking(track: Track): List<Track> {
        val vid: String? = youtubeId(track.url)
        if (vid != null) {
            val direct: List<Track>? = mix(vid)
            if (direct != null) return direct
            // Mixes of song uploads ("Topic", music.youtube.com) need YouTube's cookie consent in some countries;
            // the same song's regular video upload has a mix that doesn't.
            val other: String? = videoUploadOf(track, vid)
            if (other != null) {
                val viaVideo: List<Track>? = mix(other)
                if (viaVideo != null) return viaVideo
            }
        }
        val info: StreamInfo = try { StreamInfo.getInfo(track.url) } catch (e: Exception) { throw wrap(e) }
        return relatedOf(info, if (vid != null) "youtube" else track.source)
    }

    /** Video id of a regular upload of the same song (not [notId]), found by a YouTube search. */
    private fun videoUploadOf(track: Track, notId: String): String? {
        val q = track.artist.removeSuffix(" - Topic") + " " + track.title
        val handler = ServiceList.YouTube.searchQHFactory.fromQuery(q, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        val items: List<InfoItem> = try { SearchInfo.getInfo(ServiceList.YouTube, handler).relatedItems } catch (e: Exception) { return null }
        for (item in items.take(4)) {
            if (item !is StreamInfoItem) continue
            val id: String = youtubeId(item.url) ?: continue
            if (id != notId && (track.durationSec <= 0 || Math.abs(item.duration - track.durationSec) <= 30)) return id
        }
        return null
    }

    private fun mix(vid: String): List<Track>? {
        val info: PlaylistInfo = try { PlaylistInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$vid&list=RD$vid") } catch (e: Exception) { return null }
        val out = ArrayList<Track>()
        for (item in info.relatedItems) {
            if (item.infoType == InfoItem.InfoType.STREAM && item.duration > 0 && youtubeId(item.url) != vid) out.add(item.toTrack("youtube"))
        }
        return if (out.size >= 5) out else null
    }

    private fun relatedOf(info: StreamInfo, source: String): List<Track> {
        val out = ArrayList<Track>()
        try {
            for (item in info.relatedItems) {
                if (item is StreamInfoItem && item.infoType == InfoItem.InfoType.STREAM && item.duration > 0) out.add(item.toTrack(source))
            }
        } catch (_: Exception) {}
        return out
    }

    companion object {
        @Volatile private var initialized = false

        /** Direct HTTP first (M4A, then Opus/WebM, then MP3), highest bitrate; HLS only when that's all there is. */
        fun pickAudio(streams: List<AudioStream>): AudioStream? {
            val usable = streams.filter { it.isUrl && it.content.isNotEmpty() && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.HLS) }
            val rank = { s: AudioStream -> when (s.format) { MediaFormat.M4A -> 3; MediaFormat.WEBMA_OPUS, MediaFormat.WEBMA, MediaFormat.OPUS -> 2; MediaFormat.MP3 -> 1; else -> 0 } }
            return usable.sortedWith(compareBy<AudioStream>({ if (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP) 0 else 1 }, { -rank(it) }, { -it.averageBitrate })).firstOrNull()
        }

        /** The headers YouTube's servers expect for each client's stream URLs (as NewPipe's player sends them). */
        fun headersFor(url: String): Map<String, String> = when {
            YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(Localization.DEFAULT))
            YoutubeParsingHelper.isWebStreamingUrl(url) -> mapOf("User-Agent" to OkHttpDownloader.WEB_UA, "Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/")
            else -> mapOf("User-Agent" to OkHttpDownloader.WEB_UA)
        }

        private val YT_ID = Regex("(?:v=|youtu\\.be/|/shorts/|/embed/)([A-Za-z0-9_-]{11})")
        fun youtubeId(url: String): String? = if ("youtu" in url) YT_ID.find(url)?.groupValues?.get(1) else null

        fun interleave(lists: List<List<Track>>): List<Track> {
            val seen = HashSet<String>()
            val out = ArrayList<Track>()
            var i = 0
            while (lists.any { i < it.size }) {
                for (l in lists) l.getOrNull(i)?.let { if (seen.add(it.id)) out += it }
                i++
            }
            return out
        }

        private fun StreamInfoItem.toTrack(source: String) = Track(
            id = "$source:${url.substringAfter("v=").substringBefore('&').takeIf { source == "youtube" } ?: url}",
            title = name,
            artist = uploaderName.orEmpty().removeSuffix(" - Topic"),
            durationSec = duration.toInt().coerceAtLeast(0),
            url = url,
            artworkUrl = thumbnails.maxByOrNull { it.width }?.url,
            source = source,
            engine = "phone",
        )

        fun wrap(e: Throwable): EngineException = when (e) {
            is EngineException -> e
            is ReCaptchaException -> EngineException("YouTube wants a captcha from this network. Try again later or switch networks.", EngineException.Code.UNAVAILABLE, e)
            is ContentNotAvailableException -> EngineException(e.message ?: "This song isn't available", EngineException.Code.NOT_FOUND, e)
            else -> EngineException(e.message ?: e.javaClass.simpleName, EngineException.Code.FAILED, e)
        }
    }
}
