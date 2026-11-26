package com.nopethedev.riftsolo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nopethedev.riftsolo.core.engine.LavalinkNode
import com.nopethedev.riftsolo.core.engine.NodeParser
import com.nopethedev.riftsolo.core.engine.NodeStatus
import com.nopethedev.riftsolo.ui.theme.Rift

class NodeActions(
    val toggle: (String, Boolean) -> Unit = { _, _ -> },
    val delete: (String) -> Unit = {},
    val add: (List<LavalinkNode>) -> Unit = {},
    val check: () -> Unit = {},
)

/** Lavalink node list with health, on/off switches and an "Add node" dialog (form or pasted JSON). */
@Composable
fun NodeList(nodes: List<LavalinkNode>, status: Map<String, NodeStatus>, actions: NodeActions, modifier: Modifier = Modifier) {
    var adding by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nodes.forEach { n -> NodeRow(n, status[n.id], actions) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ adding = true }, shape = RoundedCornerShape(50)) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add node") }
            OutlinedButton(actions.check, shape = RoundedCornerShape(50)) { Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Check all") }
        }
    }
    if (adding) AddNodeDialog(onDismiss = { adding = false }, onAdd = { actions.add(it); adding = false })
}

@Composable
private fun NodeRow(n: LavalinkNode, st: NodeStatus?, actions: NodeActions) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Rift.Surface).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Rift.Surface2), contentAlignment = Alignment.Center) {
            Icon(if (n.preset) Icons.Rounded.Public else Icons.Rounded.Dns, null, tint = if (n.enabled) Rift.AccentLight else Rift.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Text(if (n.preset) "PUBLIC" else "CUSTOM", style = MaterialTheme.typography.labelSmall, color = if (n.preset) Rift.AccentPale else Color(0xFF7DD3FC),
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background((if (n.preset) Rift.Accent else Color(0xFF0EA5E9)).copy(alpha = 0.22f)).padding(horizontal = 5.dp, vertical = 1.dp))
            }
            Text(n.host + if (n.port != 443 && n.port != 80) ":${n.port}" else "", style = MaterialTheme.typography.bodySmall, color = Rift.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val (dot, line) = when (st) {
                null -> Rift.Muted to "Not checked yet"
                NodeStatus.Checking -> Rift.AccentPale to "Checking…"
                is NodeStatus.Online -> Rift.Success to "Online · v${st.version} · ${st.latencyMs} ms"
                is NodeStatus.Offline -> Rift.Danger to "Offline · ${st.message}"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(line, style = MaterialTheme.typography.bodySmall, color = if (st is NodeStatus.Offline) Rift.Danger else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!n.preset) IconButton({ actions.delete(n.id) }) { Icon(Icons.Rounded.DeleteOutline, "Remove", tint = Rift.Muted) }
        Switch(n.enabled, { actions.toggle(n.id, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Rift.Accent))
    }
}

@Composable
fun AddNodeDialog(onDismiss: () -> Unit, onAdd: (List<LavalinkNode>) -> Unit) =
    Dialog(onDismiss) { AddNodeCard(onDismiss, onAdd) }

/** The dialog's content on its own, so it can be screenshot-tested without a separate window. */
@Composable
fun AddNodeCard(onDismiss: () -> Unit, onAdd: (List<LavalinkNode>) -> Unit, initialTab: Int = 0, initialJson: String = "") {
    var tab by remember { mutableIntStateOf(initialTab) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var password by remember { mutableStateOf("") }
    var secure by remember { mutableStateOf(true) }
    var pasted by remember { mutableStateOf(initialJson) }
    val parsed = remember(pasted) { runCatching { NodeParser.parse(pasted) } }
    val formNode = host.trim().takeIf { it.isNotEmpty() }?.let {
        val h = it.removePrefix("https://").removePrefix("http://").trimEnd('/')
        LavalinkNode(name = name.ifBlank { h }, host = h, port = port.toIntOrNull() ?: if (secure) 443 else 2333, password = password, secure = secure)
    }
    val result = if (tab == 0) listOfNotNull(formNode) else parsed.getOrDefault(emptyList())
    val colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Rift.Line, focusedBorderColor = Rift.Accent, unfocusedContainerColor = Rift.Bg, focusedContainerColor = Rift.Bg)

    Surface(shape = RoundedCornerShape(28.dp), color = Rift.Surface) {
        Column(Modifier.padding(24.dp)) {
            Text("Add a Lavalink node", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Column {
                SecondaryTabRow(tab, containerColor = Color.Transparent) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Details") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Paste JSON") })
                }
                Spacer(Modifier.height(12.dp))
                if (tab == 0) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name (optional)") }, singleLine = true, colors = colors)
                    OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text("Host") }, placeholder = { Text("lavalink.example.com") }, singleLine = true, colors = colors)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.width(110.dp), label = { Text("Port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = colors)
                        Spacer(Modifier.weight(1f))
                        Text("HTTPS", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        Switch(secure, { secure = it; if (it && port == "2333") port = "443" else if (!it && port == "443") port = "2333" }, colors = SwitchDefaults.colors(checkedTrackColor = Rift.Accent))
                    }
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = colors)
                } else {
                    OutlinedTextField(
                        pasted, { pasted = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        placeholder = { Text("{\n  \"host\": \"…\",\n  \"port\": 443,\n  \"password\": \"…\",\n  \"secure\": true\n}", fontFamily = FontFamily.Monospace) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), colors = colors,
                    )
                    Spacer(Modifier.height(6.dp))
                    val msg = when {
                        pasted.isBlank() -> "One node object, a list of them, or a URL."
                        parsed.isFailure -> "That isn't valid node JSON."
                        result.isEmpty() -> "No host found."
                        else -> "Found ${result.size}: ${result.joinToString { it.host }}"
                    }
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = if (parsed.isFailure) Rift.Danger else if (result.isNotEmpty()) Rift.Success else Rift.Muted)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss) { Text("Cancel") }
                TextButton({ onAdd(result) }, enabled = result.isNotEmpty()) { Text("Add") }
            }
        }
    }
}
