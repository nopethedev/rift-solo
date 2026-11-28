package com.nopethedev.riftsolo.core.sync

import com.nopethedev.riftsolo.core.data.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ServerException(message: String, val status: Int = 0, val body: JsonObject? = null) : Exception(message)

@Serializable
data class ServerUser(val id: String, val email: String, val name: String = "", val createdAt: Long = 0)

/** REST client for rift-server (github.com/nopethedev/rift-server). */
class RiftServer(
    private val baseUrl: () -> String,
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build(),
) {
    /** The cache gets a short leash: a slow server must never make the app slower than going to the node itself. */
    private val quick = http.newBuilder().connectTimeout(1500, TimeUnit.MILLISECONDS).callTimeout(2500, TimeUnit.MILLISECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mediaJson = "application/json".toMediaType()

    private suspend fun call(method: String, path: String, body: JsonElement? = null, token: String? = null, client: OkHttpClient = http): JsonObject = withContext(Dispatchers.IO) {
        val base = baseUrl().trim().trimEnd('/').toHttpUrlOrNull() ?: throw ServerException("Set a server address first")
        val url = base.newBuilder().addPathSegments(path.substringBefore('?').trimStart('/')).apply {
            path.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.forEach { kv ->
                addQueryParameter(java.net.URLDecoder.decode(kv.substringBefore('='), "UTF-8"), java.net.URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8"))
            }
        }.build()
        val req = Request.Builder().url(url).method(method, body?.let { json.encodeToString(JsonElement.serializer(), it).toRequestBody(mediaJson) })
            .header("Accept", "application/json").apply { if (token != null) header("Authorization", "Bearer $token") }.build()
        try {
            client.newCall(req).execute().use { r ->
                val text = r.body.string()
                val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { JsonObject(emptyMap()) }
                if (!r.isSuccessful) throw ServerException(obj["error"]?.jsonPrimitive?.content ?: "Server answered ${r.code}", r.code, obj)
                obj
            }
        } catch (e: ServerException) { throw e } catch (e: Exception) {
            throw ServerException("Can't reach the server: ${e.message}")
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    data class Session(val token: String, val user: ServerUser)

    private fun session(o: JsonObject) = Session(o["token"]!!.jsonPrimitive.content, json.decodeFromJsonElement(ServerUser.serializer(), o["user"]!!))

    suspend fun signUp(email: String, password: String, name: String, device: String) =
        session(call("POST", "v1/auth/signup", buildJsonObject { put("email", email); put("password", password); put("name", name); put("device", device) }))

    suspend fun signIn(email: String, password: String, device: String) =
        session(call("POST", "v1/auth/signin", buildJsonObject { put("email", email); put("password", password); put("device", device) }))

    suspend fun signOut(token: String) { call("POST", "v1/auth/signout", JsonObject(emptyMap()), token) }
    suspend fun me(token: String): ServerUser = json.decodeFromJsonElement(ServerUser.serializer(), call("GET", "v1/me", token = token)["user"]!!)
    suspend fun deleteAccount(token: String) { call("DELETE", "v1/me", token = token) }

    data class Doc(val version: Int, val data: JsonElement?)

    /** A synced document, or version 0 with no data when nothing was synced yet. */
    suspend fun getDoc(token: String, kind: String): Doc = try {
        val o = call("GET", "v1/sync/$kind", token = token)
        Doc(o["version"]!!.jsonPrimitive.content.toInt(), o["data"])
    } catch (e: ServerException) { if (e.status == 404) Doc(0, null) else throw e }

    /** Writes a document. Null when another device wrote first (call [getDoc], merge, try again). */
    suspend fun putDoc(token: String, kind: String, baseVersion: Int, data: JsonElement): Int? = try {
        call("PUT", "v1/sync/$kind", buildJsonObject { put("baseVersion", baseVersion); put("data", data) }, token)["version"]!!.jsonPrimitive.content.toInt()
    } catch (e: ServerException) { if (e.status == 409) null else throw e }

    suspend fun getLibrary(token: String): Pair<Int, Library?> {
        val d = getDoc(token, "library")
        return d.version to d.data?.let { runCatching { json.decodeFromJsonElement(Library.serializer(), it) }.getOrNull() }
    }
    suspend fun putLibrary(token: String, baseVersion: Int, lib: Library) = putDoc(token, "library", baseVersion, json.encodeToJsonElement(Library.serializer(), lib))

    // ---- shared cache ----
    data class Cached(val value: JsonElement, val expiresAt: Long)

    suspend fun cacheGet(ns: String, key: String): Cached? = try {
        val o = call("GET", "v1/cache/$ns?key=${enc(key)}", client = quick)
        Cached(o["value"]!!, o["expiresAt"]?.jsonPrimitive?.longOrNull ?: 0)
    } catch (e: ServerException) { null }

    suspend fun cacheLookup(ns: String, keys: List<String>): Map<String, Cached> = try {
        val o = call("POST", "v1/cache/$ns/lookup", buildJsonObject { put("keys", kotlinx.serialization.json.JsonArray(keys.map(::JsonPrimitive))) }, client = quick)
        o["found"]?.jsonObject?.mapValues { (_, v) -> Cached(v.jsonObject["value"]!!, v.jsonObject["expiresAt"]?.jsonPrimitive?.longOrNull ?: 0) }.orEmpty()
    } catch (e: ServerException) { emptyMap() }

    suspend fun cachePut(ns: String, key: String, value: JsonElement, expiresAt: Long) {
        runCatching { call("PUT", "v1/cache/$ns", buildJsonObject { put("key", key); put("value", value); if (expiresAt > 0) put("expiresAt", expiresAt) }, client = quick) }
    }

    companion object {
        /** The server new installs talk to. Empty = none until the user enters one (Profile → Account). */
        const val DEFAULT_URL = ""
    }
}
