package com.nopethedev.riftsolo.core.engine

import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A Lavalink v4 node the app can search through. Field names follow the JSON that public node lists share. */
@Serializable
data class LavalinkNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 443,
    val password: String = "",
    val secure: Boolean = true,
    /** Comma separated search prefixes, tried together and merged (Rift's shared/lavalink-search.ts rules). */
    val searchPrefix: String = "auto",
    /** Shipped with the app (can be switched off, not deleted). */
    val preset: Boolean = false,
    val enabled: Boolean = true,
) {
    val url: String get() {
        val scheme = if (secure) "https" else "http"
        val defaultPort = (secure && port == 443) || (!secure && port == 80)
        return if (defaultPort) "$scheme://$host" else "$scheme://$host:$port"
    }
    val config: LavalinkNodeConfig get() = LavalinkNodeConfig(url, password, searchPrefix, id, name)
}

/**
 * Public nodes run by volunteers. They can come and go, so the app checks them and skips the ones that are down;
 * add more here (or from the app with "Add node").
 */
object LavalinkPresets {
    val nodes = listOf(
        LavalinkNode(
            id = "preset-serenetia",
            name = "Serenetia",
            host = "lavalinkv4.serenetia.com",
            port = 443,
            password = "https://seretia.link/discord",
            secure = true,
            // YouTube Music + SoundCloud, picked from what the node supports. A failing prefix doesn't hide the other.
            searchPrefix = "auto",
            preset = true,
        ),
    )

    /** Keeps the user's on/off choice for presets, adds presets shipped by a newer version, drops removed ones. */
    fun merge(saved: List<LavalinkNode>): List<LavalinkNode> {
        val byId = saved.associateBy { it.id }
        val presets = nodes.map { p -> byId[p.id]?.let { p.copy(enabled = it.enabled) } ?: p }
        return presets + saved.filterNot { it.preset }
    }
}

/**
 * Reads a node from what people paste: a JSON object like
 * `{"host": "...", "port": 443, "password": "...", "secure": true}`, an array of them, or a plain URL.
 */
object NodeParser {
    fun parse(text: String): List<LavalinkNode> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        return when {
            t.startsWith("[") -> JSONArray(t).let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::fromJson) } }
            t.startsWith("{") -> listOfNotNull(fromJson(JSONObject(t)))
            else -> listOfNotNull(fromUrl(t, ""))
        }
    }

    private fun fromJson(o: JSONObject): LavalinkNode? {
        val rawHost = o.optString("host").ifEmpty { o.optString("url").ifEmpty { o.optString("address") } }
        if (rawHost.isEmpty()) return null
        val password = o.optString("password").ifEmpty { o.optString("auth").ifEmpty { o.optString("authorization") } }
        if (rawHost.contains("://")) return fromUrl(rawHost, password)?.let { n -> o.optString("identifier").ifEmpty { o.optString("name") }.takeIf { it.isNotEmpty() }?.let { n.copy(name = it) } ?: n }
        val secure = if (o.has("secure")) o.optBoolean("secure") else o.optInt("port", 443) == 443
        val (host, portInHost) = rawHost.split(":").let { it[0] to it.getOrNull(1)?.toIntOrNull() }
        return LavalinkNode(
            name = o.optString("identifier").ifEmpty { o.optString("name") }.ifEmpty { host },
            host = host,
            port = o.optInt("port", portInHost ?: if (secure) 443 else 2333),
            password = password,
            secure = secure,
        )
    }

    private fun fromUrl(url: String, password: String): LavalinkNode? {
        val m = Regex("^(https?|wss?)://([^/:]+)(?::(\\d+))?").find(url.trim()) ?: return null
        val secure = m.groupValues[1] == "https" || m.groupValues[1] == "wss"
        val host = m.groupValues[2]
        return LavalinkNode(name = host, host = host, port = m.groupValues[3].toIntOrNull() ?: if (secure) 443 else 80, password = password, secure = secure)
    }
}
