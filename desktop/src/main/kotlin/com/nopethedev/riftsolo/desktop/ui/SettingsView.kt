package com.nopethedev.riftsolo.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nopethedev.riftsolo.core.EngineChoice
import com.nopethedev.riftsolo.core.engine.NodeParser
import com.nopethedev.riftsolo.core.sync.SyncManager
import com.nopethedev.riftsolo.desktop.BuildInfo
import com.nopethedev.riftsolo.desktop.DesktopState
import kotlinx.coroutines.launch

@Composable
fun SettingsView(app: DesktopState) {
    val s by app.settings.collectAsState()
    val account by app.accounts.account.collectAsState()
    val sync by app.sync.state.collectAsState()
    val taste by app.taste.collectAsState()
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableStateOf(-1L) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp)) {
        item { TopBar(app, "Settings") }
        item {
            Group("Account") {
                if (!account.signedIn) {
                    Text("Sign up to keep your library, playlists and taste in sync with your phone.", color = Rift.Muted)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button({ app.openAuth(true) }, colors = ButtonDefaults.buttonColors(containerColor = Rift.Accent)) { Text("Sign up") }
                        OutlinedButton({ app.openAuth(false) }) { Text("Sign in") }
                    }
                } else {
                    Text(account.email.orEmpty(), fontWeight = FontWeight.SemiBold)
                    Text(when {
                        sync is SyncManager.State.Syncing -> "Syncing…"
                        account.lastError != null -> account.lastError!!
                        account.lastSyncAt > 0 -> "Synced ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(account.lastSyncAt))} · ${account.serverUrl.removePrefix("https://")}"
                        else -> "Not synced yet"
                    }, color = if (account.lastError != null) Rift.Danger else Rift.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton({ scope.launch { app.sync.sync() } }) { Text("Sync now") }
                        TextButton({ scope.launch { app.sync.signOut() } }) { Text("Sign out") }
                        TextButton({ scope.launch { runCatching { app.sync.deleteAccount() } } }) { Text("Delete account", color = Rift.Danger) }
                    }
                }
                Toggle("Share cache anonymously", "Search results and song links are shared with other Rift Solo users (no account or history attached), so busy Lavalink nodes are asked less.", account.shareCache) { app.sync.setShareCache(it) }
            }
        }
        item {
            Group("Discord") {
                Toggle("Show what you're listening to", "Rich presence: \"Listening to\" with the song, artist, cover and time. Discord has to be running on this computer.", s.discordPresence) { on -> app.updateSettings { it.copy(discordPresence = on) } }
                Text(app.discord.status, style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
            }
        }
        item {
            Group("Playback") {
                Toggle("Allow all songs", "You confirm you may listen to what the sources return. Needed to play.", s.allowAllSongs) { on -> app.updateSettings { it.copy(allowAllSongs = on) } }
                Text("Search engine", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EngineChoice.LAVALINK to "Lavalink", EngineChoice.AUTO to "Auto", EngineChoice.PHONE to "This computer only").forEach { (e, label) ->
                        if (s.engine == e) Button({}, colors = ButtonDefaults.buttonColors(containerColor = Rift.Accent)) { Text(label) } else OutlinedButton({ app.updateSettings { it.copy(engine = e) } }) { Text(label) }
                    }
                }
            }
        }
        item {
            Group("Lavalink nodes") {
                s.nodes.forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(n.name); Text(n.url, style = MaterialTheme.typography.bodySmall, color = Rift.Muted) }
                        Switch(n.enabled, { on -> app.updateSettings { st -> st.copy(nodes = st.nodes.map { if (it.id == n.id) it.copy(enabled = on) else it }) } }, colors = SwitchDefaults.colors(checkedTrackColor = Rift.Accent))
                    }
                }
                var add by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(add, { add = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Add a node: host:port password, or a JSON list") })
                    Spacer(Modifier.width(8.dp))
                    TextButton({ val n = NodeParser.parse(add); if (n.isNotEmpty()) { app.updateSettings { it.copy(nodes = it.nodes + n) }; add = "" } }) { Text("Add") }
                }
            }
        }
        item {
            Group("Your taste") {
                if (taste.topArtists.isEmpty()) Text("Play, like and skip a few songs and this fills in.", color = Rift.Muted)
                else Text("Top artists: " + taste.topArtists.take(8).joinToString(", ") { it.first.split(" ").joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) } }, color = Rift.Muted)
            }
        }
        item {
            Group("Cache") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (cacheBytes < 0) "Searches, similar songs and stream links are kept so busy nodes aren't asked twice." else "%.1f MB".format(cacheBytes / 1048576.0), Modifier.weight(1f), color = Rift.Muted)
                    TextButton({ scope.launch { cacheBytes = app.cache.sizeBytes() } }) { Text("Size") }
                    TextButton({ scope.launch { app.cache.clear(); cacheBytes = app.cache.sizeBytes() } }) { Text("Clear") }
                }
            }
        }
        item { Text("Rift Solo ${BuildInfo.version} for desktop", Modifier.padding(vertical = 24.dp), color = Rift.Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(12.dp)).background(Rift.Surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(sub, style = MaterialTheme.typography.bodySmall, color = Rift.Muted) }
        Switch(on, set, colors = SwitchDefaults.colors(checkedTrackColor = Rift.Accent))
    }
}
