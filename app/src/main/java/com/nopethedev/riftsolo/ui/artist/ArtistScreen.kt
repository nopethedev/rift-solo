package com.nopethedev.riftsolo.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.taste.Album
import com.nopethedev.riftsolo.core.taste.Artist
import com.nopethedev.riftsolo.ui.components.Artwork
import com.nopethedev.riftsolo.ui.components.CoverCard
import com.nopethedev.riftsolo.ui.components.Shimmer
import com.nopethedev.riftsolo.ui.components.TrackRow
import com.nopethedev.riftsolo.ui.theme.Rift

data class ArtistPageState(
    val artist: Artist,
    val following: Boolean = false,
    val loading: Boolean = true,
    val top: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    /** "Fans also like". */
    val related: List<Artist> = emptyList(),
    val error: String? = null,
)

data class AlbumPageState(val album: Album, val loading: Boolean = true, val tracks: List<Track> = emptyList(), val error: String? = null)

class ArtistPageActions(
    val back: () -> Unit = {},
    val follow: () -> Unit = {},
    val play: (List<Track>, Int) -> Unit = { _, _ -> },
    val like: (Track) -> Unit = {},
    val openAlbum: (Album) -> Unit = {},
    val openArtist: (Artist) -> Unit = {},
)

@Composable
private fun Header(title: String, subtitle: String, image: String?, seed: String, circle: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF3B2470), Rift.Bg)))) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }
            Artwork(image, seed, 180.dp, Modifier, corner = if (circle) 90.dp else 10.dp)
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PlayButtons(tracks: List<Track>, play: (List<Track>, Int) -> Unit, extra: @Composable () -> Unit = {}) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        extra()
        IconButton({ if (tracks.isNotEmpty()) play(tracks.shuffled(), 0) }, enabled = tracks.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, "Shuffle") }
        FilledIconButton({ if (tracks.isNotEmpty()) play(tracks, 0) }, Modifier.size(56.dp), enabled = tracks.isNotEmpty(), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Rift.Accent)) {
            Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(32.dp), tint = Color.White)
        }
    }
}

@Composable
fun ArtistScreen(state: ArtistPageState, actions: ArtistPageActions, playingId: String?, likedIds: Set<String>, contentPadding: PaddingValues) {
    val fans = state.artist.fans.takeIf { it > 0 }?.let { "%,d fans".format(it) }.orEmpty()
    LazyColumn(Modifier.fillMaxSize().background(Rift.Bg), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
        item {
            Header(state.artist.name, fans, state.artist.pictureUrl, state.artist.name, circle = true, onBack = actions.back) {
                PlayButtons(state.top, actions.play) {
                    if (state.following) OutlinedButton(actions.follow, shape = RoundedCornerShape(50)) { Text("Following") }
                    else Button(actions.follow, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF111111))) { Text("Follow") }
                }
            }
        }
        if (state.loading && state.top.isEmpty()) items(5) { Shimmer(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp)) }
        if (state.top.isNotEmpty()) {
            item { SectionTitle("Popular") }
            itemsIndexed(state.top.take(10), key = { _, t -> "top" + t.id }) { i, t ->
                TrackRow(t, { actions.play(state.top, i) }, playing = t.id == playingId, liked = t.id in likedIds, onLike = { actions.like(t) }, index = i + 1)
            }
        }
        if (state.albums.isNotEmpty()) item {
            SectionTitle("Albums")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(state.albums, key = { "al" + it.deezerId }) { a -> CoverCard(a.title, listOf(a.year, a.type.replaceFirstChar(Char::uppercase)).filter { it.isNotBlank() }.joinToString(" · "), "al${a.deezerId}", a.coverUrl, { actions.openAlbum(a) }) }
            }
        }
        if (state.related.isNotEmpty()) item {
            SectionTitle("Fans also like")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(state.related.take(15), key = { "ar" + it.name }) { a -> CoverCard(a.name, "Artist", a.name, a.pictureUrl, { actions.openArtist(a) }, size = 120.dp, circle = true) }
            }
        }
        if (state.error != null && state.top.isEmpty()) item { Text(state.error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun AlbumScreen(state: AlbumPageState, actions: ArtistPageActions, playingId: String?, likedIds: Set<String>, contentPadding: PaddingValues, onArtist: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(Rift.Bg), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
        item {
            val a = state.album
            Header(a.title, listOf(a.artist, a.year).filter { it.isNotBlank() }.joinToString(" · "), a.coverUrl, "al${a.deezerId}", circle = false, onBack = actions.back) {
                PlayButtons(state.tracks, actions.play) { OutlinedButton(onArtist, shape = RoundedCornerShape(50)) { Text(a.artist, maxLines = 1) } }
            }
        }
        if (state.loading && state.tracks.isEmpty()) items(6) { Shimmer(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp)) }
        itemsIndexed(state.tracks, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, { actions.play(state.tracks, i) }, playing = t.id == playingId, liked = t.id in likedIds, onLike = { actions.like(t) }, index = i + 1)
        }
        if (state.error != null && state.tracks.isEmpty()) item { Text(state.error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionTitle(t: String) = Text(t, Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)

