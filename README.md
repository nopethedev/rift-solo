# Rift Solo

The lightweight, portable Rift. An Android app (Kotlin, Jetpack Compose, Material 3) that searches, plays and collects music with no Rift server needed: songs are found through Lavalink nodes (public ones built in, or your own) and their audio is extracted on the phone by [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

- **Tabs**: Home (a feed built from the genres you pick), Search (browse tiles + results from YouTube / SoundCloud), Library (liked songs, history, playlists) and Profile (engine settings, components, stats).
- **Onboarding**: welcome → name and avatar → 3+ genres → where to search (Lavalink nodes, or the phone) → background playback (battery optimisation off + notification permission, with an extra hint on brands like Xiaomi / Samsung that kill background apps) → the sources are checked automatically, plus the "Allow all songs" consent.
- **Playlists**: add songs from a search sheet inside the playlist, remove / rename / delete, and a "Recommended" list at the end built from the playlist's songs. Spotify playlists and albums can be imported from a share link.
- **Taste profile**: learned on the phone from likes, playlist adds, plays, songs finished and songs skipped (30-day half-life), plus the onboarding genres. It drives "Made for you", "More like …", the order of the genre mixes, playlist suggestions and autoplay when the queue runs out. Profile shows top artists and genres.
- **Cache**: everything from the network is kept on the phone (see below), so free nodes with strict rate limits are asked as little as possible.
- **Lavalink nodes**: ships with public nodes ready to use, and you can add your own (form, or paste the `{"host", "port", "password", "secure"}` JSON that node lists share). Nodes are searched in order, offline ones skipped.
- **Player**: Media3 ExoPlayer in a `MediaSessionService` (background playback, notification and lock-screen controls), mini player plus a full-screen Now Playing view.

## How it maps to Rift

Rift's worker (`worker/music/`) has two engines behind one interface (`search`, `resolve`, `open`):

| Rift (server) | Rift Solo (phone) |
| --- | --- |
| `engines/ytdlp.ts`: spawns `yt-dlp`, pipes audio into ffmpeg and the music cache | `NewPipeEngine`: NewPipe Extractor (pure Java, ~1-3 s per song); `open()` returns the audio URL + the headers YouTube expects and ExoPlayer streams it. When an upload is refused (label/region), other uploads of the same song are tried |
| `engines/lavalink.ts` + `lavalink.ts`: REST `loadtracks` search over a node pool (online / least loaded first), then plays into the **voice sink** | `LavalinkEngine`: the same REST `v4/loadtracks` search and `v4/info` check over the enabled nodes, healthy first. Spotify / Deezer / Apple Music results (LavaSrc) are played through a YouTube match |
| `voice-sink.ts`: fake Discord voice gateway (WSS + UDP/RTP, AES-GCM) that catches Lavalink's Opus audio | Not ported (see below). A Lavalink track's page URL is handed to the extractor instead (`EngineHub.open`) |
| `engineChain`: `auto` = Lavalink when a node is configured, then yt-dlp | `engineChain` in `Engine.kt`: default is Lavalink only; `auto` adds the phone's own search as a fallback |
| `shared/lavalink-search.ts` prefix rules | `LavalinkEngine.searchPrefixes`, same rules |

### Why Lavalink doesn't run on the phone

Android runs apps on ART, not a Java SE JVM, so `Lavalink.jar` (Spring Boot, Netty, native audio libraries built for desktop Linux) can't run there.

### Why a node's audio can't reach the phone (Rift's voice sink)

Lavalink has no "give me the audio" endpoint. It only plays into a Discord voice connection: the client tells the node "connect to voice server X", and **the node opens the connection** (a TLS WebSocket, then UDP packets of encrypted Opus audio) to that address. Rift's `voice-sink.ts` pretends to be that voice server, so the audio lands on the Rift worker. For that to work:

1. The node must be able to **reach** the sink: Rift opens TCP 4443 + UDP 4444 on its server. A phone sits behind its carrier's or router's NAT, often CGNAT, with no public address a node on the internet could connect to.
2. The node must **trust the sink's TLS certificate**: Lavalink connects with `wss://` and its JVM checks the certificate, which is why Rift ships `deploy/lavalink-trust.sh` to add its certificate to the node's trust store. Nobody can do that on a public node.

So with a public node, the sink could never receive anything. It could work with a node you run on the same Wi-Fi after trusting the phone's certificate, which is why that isn't the default.

## Caching (`core/data/Cache.kt`)

One JSON file per entry under `files/cache/`, with an in-memory LRU in front. Two screens asking for the same thing share one request, and when the network fails (rate limit, node down) the last copy is used, however old.

| What | Kept for | Notes |
| --- | --- | --- |
| Search results (per engine + query) | 3 days | Shown instantly while typing if cached; otherwise sent 1 s after typing stops (or on the keyboard's search key) |
| Home feed | 6 h | The app opens on the last feed; refresh reshuffles from cached songs |
| Stream URLs | until YouTube's `expire`, minus 15 min | If a cached URL fails (e.g. network changed), the player extracts again |
| Similar songs (YouTube mix / related) | 7 days | Related songs from extraction are stored for free |
| Spotify → YouTube matches | 60 days | |
| Spotify imports | 1 day | |
| Node info (`/v4/info`) | 10 min (sources: a day) | "Check all" bypasses it |

Requests to one node also go out one at a time, at least 400 ms apart, and a node that answers 429 is left alone for 45 s (or its `Retry-After`).

## Recommendations (`core/taste/`)

Candidates come from YouTube's Mix for a few seed songs (straight from YouTube through the extractor, not the node). Mixes of "Topic" song uploads need YouTube's cookie consent in some countries, so the regular video upload's Mix is used instead. `Taste.score` weighs how strongly the source suggests the song (35 %), artist affinity (30 %), words in titles they like (15 %) and genre (10 %), adds a little for unheard artists, and subtracts for songs they skip, songs played in the last day, covers / sped-up / nightcore versions they don't otherwise play, and hour-long compilations. `Taste.rank` merges re-uploads of the same song, drops versions of songs already in the playlist, and keeps at most two songs per artist.

## Spotify import

Paste a playlist or album link (Library → Import). A Lavalink node with LavaSrc reads the whole list in one request; without one the public embed page is read (first 100 songs). Songs are played through their best YouTube match, which is then remembered.

## Lavalink nodes

Built-in public nodes live in `core/engine/LavalinkNodes.kt` (`LavalinkPresets`). They're run by volunteers: they see what you search, rate-limit bursts (the app waits for 1 s without typing and loads the home feed one row at a time) and can disappear, which is why the app checks them (onboarding, Profile → Check all) and skips the ones that are down. Audio always comes from the extractor on the device (see above).

| Preset | Host | Search |
| --- | --- | --- |
| Serenetia | `lavalinkv4.serenetia.com:443` (HTTPS) | `auto` = YouTube Music + SoundCloud (its SoundCloud search is broken right now) |

Live checks (network): `./gradlew :app:testDebugUnitTest -PliveNodes --tests '*LavalinkNodesTest*' --tests '*LiveExtractorTest*'`. The extractor test searches on the node, then extracts that track's audio.

## Build

Requirements: JDK 17+ and the Android SDK (platform 36). `local.properties` is not committed: set `sdk.dir` or `ANDROID_HOME`.

```bash
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:recordRoborazziDebug   # renders every screen with sample data into app/screenshots/
```

The screenshot tests (`app/src/test/.../ScreenshotTest.kt`) use Robolectric + Roborazzi, so no emulator is needed.

## Layout

```
app/src/main/java/com/nopethedev/riftsolo/
  core/Models.kt              Track, Playlist, genres, engine choice
  core/engine/                Engine interface + chain, NewPipeEngine, LavalinkEngine + nodes, Components (onboarding checks)
  core/data/                  SettingsStore (DataStore), LibraryStore (liked / history / playlists as JSON)
  core/player/                PlaybackService (Media3), PlayerController (queue, resolve-on-play, retry on expired URLs)
  ui/                         Theme (Rift palette), onboarding, home, search, library, profile, player, root scaffold
```

## License

GPL-3.0, see [LICENSE](LICENSE).
