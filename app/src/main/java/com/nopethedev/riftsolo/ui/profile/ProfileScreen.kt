package com.nopethedev.riftsolo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nopethedev.riftsolo.core.EngineChoice
import com.nopethedev.riftsolo.core.GENRES
import com.nopethedev.riftsolo.core.SourceProvider
import com.nopethedev.riftsolo.core.engine.ComponentId
import com.nopethedev.riftsolo.core.engine.ComponentState
import com.nopethedev.riftsolo.core.engine.LavalinkNode
import com.nopethedev.riftsolo.core.engine.NodeStatus
import com.nopethedev.riftsolo.ui.components.NodeActions
import com.nopethedev.riftsolo.ui.components.NodeList
import com.nopethedev.riftsolo.ui.components.Avatar
import com.nopethedev.riftsolo.ui.components.Logo
import com.nopethedev.riftsolo.ui.onboarding.ComponentRow
import com.nopethedev.riftsolo.ui.onboarding.SmallTextButton
import com.nopethedev.riftsolo.ui.theme.Rift
import com.nopethedev.riftsolo.core.taste.Taste
import com.nopethedev.riftsolo.core.taste.TasteProfile
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.CheckCircle

data class ProfileState(
    val name: String = "",
    val avatar: String = "headphones:violet",
    val genres: Set<String> = emptySet(),
    val liked: Int = 0,
    val playlists: Int = 0,
    val plays: Int = 0,
    val engine: EngineChoice = EngineChoice.LAVALINK,
    val providers: Set<String> = emptySet(),
    val nodes: List<LavalinkNode> = emptyList(),
    val nodeStatus: Map<String, NodeStatus> = emptyMap(),
    val components: Map<ComponentId, ComponentState> = emptyMap(),
    val allowAllSongs: Boolean = false,
    val version: String = "0.4.1",
    val taste: TasteProfile = TasteProfile(),
    val batteryUnrestricted: Boolean = true,
    val notificationsAllowed: Boolean = true,
    val cacheBytes: Long = -1,
    val account: AccountUi = AccountUi(),
    val liquidGlass: Boolean = false,
)

class ProfileActions(
    val engine: (EngineChoice) -> Unit = {},
    val provider: (String, Boolean) -> Unit = { _, _ -> },
    val nodes: NodeActions = NodeActions(),
    val testExtractor: () -> Unit = {},
    val allowAllSongs: (Boolean) -> Unit = {},
    val editProfile: () -> Unit = {},
    val fixBattery: () -> Unit = {},
    val allowNotifications: () -> Unit = {},
    val clearCache: () -> Unit = {},
    val account: AccountActions = AccountActions(),
    val liquidGlass: (Boolean) -> Unit = {},
)

@Composable
fun ProfileScreen(state: ProfileState, actions: ProfileActions, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Column(
                Modifier.fillMaxWidth().drawBehind { drawRect(Brush.verticalGradient(listOf(Color(0xFF4C1D95), Rift.Bg))) }.statusBarsPadding().padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(state.avatar, 88.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PROFILE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(state.name.ifBlank { "Listener" }, style = MaterialTheme.typography.headlineMedium)
                        Text(GENRES.filter { it.id in state.genres }.joinToString(" · ") { it.label }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("${state.liked}", "Liked", Modifier.weight(1f)); Stat("${state.playlists}", "Playlists", Modifier.weight(1f)); Stat("${state.plays}", "Plays", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(actions.editProfile, shape = RoundedCornerShape(50)) { Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Edit profile") }
            }
        }
        item {
            Header(Icons.Rounded.AccountCircle, "Account")
            AccountCard(state.account, actions.account, Modifier.padding(horizontal = 16.dp))
        }
        item { TasteSection(state.taste) }
        item {
            Header(Icons.Rounded.AutoAwesome, "Appearance")
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Liquid glass")
                    Text("Glass navigation bar that bends what scrolls under it. Best on Android 13+; uses a little more battery.", style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
                }
                Switch(state.liquidGlass, actions.liquidGlass, colors = SwitchDefaults.colors(checkedTrackColor = Rift.Accent))
            }
        }
        item {
            Header(Icons.Rounded.BatteryChargingFull, "Background playback")
            Column(Modifier.padding(horizontal = 16.dp)) {
                SettingRow("Run in the background", if (state.batteryUnrestricted) "Unrestricted: music keeps playing with the screen off" else "Android may stop the music when the screen is off", state.batteryUnrestricted, actions.fixBattery)
                SettingRow("Player notification", if (state.notificationsAllowed) "Controls on the lock screen" else "Off: no lock-screen controls", state.notificationsAllowed, actions.allowNotifications)
            }
        }
        item {
            Header(Icons.Rounded.Tune, "Playback engine")
            Column(Modifier.padding(horizontal = 16.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val opts = listOf(EngineChoice.LAVALINK to "Lavalink", EngineChoice.AUTO to "Auto", EngineChoice.PHONE to "Phone only")
                    opts.forEachIndexed { i, (e, label) ->
                        SegmentedButton(state.engine == e, { actions.engine(e) }, SegmentedButtonDefaults.itemShape(i, opts.size),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = Rift.Accent.copy(alpha = 0.35f), activeContentColor = Color.White, inactiveContainerColor = Rift.Surface)) { Text(label) }
                    }
                }
                Text(
                    when (state.engine) {
                        EngineChoice.PHONE -> "Search YouTube Music and SoundCloud from this phone, without a node."
                        EngineChoice.AUTO -> "Search the Lavalink nodes first, fall back to searching from this phone if they're all down."
                        EngineChoice.LAVALINK -> "Recommended. Search only through the Lavalink nodes. Audio is extracted on the phone."
                    }, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = Rift.Muted,
                )
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow all songs")
                        Text("You confirm you may listen to what the sources return. Needed to play.", style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
                    }
                    Switch(state.allowAllSongs, actions.allowAllSongs, colors = SwitchDefaults.colors(checkedTrackColor = Rift.Accent))
                }
                Text("Sources for phone search", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                SourceProvider.entries.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label, Modifier.weight(1f))
                        Switch(p.id in state.providers, { actions.provider(p.id, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Rift.Accent))
                    }
                }
            }
        }
        if (state.engine != EngineChoice.PHONE) item {
            Header(Icons.Rounded.CloudSync, "Lavalink nodes")
            Column(Modifier.padding(horizontal = 16.dp)) {
                val online = state.nodes.count { it.enabled && state.nodeStatus[it.id] is NodeStatus.Online }
                Text("${state.nodes.count { it.enabled }} enabled · $online online. Searched in order, offline ones skipped.", style = MaterialTheme.typography.bodySmall, color = Rift.Muted, modifier = Modifier.padding(bottom = 10.dp))
                NodeList(state.nodes, state.nodeStatus, actions.nodes)
            }
        }
        item {
            Header(Icons.Rounded.Info, "Components")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ComponentRow(ComponentId.EXTRACTOR, state.components[ComponentId.EXTRACTOR] ?: ComponentState.Idle) { SmallTextButton("Test", actions.testExtractor) }
            }
        }
        item {
            Header(Icons.Rounded.Storage, "Cache")
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.cacheBytes < 0) "…" else formatBytes(state.cacheBytes))
                    Text("Searches, similar songs, Spotify matches and stream links are kept so busy nodes aren't asked twice.", style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
                }
                SmallTextButton("Clear", actions.clearCache)
            }
        }
        item {
            HorizontalDivider(Modifier.padding(top = 24.dp), color = Rift.Line)
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Logo(24.dp, 18.sp)
                Spacer(Modifier.height(6.dp))
                Text("Version ${state.version} · the lightweight, portable Rift", style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
            }
        }
    }
}

@Composable
private fun Header(icon: ImageVector, title: String) {
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = Rift.AccentLight)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.07f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
    }
}


@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TasteSection(t: TasteProfile) {
    Header(Icons.Rounded.Insights, "Your taste")
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (t.topArtists.isEmpty()) {
            Text("Play, like and skip a few songs and this fills in. Your home feed and suggestions are built from it.", style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
        } else {
            Text("Top artists", style = MaterialTheme.typography.labelLarge, color = Rift.Muted)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                t.topArtists.take(8).forEach { (a, _) ->
                    Text(a.split(" ").joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }, Modifier.clip(RoundedCornerShape(50)).background(Rift.Surface2).padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (t.topGenres.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Genres", style = MaterialTheme.typography.labelLarge, color = Rift.Muted)
            t.topGenres.take(5).forEach { (g, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Taste.genreLabel(g), Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(Rift.Surface2)) {
                        Box(Modifier.fillMaxWidth(v.toFloat().coerceIn(0.04f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(Rift.AccentLight))
                    }
                }
            }
        }
        if (t.signals > 0) Text("Learned from ${t.signals} likes, plays and skips", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
    }
}

@Composable
private fun SettingRow(title: String, sub: String, ok: Boolean, fix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = if (ok) Rift.Muted else Rift.Danger)
        }
        if (ok) Icon(Icons.Rounded.CheckCircle, null, tint = Rift.Success) else SmallTextButton("Fix", fix)
    }
}

private fun formatBytes(b: Long) = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.0f KB".format(b / 1024.0)
    else -> "%.1f MB".format(b / 1024.0 / 1024.0)
}
