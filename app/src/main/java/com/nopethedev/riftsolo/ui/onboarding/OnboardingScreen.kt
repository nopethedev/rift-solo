package com.nopethedev.riftsolo.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.graphicsLayer
import com.nopethedev.riftsolo.ui.components.GeneratedArt
import com.nopethedev.riftsolo.ui.components.Logo
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ButtonDefaults
import com.nopethedev.riftsolo.ui.components.Spinner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nopethedev.riftsolo.core.EngineChoice
import com.nopethedev.riftsolo.core.GENRES
import com.nopethedev.riftsolo.core.engine.ComponentId
import com.nopethedev.riftsolo.core.engine.ComponentState
import com.nopethedev.riftsolo.core.engine.LavalinkNode
import com.nopethedev.riftsolo.core.engine.LavalinkPresets
import com.nopethedev.riftsolo.core.engine.NodeStatus
import com.nopethedev.riftsolo.ui.components.NodeActions
import com.nopethedev.riftsolo.ui.components.NodeList
import com.nopethedev.riftsolo.ui.components.AVATAR_COLORS
import com.nopethedev.riftsolo.ui.components.AVATAR_ICONS
import com.nopethedev.riftsolo.ui.components.Avatar
import com.nopethedev.riftsolo.ui.components.RMark
import com.nopethedev.riftsolo.ui.components.Wordmark
import com.nopethedev.riftsolo.ui.theme.Rift

data class OnboardingState(
    val step: Int = 0,
    val name: String = "",
    val avatar: String = "headphones:violet",
    val genres: Set<String> = emptySet(),
    val engine: EngineChoice = EngineChoice.LAVALINK,
    val nodes: List<LavalinkNode> = LavalinkPresets.nodes,
    val nodeStatus: Map<String, NodeStatus> = emptyMap(),
    val components: Map<ComponentId, ComponentState> = emptyMap(),
    val installing: Boolean = false,
    val allowAllSongs: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val notificationsAllowed: Boolean = false,
    /** Brand that kills background apps on its own (Xiaomi, Samsung...): show the extra hint. */
    val manufacturer: String? = null,
) {
    val usesLavalink get() = engine != EngineChoice.PHONE
    /** The extractor check has run (a failure is shown but doesn't trap the user in onboarding). */
    val setupDone get() = (components[ComponentId.EXTRACTOR] is ComponentState.Ready || components[ComponentId.EXTRACTOR] is ComponentState.Failed) && !installing
    val canContinue get() = when (step) {
        1 -> name.isNotBlank()
        2 -> genres.size >= 3
        3 -> !usesLavalink || nodes.any { it.enabled }
        LAST_STEP -> setupDone && allowAllSongs
        else -> true
    }
}

class OnboardingActions(
    val next: () -> Unit = {}, val back: () -> Unit = {}, val name: (String) -> Unit = {}, val avatar: (String) -> Unit = {},
    val genre: (String) -> Unit = {}, val engine: (EngineChoice) -> Unit = {}, val nodes: NodeActions = NodeActions(),
    val install: () -> Unit = {}, val finish: () -> Unit = {}, val allowAllSongs: (Boolean) -> Unit = {},
    val requestBattery: () -> Unit = {}, val requestNotifications: () -> Unit = {}, val openAppSettings: () -> Unit = {},
)

const val ONBOARDING_STEPS = 6
private const val LAST_STEP = ONBOARDING_STEPS - 1
private const val BACKGROUND_STEP = 4

@Composable
fun OnboardingScreen(state: OnboardingState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Rift.Bg)) {
        if (state.step == 0) CoverWall(Modifier.fillMaxWidth().fillMaxHeight(0.62f))
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            if (state.step > 0) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 24.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(actions.back, enabled = !state.installing) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Rift.Line)) {
                        Box(Modifier.fillMaxWidth(state.step / LAST_STEP.toFloat()).fillMaxHeight().clip(CircleShape).background(Color.White))
                    }
                }
            }
            AnimatedContent(state.step, Modifier.weight(1f), transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir / 6 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 6 } + fadeOut())
            }, label = "step") { step ->
                if (step == 0) Welcome()
                else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    when (step) {
                        1 -> NameStep(state, actions)
                        2 -> TasteStep(state, actions)
                        3 -> SourcesStep(state, actions)
                        BACKGROUND_STEP -> BackgroundStep(state, actions)
                        else -> LastStep(state, actions)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            BottomButtons(state, actions)
        }
    }
}

@Composable
private fun BottomButtons(state: OnboardingState, actions: OnboardingActions) {
    val needed = 3 - state.genres.size
    val askBattery = state.step == BACKGROUND_STEP && !state.batteryUnrestricted
    val label = when {
        state.step == 0 -> "Get started"
        state.step == 2 && needed > 0 -> "Pick $needed more"
        askBattery -> "Allow background playback"
        state.step == LAST_STEP -> when { state.installing -> "Checking sources…"; !state.setupDone -> "Check again"; else -> "Start listening" }
        else -> "Continue"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Button(
            onClick = {
                when {
                    askBattery -> actions.requestBattery()
                    state.step == LAST_STEP -> if (state.setupDone) actions.finish() else actions.install()
                    else -> actions.next()
                }
            },
            enabled = state.canContinue || askBattery || (state.step == LAST_STEP && !state.installing && !state.setupDone),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0B0B12), disabledContainerColor = Rift.Surface2, disabledContentColor = Rift.Muted),
        ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        if (askBattery) TextButton(actions.next, Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Not now", color = Rift.Muted) }
    }
}

/** Welcome background: a tilted wall of album covers fading into the page. */
@Composable
private fun CoverWall(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(0.dp))) {
        Column(
            Modifier.fillMaxWidth().graphicsLayer { rotationZ = -14f; scaleX = 1.45f; scaleY = 1.45f; translationY = -40f },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (r in 0 until 6) {
                Row(Modifier.fillMaxWidth().offset(x = if (r % 2 == 0) 0.dp else (-40).dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0 until 5) GeneratedArt("wall-$r-$c", Modifier.size(96.dp).clip(RoundedCornerShape(6.dp)))
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Rift.Bg.copy(alpha = 0.25f), Rift.Bg.copy(alpha = 0.55f), Rift.Bg))))
    }
}

@Composable
private fun Welcome() {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Bottom) {
        Logo(30.dp, 22.sp)
        Spacer(Modifier.height(20.dp))
        Text("Search anything.\nPlay it right here.", fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(12.dp))
        Text("The pocket-sized Rift. No server, no account.", color = Rift.Muted, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Heading(text: String, sub: String? = null) {
    Spacer(Modifier.height(20.dp))
    Text(text, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
    if (sub != null) {
        Spacer(Modifier.height(8.dp))
        Text(sub, color = Rift.Muted)
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun NameStep(state: OnboardingState, actions: OnboardingActions) {
    Heading("What should we call you?")
    TextField(
        state.name, actions.name, Modifier.fillMaxWidth(), singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        placeholder = { Text("Your name", style = MaterialTheme.typography.headlineSmall, color = Rift.Line) },
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.White, unfocusedIndicatorColor = Rift.Line, cursorColor = Color.White),
    )
    Spacer(Modifier.height(36.dp))
    Text("Avatar", style = MaterialTheme.typography.labelLarge, color = Rift.Muted)
    Spacer(Modifier.height(12.dp))
    val (icon, color) = state.avatar.split(":").let { it[0] to it.getOrElse(1) { "violet" } }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AVATAR_ICONS.keys.forEach { k ->
            Box(Modifier.size(60.dp).border(2.dp, if (k == icon) Color.White else Color.Transparent, CircleShape).padding(4.dp).clip(CircleShape).clickable { actions.avatar("$k:$color") }) { Avatar("$k:$color", 52.dp) }
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AVATAR_COLORS.forEach { (k, c) ->
            Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.first, c.second))).border(2.dp, if (k == color) Color.White else Color.Transparent, CircleShape).clickable { actions.avatar("$icon:$k") })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasteStep(state: OnboardingState, actions: OnboardingActions) {
    Heading("Pick 3 or more genres you like.", "Your home feed starts here, then learns from what you play and skip.")
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(18.dp), maxItemsInEachRow = 3) {
        GENRES.forEach { g ->
            val on = g.id in state.genres
            Column(Modifier.width(96.dp).clip(RoundedCornerShape(8.dp)).clickable { actions.genre(g.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(92.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.linearGradient(listOf(Color(g.colors.first), Color(g.colors.second)))).graphicsLayer { alpha = if (on) 1f else 0.85f })
                    GeneratedArt("genre-${g.id}", Modifier.fillMaxSize().clip(CircleShape).graphicsLayer { alpha = 0.35f })
                    if (on) Box(Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, Modifier.size(17.dp), tint = Color.Black) }
                }
                Spacer(Modifier.height(8.dp))
                Text(g.label, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Color.White else Rift.Muted)
            }
        }
    }
}

@Composable
private fun SourcesStep(state: OnboardingState, actions: OnboardingActions) {
    Heading("Where should we look for music?", "Rift Solo searches through Lavalink nodes. A public one is set up for you.")
    if (state.usesLavalink) {
        NodeList(state.nodes, state.nodeStatus, actions.nodes)
        Spacer(Modifier.height(12.dp))
        Text("Public nodes are shared, so they can be slow or busy at times. Results are cached on your phone to go easy on them. Your own Rift node works too.", style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
        Spacer(Modifier.height(20.dp))
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { actions.engine(if (state.usesLavalink) EngineChoice.PHONE else EngineChoice.LAVALINK) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Search from this phone instead", fontWeight = FontWeight.SemiBold)
            Text("Skips the nodes and asks YouTube directly. Less reliable.", style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
        }
        Switch(!state.usesLavalink, { actions.engine(if (it) EngineChoice.PHONE else EngineChoice.LAVALINK) }, colors = SwitchDefaults.colors(checkedTrackColor = Rift.Accent))
    }
}

@Composable
private fun BackgroundStep(state: OnboardingState, actions: OnboardingActions) {
    Heading("Keep the music playing", "Android shuts apps down in the background to save battery, which can stop a song as soon as your screen turns off. Let Rift Solo run unrestricted so that doesn't happen.")
    PermissionRow(Icons.Rounded.BatteryChargingFull, "Run in the background", "No battery restrictions for Rift Solo", state.batteryUnrestricted, actions.requestBattery)
    HorizontalDivider(color = Rift.Line)
    PermissionRow(Icons.Rounded.Notifications, "Player notification", "Play, pause and skip from the lock screen", state.notificationsAllowed, actions.requestNotifications)
    if (state.manufacturer != null) {
        Spacer(Modifier.height(20.dp))
        Text("${state.manufacturer} phones have their own battery saver on top of Android's. If music still stops, open the app settings and set Battery to \"No restrictions\" (and turn on Autostart if you see it).", style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
        TextButton(actions.openAppSettings, contentPadding = PaddingValues(0.dp)) { Text("Open app settings", color = Rift.AccentLight) }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, sub: String, granted: Boolean, onAllow: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (granted) Rift.Success else Color.White)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
        }
        if (granted) Text("On", color = Rift.Success, fontWeight = FontWeight.SemiBold)
        else OutlinedButton(onAllow, shape = CircleShape, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Allow") }
    }
}

@Composable
private fun LastStep(state: OnboardingState, actions: OnboardingActions) {
    Heading("One last thing")
    val ids = if (state.usesLavalink) ComponentId.entries else listOf(ComponentId.EXTRACTOR)
    ids.forEach { id -> StatusLine(id, state.components[id] ?: ComponentState.Idle) }
    Spacer(Modifier.height(24.dp))
    ConsentCard(state.allowAllSongs, actions.allowAllSongs)
}

@Composable
private fun StatusLine(id: ComponentId, s: ComponentState) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (s) {
                is ComponentState.Ready -> Icon(Icons.Rounded.CheckCircle, null, tint = Rift.Success, modifier = Modifier.size(20.dp))
                is ComponentState.Failed -> Icon(Icons.Rounded.ErrorOutline, null, tint = Rift.Danger, modifier = Modifier.size(20.dp))
                is ComponentState.Working, ComponentState.Idle -> Spinner(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                ComponentState.Skipped -> Icon(Icons.Rounded.RemoveCircleOutline, null, tint = Rift.Muted, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(if (id == ComponentId.EXTRACTOR) "YouTube" else "Lavalink", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(84.dp))
        val (line, color) = when (s) {
            ComponentState.Idle -> "Waiting" to Rift.Muted
            ComponentState.Skipped -> "Not used" to Rift.Muted
            is ComponentState.Working -> "Checking…" to Rift.Muted
            is ComponentState.Ready -> s.detail to Rift.Muted
            is ComponentState.Failed -> s.message to Rift.Danger
        }
        Text(line, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2, modifier = Modifier.weight(1f))
    }
}

const val CONSENT_TEXT = "Songs come from public sources like YouTube and SoundCloud, and many are copyrighted. " +
    "By turning this on you confirm you're allowed to listen to them where you live. Nothing is uploaded or shared."

/** Rift's "attested" licence policy, as a checkbox: required before anything plays. */
@Composable
fun ConsentCard(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 6.dp)) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color.Black, uncheckedColor = Rift.Muted))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Text("Allow all songs", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(CONSENT_TEXT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ComponentRow(id: ComponentId, s: ComponentState, trailing: @Composable (() -> Unit)? = null) {
    val icon = when (id) { ComponentId.EXTRACTOR -> Icons.Rounded.Memory; ComponentId.LAVALINK -> Icons.Rounded.Dns }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rift.Surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Rift.Surface2), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Rift.AccentLight) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(id.title, fontWeight = FontWeight.SemiBold)
                val (line, color) = when (s) {
                    ComponentState.Idle -> id.description to MaterialTheme.colorScheme.onSurfaceVariant
                    ComponentState.Skipped -> "Not used" to MaterialTheme.colorScheme.onSurfaceVariant
                    is ComponentState.Working -> s.label to Rift.AccentPale
                    is ComponentState.Ready -> s.detail to Rift.Success
                    is ComponentState.Failed -> s.message to Rift.Danger
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            when (s) {
                is ComponentState.Working -> Spinner(Modifier.size(22.dp))
                is ComponentState.Ready -> Icon(Icons.Rounded.CheckCircle, null, tint = Rift.Success)
                is ComponentState.Failed -> Icon(Icons.Rounded.ErrorOutline, null, tint = Rift.Danger)
                ComponentState.Skipped -> Icon(Icons.Rounded.RemoveCircleOutline, null, tint = Rift.Muted)
                ComponentState.Idle -> Icon(Icons.Rounded.Download, null, tint = Rift.Muted)
            }
            trailing?.invoke()
        }
        if (s is ComponentState.Working) {
            Spacer(Modifier.height(12.dp))
            if (s.progress != null) LinearProgressIndicator(progress = { s.progress }, Modifier.fillMaxWidth().clip(CircleShape))
            else if (androidx.compose.ui.platform.LocalInspectionMode.current) LinearProgressIndicator(progress = { 0.4f }, Modifier.fillMaxWidth().clip(CircleShape)) else LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Rift.Line, focusedBorderColor = Rift.Accent, unfocusedContainerColor = Rift.Surface, focusedContainerColor = Rift.Surface)

@Composable
fun SmallTextButton(text: String, onClick: () -> Unit) = TextButton(onClick, colors = ButtonDefaults.textButtonColors(contentColor = Rift.AccentLight)) { Text(text) }
