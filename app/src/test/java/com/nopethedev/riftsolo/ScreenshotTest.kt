package com.nopethedev.riftsolo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.nopethedev.riftsolo.core.EngineChoice
import com.nopethedev.riftsolo.core.Playlist
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.data.Library
import com.nopethedev.riftsolo.core.data.Settings
import com.nopethedev.riftsolo.core.engine.ComponentId
import com.nopethedev.riftsolo.core.engine.ComponentState
import com.nopethedev.riftsolo.core.engine.LavalinkNode
import com.nopethedev.riftsolo.core.engine.LavalinkPresets
import com.nopethedev.riftsolo.core.engine.NodeStatus
import com.nopethedev.riftsolo.core.player.PlayerState
import com.nopethedev.riftsolo.ui.MainScaffold
import com.nopethedev.riftsolo.ui.Tab
import com.nopethedev.riftsolo.ui.home.HomeActions
import com.nopethedev.riftsolo.ui.home.HomeRow
import com.nopethedev.riftsolo.ui.home.HomeScreen
import com.nopethedev.riftsolo.ui.home.HomeState
import com.nopethedev.riftsolo.ui.library.CollectionScreen
import com.nopethedev.riftsolo.ui.library.LibraryActions
import com.nopethedev.riftsolo.ui.library.LibraryScreen
import com.nopethedev.riftsolo.ui.library.LibraryState
import com.nopethedev.riftsolo.ui.onboarding.OnboardingActions
import com.nopethedev.riftsolo.ui.onboarding.OnboardingScreen
import com.nopethedev.riftsolo.ui.onboarding.OnboardingState
import com.nopethedev.riftsolo.ui.player.NowPlayingScreen
import com.nopethedev.riftsolo.ui.player.PlayerActions
import com.nopethedev.riftsolo.ui.profile.ProfileActions
import com.nopethedev.riftsolo.ui.profile.ProfileScreen
import com.nopethedev.riftsolo.ui.profileState
import com.nopethedev.riftsolo.ui.search.SearchActions
import com.nopethedev.riftsolo.ui.search.SearchScreen
import com.nopethedev.riftsolo.ui.search.SearchState
import com.nopethedev.riftsolo.ui.theme.RiftSoloTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens with sample data (no network) into PNGs under app/screenshots/.
 * Run: ./gradlew :app:recordRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    private fun t(id: String, title: String, artist: String, d: Int, src: String = "youtube") = Track("$src:$id", title, artist, d, "https://example.com/$id", null, src)

    private val songs = listOf(
        t("a1", "Midnight Signal", "Solo Echoes", 204), t("a2", "Neon Drift", "Glass Coast", 238, "soundcloud"),
        t("a3", "Low Orbit", "Paper Moons", 187), t("a4", "Afterglow", "The Quiet Hour", 251),
        t("a5", "Salt & Static", "Wildwire", 199, "soundcloud"), t("a6", "Night Drive", "Neon Drift", 222),
        t("a7", "Cassette Heart", "Solo Echoes", 176), t("a8", "Glass Coast", "Low Orbit", 263, "soundcloud"),
    )
    private val settings = Settings(onboarded = true, name = "Alex", avatar = "headphones:violet", genres = setOf("synthwave", "lofi", "electronic"))
    private val library = Library(liked = songs.take(5), recent = songs.drop(2) + songs.take(2), playlists = listOf(Playlist("p1", "Late night coding", songs.take(4)), Playlist("p2", "Gym", songs.drop(3).take(3))), plays = 128)
    private val playing = PlayerState(queue = songs, index = 0, isPlaying = true, positionMs = 72_000, durationMs = 204_000)
    private val ready = mapOf<ComponentId, ComponentState>(ComponentId.EXTRACTOR to ComponentState.Ready("Ready · 640 ms"), ComponentId.LAVALINK to ComponentState.Skipped)

    private val nodes = LavalinkPresets.nodes + LavalinkNode(id = "home", name = "Rift server", host = "192.168.1.20", port = 2333, password = "x", secure = false)
    private val nodeStatus = mapOf<String, NodeStatus>("preset-serenetia" to NodeStatus.Online("4.5.4", 142, listOf("youtube", "spotify")), "home" to NodeStatus.Offline("Can't reach the Lavalink node: timeout"))

    private fun shot(name: String, content: @Composable () -> Unit) = captureRoboImage("screenshots/$name.png") { CompositionLocalProvider(LocalInspectionMode provides true) { RiftSoloTheme { content() } } }

    private fun main(name: String, tab: Tab, player: PlayerState = playing, body: @Composable (PaddingValues) -> Unit) = shot(name) {
        MainScaffold(tab, {}, player, liked = true, playerActions = PlayerActions(), nowPlaying = false, content = body)
    }

    @Test fun onboarding1Welcome() = shot("01_onboarding_welcome") { OnboardingScreen(OnboardingState(step = 0), OnboardingActions()) }
    @Test fun onboarding2Profile() = shot("02_onboarding_name") { OnboardingScreen(OnboardingState(step = 1, name = "Alex", avatar = "headphones:violet"), OnboardingActions()) }
    @Test fun onboarding3Taste() = shot("03_onboarding_taste") { OnboardingScreen(OnboardingState(step = 2, genres = setOf("synthwave", "lofi")), OnboardingActions()) }
    @Test fun onboarding4Engine() = shot("04_onboarding_sources") { OnboardingScreen(OnboardingState(step = 3, engine = EngineChoice.LAVALINK, nodes = nodes, nodeStatus = nodeStatus), OnboardingActions()) }
    @Test fun onboarding5Background() = shot("05_onboarding_background") {
        OnboardingScreen(OnboardingState(step = 4, batteryUnrestricted = false, notificationsAllowed = true, manufacturer = "Xiaomi"), OnboardingActions())
    }
    @Test fun onboarding6Done() = shot("06_onboarding_last") {
        OnboardingScreen(OnboardingState(step = 5, engine = EngineChoice.LAVALINK, allowAllSongs = true, components = ready + (ComponentId.LAVALINK to ComponentState.Ready("1 of 1 online · Lavalink 4.5.4"))), OnboardingActions())
    }

    @Test fun home() = main("07_home", Tab.HOME) { pad ->
        HomeScreen(HomeState("Alex", "headphones:violet", "Good evening", 5, library.recent, listOf(
            HomeRow("foryou", "Made for Alex", "Picked from what you play", songs.shuffled(java.util.Random(3))),
            HomeRow("synthwave", "Synthwave mix", "Made for Alex", songs.shuffled(java.util.Random(1))),
            HomeRow("lofi", "Lo-fi mix", "Made for Alex", songs.shuffled(java.util.Random(2))),
            HomeRow("electronic", "Electronic mix", "Made for Alex", loading = true),
        )), HomeActions(), pad)
    }
    @Test fun searchBrowse() = main("08_search_browse", Tab.SEARCH) { pad -> SearchScreen(SearchState(), SearchActions(), pad) }
    @Test fun searchResults() = main("09_search_results", Tab.SEARCH) { pad ->
        SearchScreen(SearchState(query = "synthwave", results = songs, playingId = songs[0].id, likedIds = setOf(songs[0].id, songs[3].id)), SearchActions(), pad)
    }
    @Test fun libraryTab() = main("10_library", Tab.LIBRARY) { pad -> LibraryScreen(LibraryState("headphones:violet", library.liked, library.recent, library.playlists + Playlist("p3", "Today's Top Hits", songs.take(6), importedFrom = "https://open.spotify.com/playlist/x")), LibraryActions(), pad) }
    @Test fun likedSongs() = main("11_liked_songs", Tab.LIBRARY) { pad ->
        CollectionScreen("Liked Songs", "Alex · 5 songs", library.liked, Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFFA78BFA))), songs[0].id, library.liked.map { it.id }.toSet(), {}, {}, {}, {}, pad)
    }
    private val taste = com.nopethedev.riftsolo.core.taste.Taste.build(library.copy(stats = songs.associate { it.id to com.nopethedev.riftsolo.core.data.ListenStat(plays = 3, completes = 2, lastAt = System.currentTimeMillis(), track = it) }), settings.genres)
    @Test fun profile() = main("12_profile", Tab.PROFILE) { pad ->
        ProfileScreen(profileState(settings.copy(engine = EngineChoice.LAVALINK, nodes = nodes, allowAllSongs = true), library, ready + (ComponentId.LAVALINK to ComponentState.Ready("1 of 2 online · Lavalink 4.5.4")), nodeStatus)
            .copy(taste = taste, batteryUnrestricted = false, cacheBytes = 2_400_000), ProfileActions(), pad)
    }
    @Test fun playlist() = main("15_playlist", Tab.LIBRARY) { pad ->
        CollectionScreen("Late night coding", "Alex · 4 songs", songs.take(4), Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))), songs[0].id, setOf(songs[1].id), {}, {}, {}, {}, pad,
            extras = com.nopethedev.riftsolo.ui.library.CollectionExtras(onAddSongs = {}, onRemove = {}, onRename = {}, onDelete = {}, recs = com.nopethedev.riftsolo.ui.RecState(tracks = songs.drop(4)), onRecAdd = {}))
    }
    @Test fun addSongs() = shot("16_add_songs") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(com.nopethedev.riftsolo.ui.theme.Rift.Surface).padding(top = 48.dp)) {
            com.nopethedev.riftsolo.ui.library.AddSongsContent("Late night coding", SearchState(query = "neon", results = songs.drop(1)), setOf(songs[2].id), emptyList(), {}, {}, {})
        }
    }
    @Test fun addNodeDialog() = shot("14_add_node") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(Color(0xCC000000)).padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            com.nopethedev.riftsolo.ui.components.AddNodeCard({}, {}, initialTab = 1, initialJson = "{\n  \"host\": \"lavalinkv4.serenetia.com\",\n  \"port\": 443,\n  \"password\": \"https://seretia.link/discord\",\n  \"secure\": true\n}")
        }
    }
    @Test fun nowPlaying() = shot("13_now_playing") { NowPlayingScreen(playing, liked = true, actions = PlayerActions()) }
}
