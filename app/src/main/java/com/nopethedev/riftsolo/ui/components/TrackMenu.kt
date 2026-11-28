package com.nopethedev.riftsolo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.ui.theme.Rift

/** Opens the song menu for a track (long-press on any song). Null = no menu here (screenshots, pickers). */
val LocalTrackMenu = compositionLocalOf<((Track) -> Unit)?> { null }

/** The playlist the song rows on screen belong to, so the menu can offer "Remove from this playlist". */
val LocalPlaylistContext = compositionLocalOf<String?> { null }

data class TrackMenuState(
    val track: Track,
    val liked: Boolean = false,
    val following: Boolean = false,
    val excluded: Boolean = false,
    /** Set when opened from a playlist the song is in. */
    val playlistName: String? = null,
)

class TrackMenuActions(
    val playNext: () -> Unit = {},
    val addToQueue: () -> Unit = {},
    val startRadio: () -> Unit = {},
    val addToPlaylist: () -> Unit = {},
    val like: () -> Unit = {},
    val goToArtist: () -> Unit = {},
    val follow: () -> Unit = {},
    val exclude: () -> Unit = {},
    val removeFromPlaylist: (() -> Unit)? = null,
    val share: () -> Unit = {},
)

/** The rows of the song menu; the same list is used by the mobile sheet and the desktop dropdown. */
data class MenuEntry(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

fun trackMenuEntries(s: TrackMenuState, a: TrackMenuActions): List<List<MenuEntry>> {
    val artist = s.track.artist.replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
    return listOf(
        listOf(
            MenuEntry("Play next", Icons.Rounded.SkipNext, onClick = a.playNext),
            MenuEntry("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic, onClick = a.addToQueue),
            MenuEntry("Start radio", Icons.Rounded.Radio, onClick = a.startRadio),
        ),
        listOfNotNull(
            MenuEntry("Add to playlist", Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = a.addToPlaylist),
            MenuEntry(if (s.liked) "Remove from Liked Songs" else "Add to Liked Songs", if (s.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, onClick = a.like),
            a.removeFromPlaylist?.let { MenuEntry("Remove from ${s.playlistName ?: "this playlist"}", Icons.Rounded.PlaylistRemove, onClick = it) },
        ),
        listOf(
            MenuEntry("Go to $artist", Icons.Rounded.Person, onClick = a.goToArtist),
            MenuEntry(if (s.following) "Unfollow $artist" else "Follow $artist", if (s.following) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd, onClick = a.follow),
            MenuEntry(if (s.excluded) "Include in your taste again" else "Exclude from your taste", if (s.excluded) Icons.Rounded.TaskAlt else Icons.Rounded.Block, danger = !s.excluded, onClick = a.exclude),
            MenuEntry("Share", Icons.Rounded.Share, onClick = a.share),
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(state: TrackMenuState, actions: TrackMenuActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismiss, containerColor = Rift.Surface) { TrackMenuContent(state, actions, onDismiss) }
}

@Composable
fun TrackMenuContent(state: TrackMenuState, actions: TrackMenuActions, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    run {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(state.track.artworkUrl, state.track.id, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            trackMenuEntries(state, actions).forEach { group ->
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Rift.Surface2)
                group.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { e.onClick(); onDismiss() }.padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(e.icon, null, tint = if (e.danger) Rift.Danger else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(18.dp))
                        Text(e.label, color = if (e.danger) Rift.Danger else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
