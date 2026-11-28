package com.nopethedev.riftsolo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nopethedev.riftsolo.ui.components.Spinner
import com.nopethedev.riftsolo.ui.onboarding.fieldColors
import com.nopethedev.riftsolo.ui.theme.Rift

data class AccountUi(
    val serverUrl: String = "",
    val email: String? = null,
    val name: String? = null,
    val syncing: Boolean = false,
    val lastSyncAt: Long = 0,
    val error: String? = null,
    val shareCache: Boolean = true,
) { val signedIn get() = email != null }

class AccountActions(
    val signUp: () -> Unit = {},
    val signIn: () -> Unit = {},
    val signOut: () -> Unit = {},
    val sync: () -> Unit = {},
    val deleteAccount: () -> Unit = {},
    val shareCache: (Boolean) -> Unit = {},
)

private fun ago(t: Long): String {
    if (t <= 0) return "never"
    val s = (System.currentTimeMillis() - t) / 1000
    return when { s < 60 -> "just now"; s < 3600 -> "${s / 60} min ago"; s < 86_400 -> "${s / 3600} h ago"; else -> "${s / 86_400} d ago" }
}

@Composable
fun AccountCard(a: AccountUi, actions: AccountActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rift.Surface).padding(16.dp)) {
        if (!a.signedIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudOff, null, tint = Rift.Muted)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Not signed in")
                    Text("Sign up to keep your library, playlists and taste on all your devices.", style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(actions.signUp, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Rift.Accent)) { Text("Sign up") }
                OutlinedButton(actions.signIn, shape = RoundedCornerShape(50)) { Text("Sign in") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (a.error == null) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff, null, tint = if (a.error == null) Rift.Success else Rift.Danger)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.email.orEmpty())
                    Text(
                        if (a.syncing) "Syncing…" else a.error ?: "Synced ${ago(a.lastSyncAt)}",
                        style = MaterialTheme.typography.bodySmall, color = if (a.error != null) Rift.Danger else Rift.Muted,
                    )
                }
                if (a.syncing) Spinner(Modifier.size(22.dp)) else TextButton(actions.sync) { Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Sync") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(actions.signOut) { Text("Sign out") }
                TextButton(actions.deleteAccount) { Text("Delete account", color = Rift.Danger) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Share cache anonymously")
                Text("Search results and song links are shared with other Rift Solo users (no account or history attached), so busy Lavalink nodes are asked less.", style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
            }
            Switch(a.shareCache, actions.shareCache, colors = SwitchDefaults.colors(checkedTrackColor = Rift.Accent))
        }
    }
}

/** Sign up / sign in form. [signUp] decides which. */
@Composable
fun AuthDialog(
    signUp: Boolean, serverUrl: String, busy: Boolean, error: String?,
    onSubmit: (server: String, email: String, password: String, name: String) -> Unit, onSwitch: () -> Unit, onDismiss: () -> Unit,
) {
    var server by remember { mutableStateOf(serverUrl) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, containerColor = Rift.Surface,
        icon = { Icon(Icons.Rounded.AccountCircle, null) },
        title = { Text(if (signUp) "Create your account" else "Sign in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(server, { server = it }, label = { Text("Server") }, placeholder = { Text("https://rift.example.com") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next))
                if (signUp) OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, supportingText = if (signUp) ({ Text("At least 8 characters") }) else null,
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors(), visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
                if (error != null) Text(error, color = Rift.Danger, style = MaterialTheme.typography.bodySmall)
                TextButton(onSwitch, enabled = !busy) { Text(if (signUp) "Have an account? Sign in" else "New here? Sign up", color = Rift.AccentLight) }
            }
        },
        confirmButton = {
            Button({ onSubmit(server, email, password, name) }, enabled = !busy && server.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Rift.Accent)) {
                if (busy) Spinner(Modifier.size(18.dp), Color.White, 2.dp) else Text(if (signUp) "Sign up" else "Sign in")
            }
        },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
